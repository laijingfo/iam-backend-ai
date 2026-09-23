package com.lenovo.ai;

import com.lenovo.controller.ai.AiAgentController.ChatRequest;
import com.lenovo.security.utils.RoleUtils;
import com.lenovo.ai.history.ChatHistoryStore;
import com.lenovo.ai.lock.RedisLockHelper;
import com.lenovo.ai.sse.AiChatSseEmitter;
import com.lenovo.ai.tools.context.AiToolContext;
import com.lenovo.util.I18nUtil;
import com.lenovo.util.RedisUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import com.lenovo.ai.runtime.AiChatExecutor;

import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.RejectedExecutionException;

@Slf4j
@Service
public class AiAgentService {

    private final ChatHistoryStore history;
    private final RedisLockHelper redisLockHelper;
    private final RedisUtils redisUtils;
    private final RoleUtils roles;
    private final AiChatExecutor chatExecutor;
    private final AiAgentRunner runner;

    /** memoryId -> 正在运行的 SSE，供 /chat/stop 使用 */
    private final Map<String, AiChatSseEmitter> runningEmitters = new ConcurrentHashMap<>();

    public AiAgentService(ChatHistoryStore history,
                          RedisLockHelper redisLockHelper,
                          RedisUtils redisUtils,
                          RoleUtils roles,
                          AiChatExecutor chatExecutor,
                          AiAgentRunner runner) {
        this.history = history;
        this.redisLockHelper = redisLockHelper;
        this.redisUtils = redisUtils;
        this.roles = roles;
        this.chatExecutor = chatExecutor;
        this.runner = runner;
    }

    /** 锁在建立 SSE 前取得：同一用户/会话的消息必须按顺序进入记忆和 PG。 */
    public SseEmitter chat(ChatRequest request, String userId) {
        Locale locale = LocaleContextHolder.getLocale();
        String sessionId = request.sessionId();
        String memoryId = memoryId(sessionId, userId);
        String lockKey = "ai:chat:lock:" + memoryId;
        String token = UUID.randomUUID().toString();
        acquire(lockKey, token);
        try {
            boolean newSession = !history.exists(sessionId);
            if (!newSession) history.assertOwner(sessionId, userId);

            AiToolContext context = AiToolContext.capture(userId, roles, redisUtils);

            long userMessageId = history.nextMessageId();
            long assistantMessageId = history.nextMessageId();

            AiChatSseEmitter emitter = new AiChatSseEmitter();

            // 注册到 runningEmitters
            runningEmitters.put(memoryId, emitter);
            emitter.onCompletion(() -> {
                runningEmitters.remove(memoryId);
                log.info(">>> SSE completion, memoryId={}", memoryId);
            });
            emitter.onError(e -> {
                runningEmitters.remove(memoryId);
                log.warn(">>> SSE error, memoryId={}", memoryId, e);
            });
            // ★ 关键：超时处理，complete + 释放锁
            emitter.onTimeout(() -> {
                runningEmitters.remove(memoryId);
                log.warn(">>> SSE timeout, memoryId={}", memoryId);
                try {
                    emitter.complete();
                } catch (Exception ignored) {
                }
                redisLockHelper.forceUnlock(lockKey);
            });

            chatExecutor.execute(() -> runner.run(request, context, userId, newSession,
                    sessionId, userMessageId, assistantMessageId,
                    locale, memoryId, lockKey, token, emitter));
            return emitter;
        } catch (RuntimeException error) {
            release(lockKey, token);
            if (error instanceof RejectedExecutionException)
                throw new IllegalStateException(I18nUtil.get("ai.error.busy"), error);
            throw error;
        }
    }

    /** 停止指定会话正在运行的对话 */
    public void stop(String sessionId, String userId) {
        if (sessionId == null || sessionId.isBlank() || userId == null) {
            return;
        }
        String memoryId = memoryId(sessionId, userId);

        // 1. 中断流
        AiChatSseEmitter emitter = runningEmitters.remove(memoryId);
        if (emitter != null) {
            try {
                emitter.complete();
                log.info(">>> stopped SSE, memoryId={}", memoryId);
            } catch (Exception ignored) {
            }
        }

        // 2. 强制释放锁
        String lockKey = "ai:chat:lock:" + memoryId;
        redisLockHelper.forceUnlock(lockKey);
        log.info(">>> force unlock, lockKey={}", lockKey);
    }

    private String memoryId(String sessionId, String userId) {
        return userId + ":" + sessionId;
    }

    private void acquire(String lockKey, String token) {
        boolean locked = redisLockHelper.tryLock(lockKey, token, 60_000);
        if (!locked) {
            throw new IllegalStateException("当前会话正在处理中，请稍后再试");
        }
    }

    private void release(String lockKey, String token) {
        redisLockHelper.unlock(lockKey, token);
    }
}