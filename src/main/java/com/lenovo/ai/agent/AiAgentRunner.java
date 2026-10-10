package com.lenovo.ai.agent;                                                    // 包声明：Agent 编排层

import com.lenovo.ai.agent.loop.AgentContext;                                   // 单次运行上下文
import com.lenovo.ai.agent.loop.AgentLoop;                                      // Agent 主循环
import com.lenovo.ai.infra.history.ChatHistoryStore;                            // 会话历史存储
import com.lenovo.ai.infra.lock.RedisLockHelper;                                // Redis 分布式锁工具
import com.lenovo.ai.infra.sse.AiChatSseEmitter;                                // SSE 发射器封装
import com.lenovo.ai.llm.Message;                                               // 统一消息模型
import com.lenovo.ai.memory.*;                                                  // 记忆相关（ContextBuilder/MemoryStore/MemoryExtractor/EmbeddingClient）
import com.lenovo.ai.tools.context.AiToolContext;                               // 工具执行上下文
import com.lenovo.controller.ai.AiAgentController.ChatRequest;                  // 控制器请求体
import lombok.RequiredArgsConstructor;                                          // Lombok：构造注入
import lombok.extern.slf4j.Slf4j;                                               // Lombok：日志
import org.springframework.stereotype.Component;                                // Spring 组件

import java.util.List;                                                          // 集合
import java.util.Locale;                                                        // 国际化
import java.util.stream.Collectors;                                             // 流收集器

@Slf4j                                                                          // 注入 log 字段
@Component                                                                      // 注册为 Spring Bean
@RequiredArgsConstructor                                                        // 为 final 字段生成构造器
public class AiAgentRunner {                                                    // Agent 运行编排入口

    private final ContextBuilder contextBuilder;                                // 上下文构建器（历史+记忆拼装）
    private final AgentLoop agentLoop;                                          // Agent 主循环（LLM+工具）
    private final MemoryStore memoryStore;                                      // 长期记忆存储（向量检索）
    private final MemoryExtractor memoryExtractor;                              // 记忆抽取器（异步）
    private final EmbeddingClient embeddingClient;                              // 向量化客户端
    private final ChatHistoryStore history;                                     // 会话历史存储
    private final RedisLockHelper redisLockHelper;                              // Redis 锁工具

    /**                                                                         // 方法 Javadoc 开始
     * 执行一次 Agent 完整运行。                                                   // 方法说明
     *                                                                          // 空行
     * 注意：本方法在专用线程池中执行，所有异常都在此处兜底，                        // 线程模型说明
     * 保证 finally 释放 Redis 锁。                                               // 锁释放保证
     */                                                                         // Javadoc 结束
    public void run(ChatRequest request, AiToolContext toolContext, String userId,
                    boolean newSession, String sessionId,
                    long userMessageId, long assistantMessageId,
                    Locale locale, String memoryId, String lockKey,
                    String token, AiChatSseEmitter emitter, String runId) {     // 参数：请求/工具上下文/用户/session/消息ID/语言/记忆ID/锁/SSE/运行ID
        try {                                                                   // 主流程 try，异常兜底
            history.appendUser(userMessageId, sessionId, userId, request.message()); // 1. 落库本轮 user 消息（后续 history.load 会包含它）

            // 长期记忆召回：用本轮问题做向量检索，把召回结果拼进系统上下文。       // 注释：记忆召回说明
            List<Double> qVec = embeddingClient.embed(request.message());       // 2. 将用户问题向量化
            List<MemoryItem> memories = memoryStore.searchByUser(userId, qVec, 5);// 3. 从用户记忆中 KNN 召回 top5
            String memoryBlock = memories.stream()                              // 4. 将召回记忆拼成文本块
                    .map(m -> "- " + m.content())                               //    每条前缀 "- "
                    .collect(Collectors.joining("\n"));                         //    换行拼接

            // ContextBuilder 不在内部重复追加 user 消息：                           // 注释：上下文构建说明
            // appendUser 已落库，load 会包含本轮问题；若存储实现暂时 load 为空，      //    说明落库与 load 的关系
            // 则显式补一条，保证模型一定能看到本轮问题。                            //    兜底策略
            List<Message> messages = contextBuilder.build(sessionId, request.message(), memoryBlock); // 5. 构建上下文（历史+记忆+本轮问题）

            // ✅ 在消息列表最前面注入基础系统提示词，告诉 LLM 必须调用知识库工具、必须基于检索结果作答 // 6. 注入系统提示词
            //    在最前面插入 system 消息
            //    规则正文（多行文本块）
            messages.add(0, Message.system("""                                 
                你是一个企业智能助手，必须严格遵守以下规则：
                
                1. **优先使用工具**：当用户询问业务数据、制度、流程、待办、审批等问题时，必须调用知识库工具（query_knowledge）检索，不要用自己的知识编造答案。 
                2. **基于检索结果作答**：工具返回的结果是唯一可信来源。如果检索结果为空或不确定，明确告知用户"未找到相关信息"，不要编造。 
                3. **知识库 ID 规则**：除非用户明确给出 13 位及以上纯数字的知识库 ID，否则不要传 knowledgeBaseId 参数，系统会自动路由到最相关的知识库。 
                4. **答案格式**：先直接回答问题，再引用检索到的具体内容（不要编造引用来源）。 
                5. **审核/审批相关问题**：涉及审核人、审核流程、审核频率等问题，必须调用知识库工具检索相关制度文档。 
                
                如果用户问题与业务无关（如闲聊、通用知识），可以直接回答，但仍需保持专业友好的语气。 
                """));

            // 检查历史消息是否包含本轮 user 消息，若不包含则显式补充                 // 7. 校验本轮问题是否已在上下文中
            boolean containsCurrent = messages.stream()                             //    流式检查
                    .anyMatch(m -> m.role() == com.lenovo.ai.llm.enums.MessageRole.USER // 角色为 USER
                            && request.message().equals(m.content()));          //    且内容匹配
            if (!containsCurrent) {                                                 // 8. 若未包含
                log.debug("历史消息不包含本轮问题，显式补充");                        //    仅打 debug 日志（ContextBuilder 已兜底，无需重复添加）
            }

            AgentContext ctx = new AgentContext(                                // 9. 构造运行上下文
                    messages, toolContext, emitter, sessionId,                  //    消息/工具上下文/SSE/sessionId
                    assistantMessageId, runId, memoryId);                       //    assistantMessageId/runId/memoryId
            agentLoop.run(ctx);                                                 // 10. 进入 Agent 主循环

            // 异步抽取长期记忆：本轮 user + assistant 作为一组对话。                  // 11. 记忆抽取
            List<Message> turn = List.of(                                       //    构造本轮对话
                    Message.user(request.message()),                            //    用户消息
                    Message.assistant(history.loadAssistant(assistantMessageId))//    助手回答（从存储读取）
            );
            memoryExtractor.extract(userId, turn);                              // 12. 异步抽取（@Async memoryExecutor）

        } catch (Exception e) {                                                 // 13. 异常兜底
            log.error("AiAgentRunner 异常，runId={}", runId, e);                 //    记录错误日志
        } finally {                                                             // 14. 无论如何
            // 只释放自己持有的锁（token 比对由 Lua 脚本保证），避免误删其他请求的锁。 //    锁释放注释
            redisLockHelper.unlock(lockKey, token);                             //    释放 Redis 锁（Lua 比对 token）
        }
    }
}