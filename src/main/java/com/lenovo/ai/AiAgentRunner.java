package com.lenovo.ai;

import com.lenovo.controller.ai.AiAgentController.ChatRequest;
import com.lenovo.ai.history.ChatHistoryStore;
import com.lenovo.ai.lock.RedisLockHelper;
import com.lenovo.ai.sse.AiChatSseEmitter;
import com.lenovo.ai.tools.context.AiToolContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.Locale;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

@Slf4j
@Component
@RequiredArgsConstructor
public class AiAgentRunner {

    private static final long HEARTBEAT_INTERVAL_SECONDS = 15L;

    private final KnowledgeAgentManager agentManager;
    private final ChatHistoryStore history;
    private final RedisLockHelper redisLockHelper;

    public void run(ChatRequest request,
                    AiToolContext context,
                    String userId,
                    boolean newSession,
                    String sessionId,
                    long userMessageId,
                    long assistantMessageId,
                    Locale locale,
                    String memoryId,
                    String lockKey,
                    String lockToken,
                    AiChatSseEmitter emitter,
                    String runId) {
        StringBuilder full = new StringBuilder();

        AiToolContext.set(context);

        AtomicBoolean cancelled = new AtomicBoolean(false);
        AtomicBoolean released = new AtomicBoolean(false);

        // ✅ 注册关闭回调：客户端一旦断开，立即置 cancelled
        emitter.setOnCloseCallback(() -> {
            if (cancelled.compareAndSet(false, true)) {
                log.warn("[RUN] emitter closed by client, cancel stream. sessionId={}, runId={}",
                        sessionId, runId);
            }
        });

        // ✅ 心跳
        ScheduledExecutorService heartbeatScheduler =
                Executors.newSingleThreadScheduledExecutor(r -> {
                    Thread t = new Thread(r, "sse-heartbeat-" + sessionId);
                    t.setDaemon(true);
                    return t;
                });
        ScheduledFuture<?> heartbeat = heartbeatScheduler.scheduleAtFixedRate(() -> {
            if (cancelled.get() || emitter.isClosed()) {
                cancelled.set(true);
                return;
            }
            try {
                emitter.send(SseEmitter.event().comment("hb"));
            } catch (Exception ignored) {
            }
        }, HEARTBEAT_INTERVAL_SECONDS, HEARTBEAT_INTERVAL_SECONDS, TimeUnit.SECONDS);

        Runnable releaseOnce = () -> {
            if (released.compareAndSet(false, true)) {
                try { heartbeat.cancel(true); } catch (Exception ignored) {}
                try { heartbeatScheduler.shutdownNow(); } catch (Exception ignored) {}
                try { AiToolContext.clear(); } catch (Exception e) { log.warn("AiToolContext.clear failed", e); }
                try { redisLockHelper.unlock(lockKey, lockToken); } catch (Exception e) { log.debug("redis unlock skipped: {}", e.getMessage()); }
            }
        };

        emitter.onCompletion(() -> {
            log.info("SSE onCompletion(runner), sessionId={}, runId={}", sessionId, runId);
            releaseOnce.run();
        });

        try {
            history.append(sessionId, userId, userMessageId,
                    "USER", request.message(), null);

            String knowledgeId = request.knowledgeId();

            // ✅ 只接受 13 位数字作为 knowledgeId
            if (knowledgeId != null && !knowledgeId.isBlank()
                    && !knowledgeId.matches("\\d{13}")) {
                log.warn("[RUN] 非法 knowledgeId={}，忽略并走自动路由", knowledgeId);
                knowledgeId = null;
            }

            String message = "当前登录用户：" + userId + "\n用户问题：" + request.message();

            String effectiveMemoryId = memoryId;
            if (knowledgeId != null && !knowledgeId.isBlank()) {
                agentManager.register(knowledgeId);
                effectiveMemoryId = memoryId + ":" + knowledgeId;
                message = "当前登录用户：" + userId
                        + "\n当前知识库 ID：" + knowledgeId
                        + "\n用户问题：" + request.message();
            }

            // ✅ 提出来，给 lambda 用
            final String finalEffectiveMemoryId = effectiveMemoryId;

            KnowledgeAssistant assistant = agentManager.getAssistant(knowledgeId);

            log.info("[RUN] start stream, sessionId={}, knowledgeId={}, effectiveMemoryId={}, runId={}",
                    sessionId, knowledgeId, finalEffectiveMemoryId, runId);

            assistant.chatStream(finalEffectiveMemoryId, message)
                    .onRetrieved(contents -> {
                        if (log.isDebugEnabled()) {
                            log.debug("retrieved {} contents, sessionId={}",
                                    contents == null ? 0 : contents.size(), sessionId);
                        }
                    })
                    .onPartialResponse(text -> {
                        if (cancelled.get() || emitter.isClosed()) {
                            cancelled.set(true);
                            return;
                        }
                        full.append(text);
                        try {
                            emitter.send(SseEmitter.event()
                                    .name("delta")
                                    .data(Map.of("text", text)));
                        } catch (Exception e) {
                            log.warn("emitter send delta failed: {}", e.getMessage());
                            cancelled.set(true);
                        }
                    })
                    .onCompleteResponse(response -> {
                        log.info(">>> stream complete, sessionId={}, runId={}, cancelled={}, length={}",
                                sessionId, runId, cancelled.get(), full.length());

                        // ✅ 关键修复：cancelled=true 时清空 memory，避免空 assistant 消息污染
                        if (cancelled.get()) {
                            try {
                                agentManager.clearMemory(finalEffectiveMemoryId);
                                log.info(">>> cancelled=true，清空 memory, memoryId={}", finalEffectiveMemoryId);
                            } catch (Exception e) {
                                log.warn("clearMemory failed", e);
                            }
                            releaseOnce.run();
                            return;
                        }

                        try {
                            history.append(sessionId, userId, assistantMessageId,
                                    "ASSISTANT", full.toString(), null);
                            try {
                                emitter.send(SseEmitter.event().name("done").data("{}"));
                            } catch (Exception ignored) {
                            }
                            emitter.complete();
                        } finally {
                            releaseOnce.run();
                        }
                    })
                    .onError(error -> {
                        log.error(">>> chat stream error, sessionId={}, runId={}, cancelled={}",
                                sessionId, runId, cancelled.get(), error);

                        // ✅ 出错时也清空 memory
                        try {
                            agentManager.clearMemory(finalEffectiveMemoryId);
                            log.info(">>> onError，清空 memory, memoryId={}", finalEffectiveMemoryId);
                        } catch (Exception e) {
                            log.warn("clearMemory failed", e);
                        }

                        if (cancelled.get()) {
                            releaseOnce.run();
                            return;
                        }
                        try {
                            emitter.send(SseEmitter.event()
                                    .name("error")
                                    .data(Map.of("message",
                                            error.getMessage() == null ? "未知错误" : error.getMessage())));
                        } catch (Exception ignored) {
                        }
                        try {
                            emitter.complete();
                        } catch (Exception ignored) {
                        }
                        releaseOnce.run();
                    })
                    .start();

            log.info("[RUN] stream started, sessionId={}, runId={}", sessionId, runId);

        } catch (Exception e) {
            log.error("run error, sessionId={}, runId={}", sessionId, runId, e);
            cancelled.set(true);

            // ✅ 入口异常也要清空 memory
            try {
                agentManager.clearMemory(memoryId);
            } catch (Exception ignored) {
            }

            try {
                emitter.send(SseEmitter.event()
                        .name("error")
                        .data(Map.of("message",
                                e.getMessage() == null ? "未知错误" : e.getMessage())));
            } catch (Exception ignored) {
            }
            try {
                emitter.complete();
            } catch (Exception ignored) {
            }
            releaseOnce.run();
        }
    }
}