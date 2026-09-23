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

@Slf4j
@Component
@RequiredArgsConstructor
public class AiAgentRunner {

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
                    AiChatSseEmitter emitter) {
        StringBuilder full = new StringBuilder();
        AiToolContext.set(context);
        try {
            history.append(sessionId, userId, userMessageId,
                    "USER", request.message(), null);

            String knowledgeId = request.knowledgeId();
            if (knowledgeId == null || knowledgeId.isBlank()) {
                knowledgeId = extractKnowledgeId(request.message());
                if (knowledgeId != null) {
                    log.info(">>> 从消息里提取到 knowledgeId: {}", knowledgeId);
                }
            }

            String message = request.message();
            if (knowledgeId != null && !knowledgeId.isBlank()) {
                agentManager.register(knowledgeId);
                message = "当前知识库 ID：" + knowledgeId + "\n用户问题：" + request.message();
            }
            KnowledgeAssistant assistant = agentManager.getAssistant(knowledgeId);

            assistant.chatStream(memoryId, message, userId)
                    .onRetrieved(contents -> {
                        if (log.isDebugEnabled()) {
                            log.debug("retrieved {} contents, sessionId={}",
                                    contents == null ? 0 : contents.size(), sessionId);
                        }
                    })
                    .onPartialResponse(text -> {
                        log.info(">>> onPartialResponse: [{}]", text);
                        full.append(text);
                        emitter.send(SseEmitter.event()
                                .name("delta")
                                .data(Map.of("text", text)));
                    })
                    .onCompleteResponse(response -> {
                        log.info(">>> stream complete, sessionId={}, length={}",
                                sessionId, full.length());
                        try {
                            history.append(sessionId, userId, assistantMessageId,
                                    "ASSISTANT", full.toString(), null);
                            // ★ 发 done 事件，通知前端结束
                            try {
                                emitter.send(SseEmitter.event().name("done").data("{}"));
                            } catch (Exception ignored) {
                            }
                            emitter.complete();
                        } finally {
                            releaseAll(lockKey, lockToken);
                        }
                    })
                    .onError(error -> {
                        log.error("chat stream error, sessionId={}", sessionId, error);
                        try {
                            emitter.send(SseEmitter.event()
                                    .name("error")
                                    .data(Map.of("message",
                                            error.getMessage() == null ? "未知错误" : error.getMessage())));
                        } catch (Exception ignored) {
                        }
                        emitter.complete();
                        releaseAll(lockKey, lockToken);
                    })
                    .start();

        } catch (Exception e) {
            log.error("run error, sessionId={}", sessionId, e);
            try {
                emitter.send(SseEmitter.event()
                        .name("error")
                        .data(Map.of("message",
                                e.getMessage() == null ? "未知错误" : e.getMessage())));
            } catch (Exception ignored) {
            }
            emitter.complete();
            releaseAll(lockKey, lockToken);
        }
    }

    /**
     * 从用户消息里提取知识库 ID（连续数字串，通常 15 位以上）。
     */
    private static String extractKnowledgeId(String message) {
        if (message == null || message.isBlank()) {
            return null;
        }
        java.util.regex.Matcher m = java.util.regex.Pattern
                .compile("\\d{15,}")
                .matcher(message);
        if (m.find()) {
            return m.group();
        }
        return null;
    }

    private void releaseAll(String lockKey, String lockToken) {
        AiToolContext.clear();
        redisLockHelper.unlock(lockKey, lockToken);
    }
}