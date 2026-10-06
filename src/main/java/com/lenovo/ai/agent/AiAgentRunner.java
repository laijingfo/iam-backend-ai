package com.lenovo.ai.agent;

import com.lenovo.ai.agent.loop.AgentContext;
import com.lenovo.ai.agent.loop.AgentLoop;
import com.lenovo.ai.infra.history.ChatHistoryStore;
import com.lenovo.ai.infra.lock.RedisLockHelper;
import com.lenovo.ai.infra.sse.AiChatSseEmitter;
import com.lenovo.ai.llm.Message;
import com.lenovo.ai.memory.*;
import com.lenovo.ai.tools.context.AiToolContext;
import com.lenovo.controller.ai.AiAgentController.ChatRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

@Slf4j
@Component
@RequiredArgsConstructor
public class AiAgentRunner {

    private final ContextBuilder contextBuilder;
    private final AgentLoop agentLoop;
    private final MemoryStore memoryStore;
    private final MemoryExtractor memoryExtractor;
    private final EmbeddingClient embeddingClient;
    private final ChatHistoryStore history;
    private final RedisLockHelper redisLockHelper;

    public void run(ChatRequest request, AiToolContext toolContext, String userId,
                    boolean newSession, String sessionId,
                    long userMessageId, long assistantMessageId,
                    Locale locale, String memoryId, String lockKey,
                    String token, AiChatSseEmitter emitter, String runId) {
        try {
            history.appendUser(userMessageId, sessionId, userId, request.message());

            // 长期记忆召回
            List<Double> qVec = embeddingClient.embed(request.message());
            List<MemoryItem> memories = memoryStore.searchByUser(userId, qVec, 5);
            String memoryBlock = memories.stream()
                    .map(m -> "- " + m.content())
                    .collect(Collectors.joining("\n"));

            // ✅ ContextBuilder 不再内部重复追加 user 消息
            // 由于 appendUser 已经落库，load 会包含这条；若 InMemory 实现暂时 load 为空，
            // 则显式追加一次，避免模型看不到本轮问题。
            List<Message> messages = contextBuilder.build(sessionId, request.message(), memoryBlock);
            boolean containsCurrent = messages.stream()
                    .anyMatch(m -> m.role() == com.lenovo.ai.llm.enums.MessageRole.USER
                            && request.message().equals(m.content()));
            if (!containsCurrent) {
                messages.add(Message.user(request.message()));
            }

            AgentContext ctx = new AgentContext(
                    messages, toolContext, emitter, assistantMessageId, runId, memoryId);
            agentLoop.run(ctx);

            // 异步抽取长期记忆
            List<Message> turn = List.of(
                    Message.user(request.message()),
                    Message.assistant(history.loadAssistant(assistantMessageId))
            );
            memoryExtractor.extract(userId, turn);

        } catch (Exception e) {
            log.error("AiAgentRunner 异常, runId={}", runId, e);
        } finally {
            redisLockHelper.forceUnlock(lockKey);
        }
    }
}