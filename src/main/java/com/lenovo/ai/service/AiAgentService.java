package com.lenovo.ai.service;

import com.lenovo.ai.agent.AiAgentRunner;
import com.lenovo.ai.agent.ChatRequest;
import com.lenovo.ai.agent.runtime.AiChatExecutor;
import com.lenovo.ai.infra.history.ChatHistoryStore;
import com.lenovo.ai.infra.lock.RedisLockHelper;
import com.lenovo.ai.infra.sse.AiChatSseEmitter;
import com.lenovo.ai.token.QuotaExceededException;
import com.lenovo.ai.token.TokenQuotaService;
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
    private final TokenQuotaService quotaService;          // ★ 新增

    // ============ 状态存储：requestId 维度 ============
    private final Map<String, AiChatSseEmitter> emittersByRequest = new ConcurrentHashMap<>();
    private final Map<String, String> runIdsByRequest = new ConcurrentHashMap<>();
    private final Map<String, String> tokensByRequest = new ConcurrentHashMap<>();
    private final Map<String, String> memoryIdsByRequest = new ConcurrentHashMap<>();
    private final Map<String, String> activeRequestByMemory = new ConcurrentHashMap<>();

    public AiAgentService(ChatHistoryStore history,
                          RedisLockHelper redisLockHelper,
                          RedisUtils redisUtils,
                          AiChatExecutor chatExecutor,
                          AiAgentRunner runner,
                          TokenQuotaService quotaService) {        // ★ 新增参数
        this.history = history;
        this.redisLockHelper = redisLockHelper;
        this.redisUtils = redisUtils;
        this.chatExecutor = chatExecutor;
        this.runner = runner;
        this.quotaService = quotaService;
    }

    public SseEmitter chat(ChatRequest request, String userId) {

        // ============================================================
        // ★★★ 改动 1：入口配额检查 ★★★
        // ============================================================
        try {
            quotaService.checkOrThrow(userId);
        } catch (QuotaExceededException e) {
            log.warn("[quota] 用户 {} 配额超限，拒绝请求: {}", userId, e.getReason());
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
        String token = UUID.randomUUID().toString();
        String newRunId = UUID.randomUUID().toString();
        String requestId = (request.clientRequestId() != null && !request.clientRequestId().isBlank())
                ? request.clientRequestId()
                : UUID.randomUUID().toString();

        AiChatSseEmitter emitter = new AiChatSseEmitter();

        try {
            // ---- 1. 替换同一会话的旧请求 ----
            String oldRequestId = activeRequestByMemory.get(memoryId);
            if (oldRequestId != null && !oldRequestId.equals(requestId)) {
                String oldToken = tokensByRequest.remove(oldRequestId);
                if (oldToken != null) {
                    redisLockHelper.forceUnlock(lockKey);
                    log.info(">>> 强制释放旧 Redis 锁: lockKey={}, oldToken={}", lockKey, oldToken);
                }
                AiChatSseEmitter oldEmitter = emittersByRequest.remove(oldRequestId);
                if (oldEmitter != null && !oldEmitter.isClosed()) {
                    try {
                        oldEmitter.send("error", Map.of("message", "会话已被新请求替换"));
                        oldEmitter.complete();
                    } catch (Exception e) {
                        log.warn("关闭旧 emitter 异常: memoryId={}", memoryId, e);
                    }
                }
                runIdsByRequest.remove(oldRequestId);
                memoryIdsByRequest.remove(oldRequestId);
                log.info(">>> 已替换旧请求: memoryId={}, oldRequestId={}, newRequestId={}",
                        memoryId, oldRequestId, requestId);
            }

            // ---- 2. 注册本次请求状态 ----
            emittersByRequest.put(requestId, emitter);
            runIdsByRequest.put(requestId, newRunId);
            tokensByRequest.put(requestId, token);
            memoryIdsByRequest.put(requestId, memoryId);
            activeRequestByMemory.put(memoryId, requestId);

            // ---- 3. 加锁 ----
            boolean locked = redisLockHelper.tryLock(lockKey, token, 5_000L);
            if (!locked) {
                log.error(">>> 锁冲突，关闭新请求: memoryId={}", memoryId);
                cleanup(requestId);
                try {
                    emitter.send(SseEmitter.event().name("error")
                            .data(Map.of("message", "上一次请求尚未完成，请稍后再试")));
                } catch (Exception ignored) {}
                emitter.complete();
                return emitter;
            }
            log.info(">>> 成功获取锁: memoryId={}, requestId={}", memoryId, requestId);

            // ---- 4. 会话初始化 ----
            boolean newSession = !history.exists(sessionId);
            if (!newSession) history.assertOwner(sessionId, userId);

            AiToolContext context = AiToolContext.capture(userId, redisUtils);
            long userMessageId = history.nextMessageId();
            long assistantMessageId = history.nextMessageId();

            // ---- 5. 生命周期回调 ----
            emitter.onCompletion(() -> {
                cleanup(requestId);
                log.debug(">>> emitter onCompletion: requestId={}", requestId);
            });
            emitter.onTimeout(() -> {
                try { emitter.complete(); } catch (Exception ignored) {}
                cleanup(requestId);
                redisLockHelper.unlock(lockKey, token);
            });
            emitter.onError(e -> {
                cleanup(requestId);
                log.warn(">>> emitter onError: requestId={}, err={}", requestId, e.toString());
            });

            // ---- 6. 提交线程池（注意：run 的最后参数是 requestId，不是 newRunId）----
            chatExecutor.execute(() -> runner.run(
                    request, context, userId, newSession, sessionId,
                    userMessageId, assistantMessageId, locale,
                    memoryId, lockKey, token, emitter, requestId));

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
        String memoryId = userId + ":" + sessionId;
        log.info(">>> 停止会话: memoryId={}, clientRequestId={}", memoryId, clientRequestId);

        if (clientRequestId == null || clientRequestId.isBlank()) {
            log.warn("stop() clientRequestId 为空，忽略: memoryId={}", memoryId);
            return;
        }

        AiChatSseEmitter emitter = emittersByRequest.remove(clientRequestId);
        if (emitter == null) {
            log.info("stop() 未找到 emitter，忽略: requestId={}, memoryId={}",
                    clientRequestId, memoryId);
            return;
        }

        try {
            if (!emitter.isClosed()) {
                emitter.send("error", Map.of("message", "会话已被手动停止"));
                emitter.complete();
                log.info(">>> 已关闭 emitter: requestId={}", clientRequestId);
            }
        } catch (Exception e) {
            log.warn("关闭 emitter 异常: requestId={}", clientRequestId, e);
        }

        String token = tokensByRequest.remove(clientRequestId);
        if (token != null) {
            String lockKey = "ai:chat:lock:" + memoryId;
            try {
                redisLockHelper.unlock(lockKey, token);
                log.info("<<< 释放 Redis 锁: lockKey={}", lockKey);
            } catch (Exception e) {
                log.error("释放 Redis 锁失败: {}", lockKey, e);
            }
        }
        runIdsByRequest.remove(clientRequestId);
        memoryIdsByRequest.remove(clientRequestId);
        activeRequestByMemory.remove(memoryId, clientRequestId);
    }

    private void cleanup(String requestId) {
        emittersByRequest.remove(requestId);
        runIdsByRequest.remove(requestId);
        tokensByRequest.remove(requestId);
        String memoryId = memoryIdsByRequest.remove(requestId);
        if (memoryId != null) {
            activeRequestByMemory.remove(memoryId, requestId);
        }
    }
}