package com.lenovo.ai.service;

import com.lenovo.ai.agent.AiAgentRunner;
import com.lenovo.ai.agent.runtime.AiChatExecutor;
import com.lenovo.ai.infra.history.ChatHistoryStore;
import com.lenovo.ai.infra.lock.RedisLockHelper;
import com.lenovo.ai.infra.sse.AiChatSseEmitter;
import com.lenovo.ai.tools.context.AiToolContext;
import com.lenovo.controller.ai.AiAgentController.ChatRequest;
import com.lenovo.security.utils.RoleUtils;
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
    private final RoleUtils roles;
    private final AiChatExecutor chatExecutor;
    private final AiAgentRunner runner;

    private final Map<String, AiChatSseEmitter> runningEmitters = new ConcurrentHashMap<>();
    private final Map<String, String> runningRunIds = new ConcurrentHashMap<>();

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

    public SseEmitter chat(ChatRequest request, String userId) {
        Locale locale = LocaleContextHolder.getLocale();
        String sessionId = request.sessionId();
        String memoryId = userId + ":" + sessionId;
        String lockKey = "ai:chat:lock:" + memoryId;
        String token = UUID.randomUUID().toString();
        String newRunId = UUID.randomUUID().toString();

        AiChatSseEmitter emitter = new AiChatSseEmitter();
        try {
            AiChatSseEmitter oldEmitter = runningEmitters.put(memoryId, emitter);
            if (oldEmitter != null && oldEmitter != emitter) {
                try { oldEmitter.complete(); } catch (Exception ignored) {}
            }
            runningRunIds.put(memoryId, newRunId);

            redisLockHelper.forceUnlock(lockKey);
            boolean locked = redisLockHelper.tryLock(lockKey, token, 60_000);
            if (!locked) {
                emitter.send(SseEmitter.event().name("error")
                        .data(Map.of("message", "当前会话正在处理中，请稍后再试")));
                emitter.complete();
                return emitter;
            }

            boolean newSession = !history.exists(sessionId);
            if (!newSession) history.assertOwner(sessionId, userId);

            AiToolContext context = AiToolContext.capture(userId, roles, redisUtils);
            long userMessageId = history.nextMessageId();
            long assistantMessageId = history.nextMessageId();

            emitter.onCompletion(() -> {
                runningEmitters.remove(memoryId, emitter);
                runningRunIds.remove(memoryId, newRunId);
            });
            emitter.onTimeout(() -> {
                try { emitter.complete(); } catch (Exception ignored) {}
                redisLockHelper.forceUnlock(lockKey);
            });

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

    public void stop(String sessionId, String userId) {
        if (sessionId == null || sessionId.isBlank() || userId == null) return;
        log.info(">>> stop 被忽略, memoryId={}", userId + ":" + sessionId);
    }
}