package com.lenovo.ai.service;

import com.lenovo.ai.agent.AiAgentRunner;
import com.lenovo.ai.agent.runtime.AiChatExecutor;
import com.lenovo.ai.infra.history.ChatHistoryStore;
import com.lenovo.ai.infra.lock.RedisLockHelper;
import com.lenovo.ai.infra.sse.AiChatSseEmitter;
import com.lenovo.ai.tools.context.AiToolContext;
import com.lenovo.controller.ai.AiAgentController.ChatRequest;
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

    private final Map<String, AiChatSseEmitter> runningEmitters = new ConcurrentHashMap<>();
    private final Map<String, String> runningRunIds = new ConcurrentHashMap<>();
    private final Map<String, String> runningTokens = new ConcurrentHashMap<>();
    private final Map<String, String> runningRequestIds = new ConcurrentHashMap<>();

    public AiAgentService(ChatHistoryStore history,
                          RedisLockHelper redisLockHelper,
                          RedisUtils redisUtils,
                          AiChatExecutor chatExecutor,
                          AiAgentRunner runner) {
        this.history = history;
        this.redisLockHelper = redisLockHelper;
        this.redisUtils = redisUtils;
        this.chatExecutor = chatExecutor;
        this.runner = runner;
    }

    public SseEmitter chat(ChatRequest request, String userId) {
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
            // 1. 清理旧状态（先取 token、forceUnlock，再关闭旧 emitter，避免 onCompletion 提前清 token）
            String oldToken = runningTokens.remove(memoryId);
            if (oldToken != null) {
                redisLockHelper.forceUnlock(lockKey);
                log.info(">>> 强制释放旧 Redis 锁: lockKey={}, oldToken={}", lockKey, oldToken);
            }

            AiChatSseEmitter oldEmitter = runningEmitters.remove(memoryId);
            if (oldEmitter != null && !oldEmitter.isClosed()) {
                try {
                    oldEmitter.send("error", Map.of("message", "会话已被新请求替换"));
                    oldEmitter.complete();
                    log.info(">>> 已关闭旧 emitter: memoryId={}", memoryId);
                } catch (Exception e) {
                    log.warn("关闭旧 emitter 异常: memoryId={}", memoryId, e);
                }
            }

            // 2. 注册当前请求状态
            runningEmitters.put(memoryId, emitter);
            runningTokens.put(memoryId, token);
            runningRunIds.put(memoryId, newRunId);
            runningRequestIds.put(memoryId, requestId);

            // 3. 获取锁
            boolean locked = redisLockHelper.tryLock(lockKey, token, 5_000L);
            if (!locked) {
                log.error(">>> 锁冲突，关闭新请求: memoryId={}, oldToken={}", memoryId, oldToken);
                runningEmitters.remove(memoryId, emitter);
                runningTokens.remove(memoryId, token);
                runningRunIds.remove(memoryId, newRunId);
                runningRequestIds.remove(memoryId, requestId);
                try {
                    emitter.send(SseEmitter.event().name("error")
                            .data(Map.of("message", "上一次请求尚未完成，请稍后再试")));
                } catch (Exception ignored) {}
                emitter.complete();
                return emitter;
            }
            log.info(">>> 成功获取锁: memoryId={}, requestId={}", memoryId, requestId);

            // 4. 会话初始化
            boolean newSession = !history.exists(sessionId);
            if (!newSession) history.assertOwner(sessionId, userId);

            // ✅ 注意：AiToolContext.capture 需要 3 个参数（userId, roles, redisUtils）
            // 如果你没有 RoleUtils 字段，请检查 AiToolContext 的签名
            AiToolContext context = AiToolContext.capture(userId, redisUtils);
            long userMessageId = history.nextMessageId();
            long assistantMessageId = history.nextMessageId();

            // 5. 注册生命周期回调
            emitter.onCompletion(() -> {
                runningEmitters.remove(memoryId, emitter);
                runningRunIds.remove(memoryId, newRunId);
                runningTokens.remove(memoryId, token);
                runningRequestIds.remove(memoryId, requestId);
            });
            emitter.onTimeout(() -> {
                try { emitter.complete(); } catch (Exception ignored) {}
                runningRequestIds.remove(memoryId);
                redisLockHelper.unlock(lockKey, token);
            });

            // 6. 提交任务
            chatExecutor.execute(() -> runner.run(
                    request, context, userId, newSession, sessionId,
                    userMessageId, assistantMessageId, locale,
                    memoryId, lockKey, token, emitter, newRunId));

            return emitter;

        } catch (Exception e) {
            log.error("chat() 入口异常", e);
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

        String storedRequestId = runningRequestIds.get(memoryId);
        if (clientRequestId != null && !clientRequestId.isBlank()) {
            boolean requestIdMatches = clientRequestId.equals(storedRequestId);
            if (!requestIdMatches) {
                log.info("stop() clientRequestId 不匹配，跳过关闭: stored={}, requested={}",
                        storedRequestId, clientRequestId);
                return;
            }
        }

        AiChatSseEmitter emitter = runningEmitters.remove(memoryId);
        if (emitter != null) {
            try {
                if (!emitter.isClosed()) {
                    emitter.send("error", Map.of("message", "会话已被新的请求替换"));
                    emitter.complete();
                    log.info(">>> 已关闭 emitter: memoryId={}", memoryId);
                }
            } catch (Exception e) {
                log.warn("关闭 emitter 异常: memoryId={}", memoryId, e);
            }
        } else {
            log.info("stop() emitter 已不存在, memoryId={}", memoryId);
        }
        runningRequestIds.remove(memoryId);

        String token = runningTokens.remove(memoryId);
        if (token != null) {
            String lockKey = "ai:chat:lock:" + memoryId;
            try {
                redisLockHelper.unlock(lockKey, token);
                log.info("<<< 释放 Redis 锁: lockKey={}", lockKey);
            } catch (Exception e) {
                log.error("释放 Redis 锁失败: {}", lockKey, e);
            }
        }

        runningRunIds.remove(memoryId);
    }
}