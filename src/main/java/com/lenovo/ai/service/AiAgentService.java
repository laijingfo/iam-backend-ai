package com.lenovo.ai.service;

import com.lenovo.ai.agent.AiAgentRunner;
import com.lenovo.ai.agent.ChatRequest;
import com.lenovo.ai.agent.runtime.AiChatExecutor;
import com.lenovo.ai.infra.history.ChatHistoryStore;
import com.lenovo.ai.infra.lock.RedisLockHelper;
import com.lenovo.ai.infra.sse.AiChatSseEmitter;
import com.lenovo.ai.token.exception.QuotaExceededException;
import com.lenovo.ai.token.quota.TokenQuotaService;
import com.lenovo.ai.tools.context.AiToolContext;
import com.lenovo.util.RedisUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Service
public class AiAgentService {

    private final ChatHistoryStore history;
    private final RedisLockHelper redisLockHelper;
    private final RedisUtils redisUtils;
    private final AiChatExecutor chatExecutor;
    private final AiAgentRunner runner;
    private final TokenQuotaService quotaService;

    private final Map<String, RequestState> requestsById = new ConcurrentHashMap<>();
    private final Map<String, String> activeByMemory = new ConcurrentHashMap<>();

    public AiAgentService(ChatHistoryStore history,
                          RedisLockHelper redisLockHelper,
                          RedisUtils redisUtils,
                          AiChatExecutor chatExecutor,
                          AiAgentRunner runner,
                          TokenQuotaService quotaService) {
        this.history = history;
        this.redisLockHelper = redisLockHelper;
        this.redisUtils = redisUtils;
        this.chatExecutor = chatExecutor;
        this.runner = runner;
        this.quotaService = quotaService;
    }

    public SseEmitter chat(ChatRequest request, String userId) {

        // 1. 入口配额检查
        try {
            quotaService.checkOrThrow(userId);
        } catch (QuotaExceededException e) {
            log.warn("[quota] 用户 {} 配额超限: {}", userId, e.getReason());
            AiChatSseEmitter reject = new AiChatSseEmitter();
            try {
                reject.send("error", Map.of(
                        "code", "QUOTA_EXCEEDED",
                        "message", e.getReason()));
            } catch (Exception ignored) {}
            reject.complete();
            return reject;
        }

        Locale locale = LocaleContextHolder.getLocale();
        String sessionId = request.sessionId();
        String memoryId = userId + ":" + sessionId;
        String lockKey = "ai:chat:lock:" + memoryId;
        String lockToken = UUID.randomUUID().toString();
        // ★ runId 服务端唯一，绝不使用客户端可伪造的值
        String runId = UUID.randomUUID().toString();
        // ★ requestId 允许客户端指定（幂等 / 停止用），但不参与黑板 / AgentContext
        String requestId = (request.clientRequestId() != null && !request.clientRequestId().isBlank())
                ? request.clientRequestId()
                : UUID.randomUUID().toString();

        AiChatSseEmitter emitter = new AiChatSseEmitter();

        try {
            // 2. 替换同一会话的旧请求
            String oldRequestId = activeByMemory.get(memoryId);
            if (oldRequestId != null && !oldRequestId.equals(requestId)) {
                RequestState oldState = requestsById.remove(oldRequestId);
                if (oldState != null) {
                    try {
                        redisLockHelper.forceUnlock(oldState.lockKey);
                    } catch (Exception e) {
                        log.warn("释放旧锁失败", e);
                    }
                    if (!oldState.emitter.isClosed()) {
                        try {
                            oldState.emitter.send("error", Map.of("message", "会话已被新请求替换"));
                            oldState.emitter.complete();
                        } catch (Exception e) {
                            log.warn("关闭旧 emitter 异常", e);
                        }
                    }
                }
                log.info(">>> 已替换旧请求: memoryId={}, old={}, new={}",
                        memoryId, oldRequestId, requestId);
            }

            // 3. 注册请求状态
            RequestState state = new RequestState(
                    requestId, runId, memoryId, lockToken, lockKey, emitter);
            requestsById.put(requestId, state);
            activeByMemory.put(memoryId, requestId);

            // 4. 加锁
            boolean locked = redisLockHelper.tryLock(lockKey, lockToken, 300_000L);
            if (!locked) {
                log.error(">>> 锁冲突: memoryId={}", memoryId);
                cleanup(requestId);
                try {
                    emitter.send(SseEmitter.event().name("error")
                            .data(Map.of("message", "上一次请求尚未完成，请稍后再试")));
                } catch (Exception ignored) {}
                emitter.complete();
                return emitter;
            }
            log.info(">>> 成功获取锁: memoryId={}, requestId={}, runId={}",
                    memoryId, requestId, runId);

            // 5. 会话初始化
            boolean newSession = !history.exists(sessionId);
            if (!newSession) history.assertOwner(sessionId, userId);

            AiToolContext context = AiToolContext.capture(userId, redisUtils);
            long userMessageId = history.nextMessageId();
            long assistantMessageId = history.nextMessageId();

            // 6. 生命周期回调
            emitter.addCloseCallback(() -> {
                cleanup(requestId);
                log.debug(">>> emitter close: requestId={}", requestId);
            });

            // 7. 提交线程池 ★ 传 runId 和 requestId 两个
            chatExecutor.execute(() -> runner.run(
                    request, context, userId, newSession, sessionId,
                    userMessageId, assistantMessageId, locale,
                    memoryId, lockKey, lockToken, emitter,
                    runId, requestId));

            return emitter;

        } catch (Exception e) {
            log.error("chat() 入口异常", e);
            cleanup(requestId);
            try {
                emitter.send(SseEmitter.event().name("error")
                        .data(Map.of("message", "系统繁忙，请稍后再试")));
            } catch (Exception ignored) {}
            emitter.complete();
            return emitter;
        }
    }

    public void stop(String sessionId, String userId, String clientRequestId) {
        if (sessionId == null || sessionId.isBlank() || userId == null) return;
        if (clientRequestId == null || clientRequestId.isBlank()) return;

        RequestState state = requestsById.remove(clientRequestId);
        if (state == null) {
            log.info("stop() 未找到 emitter: requestId={}", clientRequestId);
            return;
        }

        log.info(">>> 停止会话: memoryId={}, requestId={}, runId={}",
                state.memoryId, clientRequestId, state.runId);

        try {
            if (!state.emitter.isClosed()) {
                state.emitter.send("error", Map.of("message", "会话已被手动停止"));
                state.emitter.complete();
            }
        } catch (Exception e) {
            log.warn("关闭 emitter 异常", e);
        }

        try {
            redisLockHelper.unlock(state.lockKey, state.lockToken);
        } catch (Exception e) {
            log.error("释放 Redis 锁失败", e);
        }

        activeByMemory.remove(state.memoryId, clientRequestId);
    }

    private void cleanup(String requestId) {
        RequestState state = requestsById.remove(requestId);
        if (state != null) {
            activeByMemory.remove(state.memoryId, requestId);
        }
    }
}