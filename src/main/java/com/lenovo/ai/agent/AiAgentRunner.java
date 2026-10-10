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

            // 长期记忆召回：用本轮问题做向量检索，把召回结果拼进系统上下文。
            List<Double> qVec = embeddingClient.embed(request.message());
            List<MemoryItem> memories = memoryStore.searchByUser(userId, qVec, 5);
            String memoryBlock = memories.stream()
                    .map(m -> "- " + m.content())
                    .collect(Collectors.joining("\n"));

            // ContextBuilder 不在内部重复追加 user 消息：
            // appendUser 已落库，load 会包含本轮问题；若存储实现暂时 load 为空，
            // 则显式补一条，保证模型一定能看到本轮问题。
            List<Message> messages = contextBuilder.build(sessionId, request.message(), memoryBlock);
            
            // ✅ 在消息列表最前面注入基础系统提示词，告诉 LLM 必须调用知识库工具、必须基于检索结果作答
            messages.add(0, Message.system("""
                你是一个企业智能助手，必须严格遵守以下规则：
                
                1. **优先使用工具**：当用户询问业务数据、制度、流程、待办、审批等问题时，必须调用知识库工具（query_knowledge）检索，不要用自己的知识编造答案。
                2. **基于检索结果作答**：工具返回的结果是唯一可信来源。如果检索结果为空或不确定，明确告知用户"未找到相关信息"，不要编造。
                3. **知识库 ID 规则**：除非用户明确给出 13 位及以上纯数字的知识库 ID，否则不要传 knowledgeBaseId 参数，系统会自动路由到最相关的知识库。
                4. **答案格式**：先直接回答问题，再引用检索到的具体内容（不要编造引用来源）。
                5. **审核/审批相关问题**：涉及审核人、审核流程、审核频率等问题，必须调用知识库工具检索相关制度文档。
                
                如果用户问题与业务无关（如闲聊、通用知识），可以直接回答，但仍需保持专业友好的语气。
                """));
            
            // 检查历史消息是否包含本轮 user 消息，若不包含则显式补充
            boolean containsCurrent = messages.stream()
                    .anyMatch(m -> m.role() == com.lenovo.ai.llm.enums.MessageRole.USER
                            && request.message().equals(m.content()));
            if (!containsCurrent) {
                log.debug("历史消息不包含本轮问题，显式补充");
            }

            AgentContext ctx = new AgentContext(
                    messages, toolContext, emitter, sessionId,
                    assistantMessageId, runId, memoryId);
            agentLoop.run(ctx);

            // 异步抽取长期记忆：本轮 user + assistant 作为一组对话。
            List<Message> turn = List.of(
                    Message.user(request.message()),
                    Message.assistant(history.loadAssistant(assistantMessageId))
            );
            memoryExtractor.extract(userId, turn);

        } catch (Exception e) {
            log.error("AiAgentRunner 异常，runId={}", runId, e);
        } finally {
            // 只释放自己持有的锁（token 比对由 Lua 脚本保证），避免误删其他请求的锁。
            redisLockHelper.unlock(lockKey, token);
        }
    }
}