package com.lenovo.ai.service;

import com.lenovo.ai.agent.AiAgentRunner;
import com.lenovo.controller.ai.AiAgentController.ChatRequest;
import com.lenovo.security.utils.RoleUtils;
import com.lenovo.ai.infra.history.ChatHistoryStore;
import com.lenovo.ai.infra.lock.RedisLockHelper;
import com.lenovo.ai.infra.sse.AiChatSseEmitter;
import com.lenovo.ai.tools.context.AiToolContext;
import com.lenovo.util.RedisUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import com.lenovo.ai.agent.runtime.AiChatExecutor;

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

    /** memoryId -> 当前正在运行的 SSE */
    private final Map<String, AiChatSseEmitter> runningEmitters = new ConcurrentHashMap<>();
    /** memoryId -> 当前正在运行的 runId（仅用于日志） */
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
        String memoryId = memoryId(sessionId, userId);
        String lockKey = "ai:chat:lock:" + memoryId;
        String token = UUID.randomUUID().toString();
        String newRunId = UUID.randomUUID().toString();

        AiChatSseEmitter emitter = new AiChatSseEmitter();
        try {
            // ✅ 1. 新请求进来时，无条件把同一 memoryId 上的旧 emitter 干掉
            AiChatSseEmitter oldEmitter = runningEmitters.put(memoryId, emitter);
            if (oldEmitter != null && oldEmitter != emitter) {
                try {
                    oldEmitter.complete();
                    log.info(">>> 新请求进来，强制停掉旧 emitter, memoryId={}, newRunId={}",
                            memoryId, newRunId);
                } catch (Exception e) {
                    log.warn(">>> 强制 complete 旧 emitter 失败: {}", e.getMessage());
                }
            }
            runningRunIds.put(memoryId, newRunId);

            // ✅ 2. 强制清掉旧锁，再抢新锁
            redisLockHelper.forceUnlock(lockKey);
            boolean locked = redisLockHelper.tryLock(lockKey, token, 60_000);
            if (!locked) {
                log.warn(">>> 抢锁失败, memoryId={}", memoryId);
                try {
                    emitter.send(SseEmitter.event().name("error")
                            .data(Map.of("message", "当前会话正在处理中，请稍后再试")));
                } catch (Exception ignored) {}
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
                log.info(">>> SSE completion, memoryId={}, runId={}", memoryId, newRunId);
            });
            emitter.onTimeout(() -> {
                log.warn(">>> SSE timeout, memoryId={}, runId={}", memoryId, newRunId);
                try { emitter.complete(); } catch (Exception ignored) {}
                redisLockHelper.forceUnlock(lockKey);
            });

            chatExecutor.execute(() -> runner.run(request, context, userId, newSession,
                    sessionId, userMessageId, assistantMessageId,
                    locale, memoryId, lockKey, token, emitter, newRunId));

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

    /**
     * ✅ 方案 B++++ 下，stop 直接忽略。
     * 原因：前端切换问题时 stop 和 chat 是同时发的，后端无法区分
     *       stop 是想停"上一个"还是"当前"。强制停当前会误伤新请求。
     *       "停上一个"已经由新 chat 里的 oldEmitter.complete() 覆盖。
     */
    public void stop(String sessionId, String userId) {
        if (sessionId == null || sessionId.isBlank() || userId == null) {
            return;
        }
        String memoryId = memoryId(sessionId, userId);
        log.info(">>> stop 调用被忽略（由新 chat 自动停旧请求）, memoryId={}", memoryId);
        // 不 forceUnlock，不动 emitter
    }

    private String memoryId(String sessionId, String userId) {
        return userId + ":" + sessionId;
    }
}