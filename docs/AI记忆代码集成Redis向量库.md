# 企业智能助手 AI Agent 模块 — 完整功能描述文档

---

## 一、模块概述

本模块是基于 **Spring Boot + LangChain4j** 构建的企业级 AI Agent 对话系统，核心能力包括：

| 能力                         | 说明                                       |
| -------------------------- | ---------------------------------------- |
| **流式对话**                   | 基于 SSE（Server-Sent Events）实时推送 LLM 输出到前端 |
| **工具调用（Function Calling）** | LLM 可自主调用知识库检索、日期计算、工单创建等工具              |
| **长期记忆**                   | 从对话中抽取用户偏好/事实/实体，存入 Redis 向量库，后续对话召回     |
| **对话摘要**                   | 历史消息过长时自动压缩为摘要，控制上下文预算                   |
| **知识库路由**                  | 基于向量相似度自动选择最相关的知识库                       |
| **权限控制**                   | 工具按角色过滤，二次校验防止 LLM 幻觉调用未授权工具             |
| **并发控制**                   | Redis 分布式锁保证同一会话串行；专用线程池限流               |
| **挂起/确认**                  | 需确认的工具（如创建工单）挂起状态存 Redis，等待用户确认后恢复       |

### 整体架构分层

```
┌─────────────────────────────────────────────────────┐
│  Controller 层  AiAgentController.ChatRequest        │
├─────────────────────────────────────────────────────┤
│  Service 层     AiAgentService（入口/生命周期管理）    │
├─────────────────────────────────────────────────────┤
│  Agent 层       AiAgentRunner → AgentLoop            │
│                 ├─ ContextBuilder（上下文构建）        │
│                 ├─ ToolRegistry（工具注册/权限）       │
│                 ├─ KnowledgeRouter（知识库路由）       │
│                 └─ PendingStateStore（挂起状态）       │
├─────────────────────────────────────────────────────┤
│  LLM 层         LlmClient → LangChain4jLlmClient     │
│                 ChatModel / StreamingChatModel        │
├─────────────────────────────────────────────────────┤
│  记忆层         MemoryStore（RedisMemoryStore）       │
│                 MemoryExtractor / SummaryUtil         │
│                 EmbeddingClient                       │
├─────────────────────────────────────────────────────┤
│  基础设施       ChatHistoryStore / RedisLockHelper    │
│                 AiChatSseEmitter / AiChatExecutor     │
└─────────────────────────────────────────────────────┘
```

### 一次完整对话的时序

```
前端 → AiAgentService.chat()
  1. 清理旧状态（旧 emitter / 旧锁）
  2. 注册新 emitter、token、runId、requestId
  3. Redis 加锁（5s TTL）
  4. 会话初始化（newSession / assertOwner）
  5. 注册生命周期回调
  6. 提交到 AiChatExecutor 线程池
       └─ AiAgentRunner.run()
            a. 落库 user 消息
            b. 向量召回长期记忆
            c. ContextBuilder 构建上下文
            d. 注入系统提示词
            e. AgentLoop.run()
                 ├─ llmClient.chatStream() 流式调用
                 ├─ 若有 tool_calls → 执行工具 → 结果回填 → 下一轮
                 ├─ 若需确认 → 存 PendingState → 发 tool_confirm 事件 → 退出
                 └─ 无 tool_calls → 落库 assistant 消息 → 发 done 事件
            f. 异步抽取长期记忆
  7. finally 释放 Redis 锁
```

---

## 二、逐文件功能说明

### 1. `AiAgentRunner.java`

**职责**：Agent 运行的编排入口，串联「落库 → 记忆召回 → 上下文构建 → 系统提示注入 → AgentLoop → 记忆抽取」全流程，并保证锁释放。

### 2. `AgentContext.java`

**职责**：单次 Agent 运行的可变上下文载体。持有消息列表、工具上下文、SSE emitter、sessionId、assistantMessageId、runId、memoryId、已确认工具调用集合。`confirmedCalls` 用于「需确认工具」重启后跳过二次确认。

### 3. `AgentLoop.java`

**职责**：Agent 的核心循环（ReAct 风格）。最多 8 步，每步调用 LLM；若 LLM 返回 tool_calls 则执行工具并回填结果，否则输出最终答案并结束。支持：

- SSE 关闭检测（提前退出）
- 工具超时保护（30s）
- 工具权限二次校验
- 需确认工具挂起（存 Redis + 发 `tool_confirm` 事件）
- 最大步数兜底

### 4. `PendingStateStore.java`

**职责**：将需确认工具调用的中间状态（消息列表、已确认集合、runId、sessionId、assistantMessageId 等）序列化到 Redis，TTL 10 分钟。用户确认后由外部恢复。

### 5. `KnowledgeRouter.java`

**职责**：知识库向量路由。启动时后台预热所有知识库描述向量（文档标题拼接），请求时用问题向量与各知识库向量做余弦相似度，返回最相关（≥0.5 阈值）的知识库 ID。具备**失败自愈**：缓存为空且距上次尝试 > backoff 时，请求链路兜底重建。

### 6. `AiChatExecutor.java`

**职责**：AI 对话专用线程池。核心线程 0，最大并发可配（默认 16），有界队列（默认 100），拒绝策略返回 false 而非抛异常。空闲 60s 回收线程。关闭时优雅等待 30s。

### 7. `AsyncConfig.java`

**职责**：定义 `memoryExecutor` 线程池，供 `MemoryExtractor` 异步抽取记忆使用。

### 8. `LangChain4jConfig.java`

**职责**：统一配置 LangChain4j 的 `ChatModel`（非流式）、`StreamingChatModel`（流式）、`EmbeddingModel` 三个 Bean。安装 Conscrypt 提供 TLS 1.3，共享 JDK HttpClient。

### 9. `ChatHistoryStore.java` / `InMemoryChatHistoryStore.java`

**职责**：会话历史存储接口与内存实现（TODO 待 PG 表就绪后替换）。提供消息落库、会话列表、owner 校验、政策状态、assistant 消息读取等。

### 10. `RedisLockHelper.java`

**职责**：Redis 分布式锁。`tryLock` 用 `setIfAbsent` + TTL；`unlock` 用 Lua 脚本比对 token 后删除，避免误删他人锁。

### 11. `AiChatSseEmitter.java`

**职责**：SSE 发射器封装。内部 `closed` 标志位保证 `complete()` 幂等；`onCompletion`/`onTimeout` 触发关闭回调（用于中断 LLM 流）；提供 `send(String)`、`send(eventName, data)`、`send(SseEventBuilder)` 三种发送方式，IOException 时自动标记关闭。

### 12. `KnowledgeRegistrar.java` / `KnowledgeTemplateRegistry.java`

**职责**：知识库注册与模板缓存。`register` 校验知识库归属项目后，构建 `KnowledgeBase` 模板（含 embedding 模型、indexMode、topK），存入 `templateRegistry`（ConcurrentHashMap）。

### 13. `MessageRole.java` / `Message.java` / `LlmResponse.java` / `LlmClient.java`

**职责**：LLM 抽象层。`Message` 是统一消息模型（role/content/toolCallId/toolCalls）；`LlmResponse` 是 LLM 返回（content + toolCalls）；`LlmClient` 定义 `chatStream` 与 `chatSimple`。

### 14. `LangChain4jLlmClient.java`

**职责**：`LlmClient` 的 LangChain4j 实现。将内部 `Message`/`ToolSpec` 转换为 LangChain4j 的 `ChatMessage`/`ToolSpecification`，调用流式模型，通过 `StreamingChatResponseHandler` 回调推送 delta，收集 tool_calls，最终返回 `LlmResponse`。120s 超时。

### 15. `ContextBuilder.java`

**职责**：构建 LLM 上下文。加载历史消息 → 分离 system/dialog → 注入长期记忆块 → 超过 6 轮时对老消息做摘要 → 控制总字符数（24000）→ 可选追加本轮 user 消息。

### 16. `EmbeddingClient.java` / `LangChain4jEmbeddingClient.java`

**职责**：向量化接口与实现。复用 `EmbeddingModel` Bean，将文本转为 `List<Double>`。

### 17. `MemoryDecayJob.java`

**职责**：每日 3 点执行记忆衰减。遍历活跃用户，对每条记忆 importance × 0.95，删除 importance < 0.1 的记忆。

### 18. `MemoryExtractor.java`

**职责**：异步从一轮对话中抽取长期记忆。调用 `SummaryUtil.extractLongTermMemoriesAsList` 拿到结构化列表，逐条去重、embedding、保存。单条失败不影响整批。

### 19. `MemoryItem.java` / `MemoryStore.java` / `RedisMemoryStore.java`

**职责**：长期记忆模型与存储。`RedisMemoryStore` 用 RediSearch 建 HNSW 向量索引，支持 `searchByUser`（KNN 检索）、`findByUserAndContentLike`、`invalidate`、`decayAll`、`deleteBelow`、`getAllActiveUsers`（SCAN）。主数据用原生 Lettuce 字节读写，辅助索引用 StringRedisTemplate。

### 20. `SummaryUtil.java`

**职责**：对话摘要 + 长期记忆抽取。含：

- `summarize`：带 Redis 缓存的摘要生成，防御 LLM 返回 requestId
- `extractLongTermMemoriesAsList`：LLM 抽取 + JSON 解析 + 字段校验规范化
- `stripCodeFence`：剥离 markdown 代码块
- `looksLikeId`：识别 UUID/hex id，防止脏数据入库

### 21. `AiAgentService.java`

**职责**：对话入口与生命周期管理。维护 `runningEmitters/runningRunIds/runningTokens/runningRequestIds` 四个 ConcurrentHashMap。核心逻辑：

- 新请求到来时清理旧状态（forceUnlock → 关闭旧 emitter）
- 注册新状态 → 加锁 → 初始化会话 → 注册回调 → 提交线程池
- `stop` 支持按 clientRequestId 精确停止

### 22. `KnowledgeService.java`

**职责**：知识库服务接口（queryAnswer、queryKnowledgeBase、getDocuments 等），由具体实现对接下游知识库网关。

### 23. `AiTool.java` / `ToolSpec.java` / `ToolResult.java` / `ToolRegistry.java`

**职责**：工具抽象。`AiTool` 定义 name/description/parameterSchema/execute/allowedRoles/requiresConfirmation；`ToolRegistry` 注册所有 `AiTool` Bean，提供按角色过滤的可用工具列表和执行前二次校验。

### 24. `AiToolContext.java`

**职责**：工具执行上下文（ThreadLocal）。从 Redis 读取用户真实角色列表，供工具做权限判断。

### 25. `CreateTicketTool.java` / `DateTool.java` / `QueryKnowledgeTool.java`

**职责**：

- **CreateTicketTool**：创建工单，`requiresConfirmation=true`，需用户确认后执行。
- **DateTool**：获取当前日期 / 计算天数差，禁止 LLM 凭记忆回答日期。
- **QueryKnowledgeTool**：知识库精准问答。knowledgeBaseId 可选（需 13 位以上纯数字且已注册），否则由 KnowledgeRouter 自动路由。

---

## 三、给所有代码逐行填注释

由于篇幅限制，以下为**关键文件的逐行注释**（核心逻辑文件），其余文件注释相对简单。

---

### `AiAgentRunner.java`（逐行注释）

```java
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
            messages.add(0, Message.system("""                                  //    在最前面插入 system 消息
                你是一个企业智能助手，必须严格遵守以下规则：                       //    规则正文（多行文本块）
                                                                                    //    空行
                1. **优先使用工具**：当用户询问业务数据、制度、流程、待办、审批等问题时，必须调用知识库工具（query_knowledge）检索，不要用自己的知识编造答案。 // 规则1
                2. **基于检索结果作答**：工具返回的结果是唯一可信来源。如果检索结果为空或不确定，明确告知用户"未找到相关信息"，不要编造。 // 规则2
                3. **知识库 ID 规则**：除非用户明确给出 13 位及以上纯数字的知识库 ID，否则不要传 knowledgeBaseId 参数，系统会自动路由到最相关的知识库。 // 规则3
                4. **答案格式**：先直接回答问题，再引用检索到的具体内容（不要编造引用来源）。 // 规则4
                5. **审核/审批相关问题**：涉及审核人、审核流程、审核频率等问题，必须调用知识库工具检索相关制度文档。 // 规则5
                                                                                    //    空行
                如果用户问题与业务无关（如闲聊、通用知识），可以直接回答，但仍需保持专业友好的语气。 // 兜底规则
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
```

---

### `AgentLoop.java`（逐行注释）

```java
package com.lenovo.ai.agent.loop;                                               // 包声明

import com.lenovo.ai.infra.history.ChatHistoryStore;                            // 历史存储
import com.lenovo.ai.infra.sse.AiChatSseEmitter;                                // SSE 发射器
import com.lenovo.ai.llm.LlmClient;                                             // LLM 客户端接口
import com.lenovo.ai.llm.LlmResponse;                                           // LLM 响应
import com.lenovo.ai.llm.Message;                                               // 消息模型
import com.lenovo.ai.memory.MemoryStore;                                        // 记忆存储（当前未使用，可能预留）
import com.lenovo.ai.tools.AiTool;                                              // 工具接口
import com.lenovo.ai.tools.ToolRegistry;                                        // 工具注册表
import com.lenovo.ai.tools.ToolResult;                                          // 工具结果
import com.lenovo.ai.tools.ToolSpec;                                            // 工具描述
import com.lenovo.ai.tools.context.AiToolContext;                               // 工具上下文
import lombok.RequiredArgsConstructor;                                          // 构造注入
import lombok.extern.slf4j.Slf4j;                                               // 日志
import org.springframework.stereotype.Component;                                // Spring 组件

import java.util.ArrayList;                                                     // 动态数组
import java.util.List;                                                          // 列表
import java.util.Map;                                                           // Map
import java.util.concurrent.CompletableFuture;                                  // 异步任务
import java.util.concurrent.TimeUnit;                                           // 时间单位

@Slf4j                                                                          // 日志
@Component                                                                      // 注册 Bean
@RequiredArgsConstructor                                                        // 构造注入
public class AgentLoop {                                                        // Agent 主循环

    private static final int MAX_STEPS = 8;                                     // 最大工具调用步数（防止无限循环）
    private static final int TOOL_TIMEOUT_SECONDS = 30;                         // 单次工具执行超时（秒）

    private final LlmClient llmClient;                                          // LLM 客户端
    private final ToolRegistry toolRegistry;                                    // 工具注册表
    private final ChatHistoryStore history;                                     // 历史存储
    private final PendingStateStore pendingStateStore;                          // 挂起状态存储（需确认工具）
    private final MemoryStore memoryStore;                                      // 记忆存储（当前未使用）

    /**                                                                         // Javadoc
     * 运行 Agent 循环。                                                          // 说明
     */                                                                         // 结束
    public void run(AgentContext ctx) {                                         // 入口方法
        try {                                                                   // 主流程 try
            List<Message> messages = new ArrayList<>(ctx.messages());           // 1. 拷贝消息列表（避免外部修改）
            List<ToolSpec> tools = toolRegistry.availableTools(ctx.toolContext());// 2. 按角色过滤可用工具
            log.info(">>> AgentLoop 启动, runId={}, 可用工具 {} 个", ctx.runId(), tools.size()); // 日志

            for (int step = 0; step < MAX_STEPS; step++) {                      // 3. 循环最多 MAX_STEPS 次
                // SSE 连接已关闭（被 stop 或新请求取代），立即退出                 //    提前退出条件
                if (ctx.emitter().isClosed()) {                                 // 4. 检测 SSE 是否关闭
                    log.info(">>> AgentLoop 检测到 SSE 已关闭, runId={}, step={}", ctx.runId(), step); // 日志
                    return;                                                     //    直接返回
                }
                log.info(">>> AgentLoop step={}, runId={}", step, ctx.runId()); // 5. 记录步数

                LlmResponse resp = llmClient.chatStream(messages, tools, ctx.emitter()); // 6. 流式调用 LLM

                if (!resp.hasToolCalls()) {                                     // 7. 若无工具调用 → 最终答案
                    history.appendAssistant(ctx.assistantMessageId(), ctx.sessionId(), resp.content()); // 7.1 落库 assistant 消息
                    sendEvent(ctx.emitter(), "done", Map.of("messageId", ctx.assistantMessageId())); // 7.2 发送 done 事件
                    ctx.emitter().complete();                                   // 7.3 关闭 SSE
                    return;                                                     // 7.4 结束
                }

                List<Message.ToolCallRef> refs = resp.toolCalls().stream()      // 8. 有工具调用：转换格式
                        .map(tc -> new Message.ToolCallRef(tc.id(), tc.name(), tc.args())) //    映射为内部 ToolCallRef
                        .toList();                                              //    收集为 List
                messages.add(Message.assistantWithTools(resp.content(), refs)); // 8.1 将 assistant+toolCalls 加入消息列表

                for (LlmResponse.ToolCall call : resp.toolCalls()) {            // 9. 逐个执行工具调用
                    AiTool tool = toolRegistry.get(call.name());                // 9.1 查找工具
                    if (tool == null) {                                         // 9.2 工具不存在
                        messages.add(Message.toolResult(call.id(), "工具不存在: " + call.name())); //    回填错误结果
                        continue;                                               //    继续下一个
                    }

                    if (!toolRegistry.isAllowed(call.name(), ctx.toolContext())) { // 9.3 权限二次校验
                        messages.add(Message.toolResult(call.id(), "无权限调用该工具")); //    回填拒绝结果
                        sendEvent(ctx.emitter(), "tool", Map.of(                //    发送 tool 事件
                                "name", call.name(),                            //    工具名
                                "status", "denied"                              //    状态：拒绝
                        ));
                        continue;                                               //    继续下一个
                    }

                    if (tool.requiresConfirmation() && !ctx.confirmed(call.id())) { // 9.4 需确认且未确认
                        replaceMessages(ctx.messages(), messages);              //    将本地消息列表回写 ctx
                        pendingStateStore.save(ctx);                            //    保存挂起状态到 Redis
                        sendEvent(ctx.emitter(), "tool_confirm", Map.of(        //    发送 tool_confirm 事件
                                "callId", call.id(),                            //    调用 ID
                                "tool", call.name(),                            //    工具名
                                "args", call.args()                             //    参数
                        ));
                        ctx.emitter().complete();                               //    关闭 SSE（等待用户确认后恢复）
                        return;                                                 //    结束本次 run
                    }

                    sendEvent(ctx.emitter(), "tool", Map.of(                    // 9.5 发送 tool 事件（执行中）
                            "name", call.name(),                                //    工具名
                            "args", call.args(),                                //    参数
                            "status", "executing"                               //    状态：执行中
                    ));

                    ToolResult result = executeWithGuard(tool, call, ctx);      // 9.6 带超时/异常保护执行工具

                    sendEvent(ctx.emitter(), "tool", Map.of(                    // 9.7 发送 tool 事件（完成）
                            "name", call.name(),                                //    工具名
                            "args", call.args(),                                //    参数
                            "status", result.success() ? "completed" : "failed",//    状态
                            "result", result.content()                          //    结果内容
                    ));

                    messages.add(Message.toolResult(call.id(), result.content())); // 9.8 将工具结果加入消息列表（下一轮 LLM 可见）
                }
            }

            history.appendAssistant(ctx.assistantMessageId(), ctx.sessionId(), "[已达到最大工具调用步数]"); // 10. 达到最大步数：落库兜底消息
            sendEvent(ctx.emitter(), "done", Map.of("messageId", ctx.assistantMessageId())); // 11. 发送 done
            ctx.emitter().complete();                                           // 12. 关闭 SSE

        } catch (Exception e) {                                                 // 13. 异常兜底
            log.error("AgentLoop 异常, runId={}", ctx.runId(), e);              //    日志
            try {                                                               //    尝试发送 error 事件
                sendEvent(ctx.emitter(), "error", Map.of(                       //    发送 error
                    "code", "INTERNAL_ERROR",                                   //    错误码
                    "message", "处理失败: " + e.getMessage()                    //    错误消息
                ));
            } catch (Exception ignored) {}                                      //    忽略发送异常
            try {                                                               //    尝试关闭 SSE
                ctx.emitter().complete();                                       //    关闭
            } catch (Exception ignored) {}                                      //    忽略异常
        }
    }

    /**                                                                         // Javadoc
     * 将 source 内容覆盖到 target（用于挂起前回写 ctx.messages）。                // 说明
     */                                                                         // 结束
    private void replaceMessages(List<Message> target, List<Message> source) {  // 覆盖方法
        target.clear();                                                         // 清空目标
        target.addAll(source);                                                  // 添加源内容
    }

    /**                                                                         // Javadoc
     * 带超时与异常保护的工具执行。                                                // 说明
     */                                                                         // 结束
    private ToolResult executeWithGuard(AiTool tool, LlmResponse.ToolCall call, AgentContext ctx) { // 方法签名
        try {                                                                   // 外层 try
            return CompletableFuture.supplyAsync(() -> {                        // 异步执行
                        AiToolContext.set(ctx.toolContext());                   //    设置 ThreadLocal 上下文
                        try {                                                   //    内层 try
                            return tool.execute(call.args(), ctx.toolContext());//    执行工具
                        } finally {                                             //    finally
                            AiToolContext.clear();                              //    清理 ThreadLocal（防止线程池污染）
                        }
                    })
                    .orTimeout(TOOL_TIMEOUT_SECONDS, TimeUnit.SECONDS)          // 超时设置
                    .exceptionally(e -> {                                       // 异常处理
                        log.error("工具执行异常: {}", tool.name(), e);            //    日志
                        return ToolResult.fail("工具执行失败: " + e.getMessage()); //    返回失败结果
                    })
                    .join();                                                    // 阻塞等待
        } catch (Exception e) {                                                 // 提交异常
            log.error("工具提交失败: {}", tool.name(), e);                        //    日志
            return ToolResult.fail("工具执行失败: " + e.getMessage());            //    返回失败结果
        }
    }

    /**                                                                         // Javadoc
     * 发送 SSE 事件（统一前端事件名格式）。                                        // 说明
     */                                                                         // 结束
    private void sendEvent(AiChatSseEmitter emitter, String name, Map<String, Object> data) { // 方法签名
        // 统一事件名为前端约定的格式                                             // 注释
        String frontendEventName = switch (name) {                              // 事件名映射
            case "tool_call" -> "tool";                                         //    tool_call → tool
            case "tool_confirm" -> "tool_confirm";                              //    tool_confirm 保持不变
            default -> name;                                                    //    其他保持不变
        };
        emitter.send(frontendEventName, data);                                  // 发送事件
    }
}
```

---

### `LangChain4jLlmClient.java`（逐行注释）

```java
package com.lenovo.ai.llm.impl;                                                 // 包声明

import com.fasterxml.jackson.core.type.TypeReference;                           // JSON 类型引用
import com.fasterxml.jackson.databind.ObjectMapper;                             // JSON 序列化
import com.lenovo.ai.infra.sse.AiChatSseEmitter;                                // SSE 发射器
import com.lenovo.ai.llm.LlmClient;                                             // LLM 客户端接口
import com.lenovo.ai.llm.LlmResponse;                                           // LLM 响应
import com.lenovo.ai.llm.Message;                                               // 消息模型
import com.lenovo.ai.llm.enums.MessageRole;                                     // 消息角色枚举
import com.lenovo.ai.tools.ToolSpec;                                            // 工具描述
import dev.langchain4j.agent.tool.ToolExecutionRequest;                         // L4j 工具执行请求
import dev.langchain4j.agent.tool.ToolSpecification;                            // L4j 工具描述
import dev.langchain4j.data.message.AiMessage;                                  // L4j AI 消息
import dev.langchain4j.data.message.ChatMessage;                                // L4j 聊天消息
import dev.langchain4j.data.message.SystemMessage;                              // L4j 系统消息
import dev.langchain4j.data.message.ToolExecutionResultMessage;                 // L4j 工具结果消息
import dev.langchain4j.data.message.UserMessage;                                // L4j 用户消息
import dev.langchain4j.model.chat.ChatModel;                                    // L4j 非流式模型
import dev.langchain4j.model.chat.StreamingChatModel;                           // L4j 流式模型
import dev.langchain4j.model.chat.request.ChatRequest;                          // L4j 聊天请求
import dev.langchain4j.model.chat.request.json.JsonObjectSchema;                // L4j JSON Schema
import dev.langchain4j.model.chat.response.ChatResponse;                        // L4j 聊天响应
import dev.langchain4j.model.chat.response.StreamingChatResponseHandler;        // L4j 流式回调
import lombok.extern.slf4j.Slf4j;                                               // 日志
import org.springframework.stereotype.Component;                                // Spring 组件

import java.util.ArrayList;                                                     // 数组
import java.util.HashMap;                                                       // HashMap
import java.util.List;                                                          // 列表
import java.util.Map;                                                           // Map
import java.util.concurrent.CompletableFuture;                                  // 异步任务
import java.util.concurrent.TimeUnit;                                           // 时间单位

/**                                                                             // Javadoc
 * 基于 LangChain4j 的 LlmClient 实现：真流式输出 + 工具调用。                       // 说明
 */                                                                             // 结束
@Slf4j                                                                          // 日志
@Component                                                                      // 注册 Bean
public class LangChain4jLlmClient implements LlmClient {                        // 实现类

    private static final int STREAM_TIMEOUT_SECONDS = 120;                      // 流式调用超时（秒）
    private static final ObjectMapper MAPPER = new ObjectMapper();              // 共享 JSON 序列化器

    private final StreamingChatModel streamingModel;                            // 流式模型
    private final ChatModel chatModel;                                          // 非流式模型

    /**                                                                         // Javadoc
     * 构造注入两个模型。                                                         // 说明
     */                                                                         // 结束
    public LangChain4jLlmClient(StreamingChatModel streamingModel, ChatModel chatModel) { // 构造器
        this.streamingModel = streamingModel;                                   // 赋值
        this.chatModel = chatModel;                                             // 赋值
    }

    @Override                                                                   // 实现接口
    public LlmResponse chatStream(List<Message> messages, List<ToolSpec> tools, AiChatSseEmitter emitter) { // 流式调用
        List<ChatMessage> chatMessages = toChatMessages(messages);              // 1. 内部消息 → L4j 消息
        List<ToolSpecification> toolSpecs = toToolSpecifications(tools);        // 2. 内部工具 → L4j 工具描述

        ChatRequest.Builder requestBuilder = ChatRequest.builder().messages(chatMessages); // 3. 构建请求
        if (!toolSpecs.isEmpty()) {                                             // 4. 若有工具
            requestBuilder.toolSpecifications(toolSpecs);                       //    加入工具描述
        }
        ChatRequest request = requestBuilder.build();                           // 5. 构建完成

        CompletableFuture<LlmResponse> future = new CompletableFuture<>();      // 6. 异步结果容器
        StringBuilder fullContent = new StringBuilder();                        // 7. 累积完整内容
        List<ToolExecutionRequest> toolRequests = new ArrayList<>();            // 8. 累积工具请求

        streamingModel.chat(request, new StreamingChatResponseHandler() {       // 9. 发起流式调用
            @Override                                                           // 回调：增量
            public void onPartialResponse(String partialResponse) {             // 增量片段
                if (partialResponse != null && !partialResponse.isEmpty()) {    //    非空
                    fullContent.append(partialResponse);                        //    累积
                    emitter.send("delta", Map.of("text", partialResponse));     //    推送 delta 事件
                }
            }

            @Override                                                           // 回调：完成
            public void onCompleteResponse(ChatResponse completeResponse) {     // 完成响应
                try {                                                           //    捕获异常
                    AiMessage aiMessage = completeResponse.aiMessage();         //    取 AI 消息
                    String content = aiMessage.text();                          //    取文本
                    if (content != null && !content.isEmpty()) {                //    非空
                        fullContent.setLength(0);                               //    清空累积
                        fullContent.append(content);                            //    写入完整内容
                        emitter.send("done", Map.of("answer", content));        //    推送 done 事件
                    }
                    if (aiMessage.hasToolExecutionRequests()) {                 //    有工具请求
                        toolRequests.addAll(aiMessage.toolExecutionRequests()); //    收集
                        emitter.send("tool", Map.of("name", "tool_execution")); //    推送 tool 事件
                    }
                    future.complete(toLlmResponse(fullContent.toString(), toolRequests)); // 完成 future
                } catch (Exception e) {                                         //    异常
                    future.completeExceptionally(e);                            //    异常完成
                }
            }

            @Override                                                           // 回调：错误
            public void onError(Throwable error) {                              // 错误
                log.error("StreamingChatResponseHandler onError", error);       //    日志
                emitter.send("error", Map.of("message", error.getMessage()));   //    推送 error
                future.completeExceptionally(error);                            //    异常完成
            }
        });

        try {                                                                   // 10. 等待结果
            return future.get(STREAM_TIMEOUT_SECONDS, TimeUnit.SECONDS);        //    带超时获取
        } catch (Exception e) {                                                 //    超时/异常
            log.error("chatStream 超时或异常", e);                                //    日志
            return new LlmResponse("", List.of());                              //    返回空响应
        }
    }

    @Override                                                                   // 实现接口
    public String chatSimple(String prompt) {                                   // 非流式简单调用
        if (prompt == null || prompt.isBlank()) return "";                      // 1. 空入参返回空
        try {                                                                   // 2. try
            ChatMessage userMsg = UserMessage.from(prompt);                     //    构造用户消息
            ChatRequest request = ChatRequest.builder()                         //    构造请求
                    .messages(List.of(userMsg))                                 //    消息列表
                    .build();                                                   //    构建
            ChatResponse response = chatModel.chat(request);                    //    调用模型
            AiMessage aiMessage = response.aiMessage();                         //    取 AI 消息
            return aiMessage.text() != null ? aiMessage.text() : "";            //    返回文本
        } catch (Exception e) {                                                 // 3. 异常
            log.error("chatSimple 异常: {}", prompt.length(), e);                //    日志
            return "";                                                          //    返回空
        }
    }

    /**                                                                         // Javadoc
     * 内部消息列表 → L4j ChatMessage 列表。                                       // 说明
     */                                                                         // 结束
    private List<ChatMessage> toChatMessages(List<Message> messages) {          // 转换方法
        List<ChatMessage> result = new ArrayList<>();                           // 结果列表
        for (Message msg : messages) {                                          // 遍历
            if (msg.role() == MessageRole.SYSTEM) {                             // SYSTEM
                result.add(new SystemMessage(msg.content()));                   //    系统消息
            } else if (msg.role() == MessageRole.USER) {                        // USER
                result.add(UserMessage.from(msg.content()));                    //    用户消息
            } else if (msg.role() == MessageRole.ASSISTANT) {                   // ASSISTANT
                if (msg.toolCalls() != null && !msg.toolCalls().isEmpty()) {    //    有工具调用
                    List<ToolExecutionRequest> requests = msg.toolCalls().stream() // 映射
                            .map(ref -> ToolExecutionRequest.builder()          //    构建
                                    .id(ref.id())                               //    ID
                                    .name(ref.name())                           //    名称
                                    .arguments(toJson(ref.args()))              //    参数 JSON
                                    .build())                                   //    构建
                            .toList();                                          //    收集
                    result.add(AiMessage.from(requests));                       //    AI 消息（含工具调用）
                } else {                                                        //    无工具调用
                    result.add(AiMessage.from(msg.content()));                  //    AI 文本消息
                }
            } else if (msg.role() == MessageRole.TOOL) {                        // TOOL
                result.add(ToolExecutionResultMessage.from(msg.toolCallId(), null, msg.content())); // 工具结果
            }
        }
        return result;                                                          // 返回
    }

    /**                                                                         // Javadoc
     * 内部工具描述 → L4j ToolSpecification。                                      // 说明
     */                                                                         // 结束
    @SuppressWarnings("unchecked")                                              // 抑制未检查警告
    private List<ToolSpecification> toToolSpecifications(List<ToolSpec> tools) {// 转换
        if (tools == null || tools.isEmpty()) return List.of();                 // 空返回
        return tools.stream().map(spec -> {                                     // 流式映射
            JsonObjectSchema.Builder schemaBuilder = JsonObjectSchema.builder();// 构建 Schema
            Map<String, Object> schema = spec.parameterSchema();                // 取参数 Schema
            if (schema != null) {                                               // 非空
                Object propsObj = schema.get("properties");                     // 取 properties
                if (propsObj instanceof Map<?, ?> properties) {                 // 类型判断
                    List<String> requiredList = new ArrayList<>();              // required 列表
                    Object reqObj = schema.get("required");                     // 取 required
                    if (reqObj instanceof List<?> reqList) {                    // 类型判断
                        for (Object r : reqList) {                              // 遍历
                            if (r != null) requiredList.add(String.valueOf(r)); // 添加
                        }
                    }
                    properties.forEach((nameObj, paramObj) -> {                 // 遍历属性
                        String name = String.valueOf(nameObj);                  // 属性名
                        if (paramObj instanceof Map<?, ?> rawMap) {             // 类型判断
                            Map<String, Object> paramMap = (Map<String, Object>) rawMap; // 强转
                            String type = String.valueOf(paramMap.getOrDefault("type", "string")); // 类型
                            String desc = String.valueOf(paramMap.getOrDefault("description", "")); // 描述
                            switch (type) {                                     // 类型分支
                                case "integer" -> schemaBuilder.addIntegerProperty(name, desc); // 整数
                                case "number" -> schemaBuilder.addNumberProperty(name, desc);   // 数字
                                case "boolean" -> schemaBuilder.addBooleanProperty(name, desc); // 布尔
                                default -> schemaBuilder.addStringProperty(name, desc);         // 字符串
                            }
                        }
                    });
                    if (!requiredList.isEmpty()) schemaBuilder.required(requiredList); // 设置 required
                }
            }
            return ToolSpecification.builder()                                  // 构建工具描述
                    .name(spec.name())                                          // 名称
                    .description(spec.description())                            // 描述
                    .parameters(schemaBuilder.build())                          // 参数 Schema
                    .build();                                                   // 构建
        }).toList();                                                            // 收集
    }

    /**                                                                         // Javadoc
     * 内部构造 LlmResponse。                                                     // 说明
     */                                                                         // 结束
    private LlmResponse toLlmResponse(String content, List<ToolExecutionRequest> requests) { // 方法
        if (requests.isEmpty()) {                                               // 无工具调用
            return new LlmResponse(content, List.of());                         //    返回纯文本
        }
        List<LlmResponse.ToolCall> toolCalls = requests.stream().map(req -> {   // 映射工具调用
            Map<String, Object> args = parseJson(req.arguments());              // 解析参数
            return new LlmResponse.ToolCall(req.id(), req.name(), args);        // 构造
        }).toList();                                                            // 收集
        return new LlmResponse(content, toolCalls);                             // 返回
    }

    /**                                                                         // Javadoc
     * Map → JSON 字符串。                                                       // 说明
     */                                                                         // 结束
    private String toJson(Map<String, Object> map) {                            // 方法
        if (map == null || map.isEmpty()) return "{}";                          // 空返回 {}
        try {                                                                   // try
            return MAPPER.writeValueAsString(map);                              // 序列化
        } catch (Exception e) {                                                 // 异常
            log.warn("toJson failed", e);                                       //    日志
            return "{}";                                                        //    返回 {}
        }
    }

    /**                                                                         // Javadoc
     * JSON 字符串 → Map。                                                       // 说明
     */                                                                         // 结束
    private Map<String, Object> parseJson(String json) {                        // 方法
        if (json == null || json.isBlank()) return Map.of();                    // 空返回
        try {                                                                   // try
            return MAPPER.readValue(json, new TypeReference<Map<String, Object>>() {}); // 反序列化
        } catch (Exception e) {                                                 // 异常
            log.warn("parseJson failed: {}", json, e);                          //    日志
            return Map.of();                                                    //    返回空
        }
    }
}
```

---

### `AiAgentService.java`（逐行注释）

```java
package com.lenovo.ai.service;                                                  // 包声明

import com.lenovo.ai.agent.AiAgentRunner;                                       // Agent 运行器
import com.lenovo.ai.agent.runtime.AiChatExecutor;                              // AI 专用线程池
import com.lenovo.ai.infra.history.ChatHistoryStore;                            // 历史存储
import com.lenovo.ai.infra.lock.RedisLockHelper;                                // Redis 锁
import com.lenovo.ai.infra.sse.AiChatSseEmitter;                                // SSE 发射器
import com.lenovo.ai.tools.context.AiToolContext;                               // 工具上下文
import com.lenovo.controller.ai.AiAgentController.ChatRequest;                  // 请求体
import com.lenovo.util.RedisUtils;                                              // Redis 工具
import lombok.extern.slf4j.Slf4j;                                               // 日志
import org.springframework.context.i18n.LocaleContextHolder;                    // 国际化上下文
import org.springframework.stereotype.Service;                                  // Spring 服务
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;        // SSE 基类

import java.util.Locale;                                                        // 语言
import java.util.Map;                                                           // Map
import java.util.UUID;                                                          // UUID
import java.util.concurrent.ConcurrentHashMap;                                  // 并发 Map

@Slf4j                                                                          // 日志
@Service                                                                        // 注册服务
public class AiAgentService {                                                   // 对话服务入口

    private final ChatHistoryStore history;                                     // 历史存储
    private final RedisLockHelper redisLockHelper;                              // 锁工具
    private final RedisUtils redisUtils;                                        // Redis 工具
    private final AiChatExecutor chatExecutor;                                  // AI 线程池
    private final AiAgentRunner runner;                                         // Agent 运行器

    private final Map<String, AiChatSseEmitter> runningEmitters = new ConcurrentHashMap<>(); // memoryId → 当前 emitter
    private final Map<String, String> runningRunIds = new ConcurrentHashMap<>();             // memoryId → runId
    private final Map<String, String> runningTokens = new ConcurrentHashMap<>();             // memoryId → 锁 token
    private final Map<String, String> runningRequestIds = new ConcurrentHashMap<>();         // memoryId → 客户端请求 ID

    /**                                                                         // Javadoc
     * 构造注入。                                                                 // 说明
     */                                                                         // 结束
    public AiAgentService(ChatHistoryStore history,                             // 构造器
                          RedisLockHelper redisLockHelper,
                          RedisUtils redisUtils,
                          AiChatExecutor chatExecutor,
                          AiAgentRunner runner) {
        this.history = history;                                                 // 赋值
        this.redisLockHelper = redisLockHelper;                                 // 赋值
        this.redisUtils = redisUtils;                                           // 赋值
        this.chatExecutor = chatExecutor;                                       // 赋值
        this.runner = runner;                                                   // 赋值
    }

    /**                                                                         // Javadoc
     * 对话入口：创建 SSE，注册状态，加锁，提交线程池。                             // 说明
     */                                                                         // 结束
    public SseEmitter chat(ChatRequest request, String userId) {                // 方法
        Locale locale = LocaleContextHolder.getLocale();                        // 1. 取当前语言
        String sessionId = request.sessionId();                                 // 2. 取 sessionId
        String memoryId = userId + ":" + sessionId;                             // 3. 构造 memoryId（用户+会话唯一键）
        String lockKey = "ai:chat:lock:" + memoryId;                            // 4. 构造锁 key
        String token = UUID.randomUUID().toString();                            // 5. 生成本次锁 token
        String newRunId = UUID.randomUUID().toString();                         // 6. 生成本次 runId
        String requestId = (request.clientRequestId() != null && !request.clientRequestId().isBlank()) // 7. 客户端请求 ID
                ? request.clientRequestId()                                     //    有则用
                : UUID.randomUUID().toString();                                 //    无则生成

        AiChatSseEmitter emitter = new AiChatSseEmitter();                      // 8. 创建 SSE 发射器

        try {                                                                   // 9. 主流程
            // 1. 清理旧状态（先取 token、forceUnlock，再关闭旧 emitter，避免 onCompletion 提前清 token） // 注释
            String oldToken = runningTokens.remove(memoryId);                   // 9.1 取旧 token 并移除
            if (oldToken != null) {                                             //    旧 token 存在
                redisLockHelper.forceUnlock(lockKey);                           //    强制释放旧锁
                log.info(">>> 强制释放旧 Redis 锁: lockKey={}, oldToken={}", lockKey, oldToken); // 日志
            }

            AiChatSseEmitter oldEmitter = runningEmitters.remove(memoryId);     // 9.2 取旧 emitter 并移除
            if (oldEmitter != null && !oldEmitter.isClosed()) {                 //    旧 emitter 存在且未关闭
                try {                                                           //    try
                    oldEmitter.send("error", Map.of("message", "会话已被新请求替换")); //    发送替换通知
                    oldEmitter.complete();                                      //    关闭
                    log.info(">>> 已关闭旧 emitter: memoryId={}", memoryId);      //    日志
                } catch (Exception e) {                                         //    异常
                    log.warn("关闭旧 emitter 异常: memoryId={}", memoryId, e);    //    日志
                }
            }

            // 2. 注册当前请求状态                                             // 注释
            runningEmitters.put(memoryId, emitter);                             // 9.3 注册 emitter
            runningTokens.put(memoryId, token);                                 //    注册 token
            runningRunIds.put(memoryId, newRunId);                              //    注册 runId
            runningRequestIds.put(memoryId, requestId);                         //    注册 requestId

            // 3. 获取锁                                                       // 注释
            boolean locked = redisLockHelper.tryLock(lockKey, token, 5_000L);   // 9.4 尝试加锁（5s TTL）
            if (!locked) {                                                      //    加锁失败
                log.error(">>> 锁冲突，关闭新请求: memoryId={}, oldToken={}", memoryId, oldToken); // 日志
                runningEmitters.remove(memoryId, emitter);                      //    清理状态
                runningTokens.remove(memoryId, token);                          //    清理
                runningRunIds.remove(memoryId, newRunId);                       //    清理
                runningRequestIds.remove(memoryId, requestId);                  //    清理
                try {                                                           //    try
                    emitter.send(SseEmitter.event().name("error")               //    发送 error
                            .data(Map.of("message", "上一次请求尚未完成，请稍后再试"))); //    消息
                } catch (Exception ignored) {}                                  //    忽略
                emitter.complete();                                             //    关闭
                return emitter;                                                 //    返回
            }
            log.info(">>> 成功获取锁: memoryId={}, requestId={}", memoryId, requestId); // 9.5 日志

            // 4. 会话初始化                                                   // 注释
            boolean newSession = !history.exists(sessionId);                    // 9.6 判断是否新会话
            if (!newSession) history.assertOwner(sessionId, userId);            //    非新会话校验 owner

            // ✅ 注意：AiToolContext.capture 需要 3 个参数（userId, roles, redisUtils） // 注释
            // 如果你没有 RoleUtils 字段，请检查 AiToolContext 的签名                  // 注释
            AiToolContext context = AiToolContext.capture(userId, redisUtils);  // 9.7 构造工具上下文
            long userMessageId = history.nextMessageId();                       // 9.8 分配 user 消息 ID
            long assistantMessageId = history.nextMessageId();                  //    分配 assistant 消息 ID

            // 5. 注册生命周期回调                                              // 注释
            emitter.onCompletion(() -> {                                        // 9.9 完成回调
                runningEmitters.remove(memoryId, emitter);                      //    清理 emitter
                runningRunIds.remove(memoryId, newRunId);                       //    清理 runId
                runningTokens.remove(memoryId, token);                          //    清理 token
                runningRequestIds.remove(memoryId, requestId);                  //    清理 requestId
            });
            emitter.onTimeout(() -> {                                           // 9.10 超时回调
                try { emitter.complete(); } catch (Exception ignored) {}        //    关闭
                runningRequestIds.remove(memoryId);                             //    清理 requestId
                redisLockHelper.unlock(lockKey, token);                         //    释放锁
            });

            // 6. 提交任务                                                     // 注释
            chatExecutor.execute(() -> runner.run(                              // 9.11 提交线程池
                    request, context, userId, newSession, sessionId,            //    参数
                    userMessageId, assistantMessageId, locale,                  //    参数
                    memoryId, lockKey, token, emitter, newRunId));              //    参数

            return emitter;                                                     // 9.12 返回 emitter

        } catch (Exception e) {                                                 // 10. 入口异常
            log.error("chat() 入口异常", e);                                     //    日志
            try {                                                               //    try
                emitter.send(SseEmitter.event().name("error")                   //    发送 error
                        .data(Map.of("message", "系统繁忙，请稍后再试")));         //    消息
            } catch (Exception ignored) {}                                      //    忽略
            emitter.complete();                                                 //    关闭
            return emitter;                                                     //    返回
        }
    }

    /**                                                                         // Javadoc
     * 停止指定会话。                                                             // 说明
     */                                                                         // 结束
    public void stop(String sessionId, String userId, String clientRequestId) { // 方法
        if (sessionId == null || sessionId.isBlank() || userId == null) return; // 1. 参数校验
        String memoryId = userId + ":" + sessionId;                             // 2. 构造 memoryId
        log.info(">>> 停止会话: memoryId={}, clientRequestId={}", memoryId, clientRequestId); // 3. 日志

        String storedRequestId = runningRequestIds.get(memoryId);               // 4. 取存储的 requestId
        if (clientRequestId != null && !clientRequestId.isBlank()) {            // 5. 若客户端传了 requestId
            boolean requestIdMatches = clientRequestId.equals(storedRequestId); //    比对
            if (!requestIdMatches) {                                            //    不匹配
                log.info("stop() clientRequestId 不匹配，跳过关闭: stored={}, requested={}", // 日志
                        storedRequestId, clientRequestId);
                return;                                                         //    直接返回
            }
        }

        AiChatSseEmitter emitter = runningEmitters.remove(memoryId);            // 6. 取 emitter 并移除
        if (emitter != null) {                                                  //    存在
            try {                                                               //    try
                if (!emitter.isClosed()) {                                      //    未关闭
                    emitter.send("error", Map.of("message", "会话已被新的请求替换")); //    发送通知
                    emitter.complete();                                         //    关闭
                    log.info(">>> 已关闭 emitter: memoryId={}", memoryId);        //    日志
                }
            } catch (Exception e) {                                             //    异常
                log.warn("关闭 emitter 异常: memoryId={}", memoryId, e);          //    日志
            }
        } else {                                                                //    不存在
            log.info("stop() emitter 已不存在, memoryId={}", memoryId);          //    日志
        }
        runningRequestIds.remove(memoryId);                                     // 7. 清理 requestId

        String token = runningTokens.remove(memoryId);                          // 8. 取 token 并移除
        if (token != null) {                                                    //    存在
            String lockKey = "ai:chat:lock:" + memoryId;                        //    构造锁 key
            try {                                                               //    try
                redisLockHelper.unlock(lockKey, token);                         //    释放锁
                log.info("<<< 释放 Redis 锁: lockKey={}", lockKey);              //    日志
            } catch (Exception e) {                                             //    异常
                log.error("释放 Redis 锁失败: {}", lockKey, e);                   //    日志
            }
        }

        runningRunIds.remove(memoryId);                                         // 9. 清理 runId
    }
}
```

---

### `RedisMemoryStore.java`（核心方法逐行注释，节选）

```java
@Override                                                                       // 实现接口
public void save(MemoryItem item) {                                             // 保存记忆
    String key = key(item.userId(), item.id());                                 // 1. 构造主键
    byte[] keyBytes = b(key);                                                   //    转字节
    byte[] embeddingBytes = toFloatBytes(item.embedding());                     // 2. 向量转 float32 字节

    // 1. 主数据：原生 Lettuce 写 Hash                                         // 注释
    try {                                                                       // 3. try
        if (nativeConnection != null) {                                         //    单机连接
            RedisCommands<byte[], byte[]> cmd = nativeConnection.sync();        //    同步命令
            writeFields((k, v) -> cmd.hset(keyBytes, k, v), item, embeddingBytes);//    写字段
            cmd.expire(keyBytes, TTL_SECONDS);                                  //    设置 TTL
        } else if (nativeClusterConnection != null) {                           //    集群连接
            RedisAdvancedClusterCommands<byte[], byte[]> cmd = nativeClusterConnection.sync(); // 同步
            writeFields((k, v) -> cmd.hset(keyBytes, k, v), item, embeddingBytes);//    写字段
            cmd.expire(keyBytes, TTL_SECONDS);                                  //    设置 TTL
        } else {                                                                //    未初始化
            log.warn(">>> save: 原生连接未初始化");                              //    日志
            return;                                                             //    返回
        }
    } catch (Exception e) {                                                     // 4. 异常
        log.error(">>> 保存记忆主数据失败, key={}", key, e);                      //    日志
        return;                                                                 //    返回
    }

    // 2. 辅助索引：StringRedisTemplate，纯字符串                               // 注释
    try {                                                                       // 5. try
        String userSetKey = USER_SET_PREFIX + item.userId();                    //    用户集合 key
        String reverseKey = REVERSE_PREFIX + item.id();                         //    反向索引 key

        stringRedisTemplate.opsForSet().add(userSetKey, item.id());             //    加入用户集合
        stringRedisTemplate.expire(userSetKey, TTL_SECONDS, TimeUnit.SECONDS);  //    设置 TTL

        stringRedisTemplate.opsForValue().set(reverseKey, item.userId(), TTL_SECONDS, TimeUnit.SECONDS); // 写反向索引
    } catch (Exception e) {                                                     // 6. 异常
        log.error(">>> 保存记忆辅助索引失败, userId={}, id={}", item.userId(), item.id(), e); // 日志
    }
}
```

```java
@Override                                                                       // 实现接口
public List<MemoryItem> searchByUser(String userId, List<Double> queryEmbedding, int topK) { // KNN 检索
    if (queryEmbedding == null || queryEmbedding.isEmpty()) {                   // 1. 向量空
        log.warn(">>> 向量召回跳过：queryEmbedding 为空, userId={}", userId);     //    日志
        return List.of();                                                       //    返回空
    }
    if (queryEmbedding.size() != vectorDim) {                                   // 2. 维度不符
        log.warn(">>> 向量召回跳过：维度不符, 期望={}, 实际={}", vectorDim, queryEmbedding.size()); // 日志
        return List.of();                                                       //    返回空
    }

    byte[] qBytes = toFloatBytes(queryEmbedding);                               // 3. 向量转字节
    String query = String.format(                                               // 4. 构造查询
            "(@userId:{%s} @invalid:{0})=>[KNN %d @embedding $vec AS score]",   //    TAG 过滤 + KNN
            escapeTag(userId), topK                                             //    用户 ID 转义 + topK
    );

    try {                                                                       // 5. try
        List<byte[]> args = new ArrayList<>();                                  //    参数列表
        args.add(b("FT.SEARCH"));                                               //    命令
        args.add(b(INDEX_NAME));                                                //    索引名
        args.add(b(query));                                                     //    查询
        args.add(b("PARAMS"));   args.add(b("2"));                              //    PARAMS 2
        args.add(b("vec"));      args.add(qBytes);                              //    参数 vec
        args.add(b("SORTBY"));   args.add(b("score"));                          //    排序
        args.add(b("DIALECT"));  args.add(b("2"));                              //    DIALECT 2
        args.add(b("LIMIT"));    args.add(b("0")); args.add(b(String.valueOf(topK))); // LIMIT

        Object raw = dispatch("FT.SEARCH", args, OutputType.NESTED);            //    派发命令
        return parseSearchResult(raw);                                          //    解析结果
    } catch (Exception e) {                                                     // 6. 异常
        log.error(">>> 向量召回失败, userId={}", userId, e);                      //    日志
        return List.of();                                                       //    返回空
    }
}
```

---

### `KnowledgeRouter.java`（核心方法逐行注释，节选）

```java
@PostConstruct                                                                  // 初始化
public void init() {                                                            // 方法
    long interval = Math.max(1L, refreshMinutes);                               // 1. 取刷新间隔（至少 1 分钟）
    // 不在启动线程里做远程调用：预热失败也不影响应用启动，后续定时任务会继续自愈 // 注释
    refresher.scheduleWithFixedDelay(this::refreshQuietly, 0L, interval, TimeUnit.MINUTES); // 2. 定时刷新（首次立即）
    log.info(">>> KnowledgeRouter 已启动后台向量刷新，间隔 {} 分钟", interval);   // 3. 日志
}
```

```java
private boolean refresh() {                                                     // 刷新向量
    if (!refreshing.compareAndSet(false, true)) {                               // 1. CAS 互斥
        return false;                                                           //    已有线程在刷新
    }
    lastAttemptAt = System.currentTimeMillis();                                 // 2. 记录尝试时间
    try {                                                                       // 3. try
        List<Long> kbIds = getAllKnowledgeBaseIds();                            //    取所有知识库 ID
        int success = 0;                                                        //    成功计数
        for (Long kbId : kbIds) {                                               //    遍历
            try {                                                               //    try
                String desc = buildDescription(kbId);                           //    构建描述
                if (desc == null || desc.isBlank()) continue;                   //    跳过空
                if (desc.length() > MAX_DESC_LENGTH) desc = desc.substring(0, MAX_DESC_LENGTH); // 截断
                float[] vec = embeddingModel.embed(desc).content().vector();    //    向量化
                kbVectors.put(kbId, vec);                                       //    缓存向量
                kbDescriptions.put(kbId, desc);                                 //    缓存描述
                success++;                                                      //    计数
                log.debug(">>> 知识库 {} 描述向量已生成，维度：{}", kbId, vec.length); // 日志
            } catch (Exception e) {                                             //    单库失败
                log.warn(">>> 知识库 {} 描述向量生成失败，将在下次刷新时重试", kbId, e); // 日志
            }
        }
        log.info(">>> KnowledgeRouter 刷新完成，本次成功 {} / 共 {} 个知识库，当前缓存 {} 个", // 汇总日志
                success, kbIds.size(), kbVectors.size());
        return true;                                                            //    返回成功
    } finally {                                                                 // 4. finally
        refreshing.set(false);                                                  //    释放互斥
    }
}
```

```java
public Long route(String question) {                                            // 路由
    if (question == null || question.isBlank()) return null;                    // 1. 空返回
    ensureReady();                                                              // 2. 兜底重建
    if (kbVectors.isEmpty()) {                                                  // 3. 缓存空
        log.warn(">>> 知识库向量缓存为空，跳过路由，将由调用方走默认知识库");       //    日志
        return null;                                                            //    返回 null
    }
    float[] questionVec = embeddingModel.embed(question).content().vector();    // 4. 问题向量化

    Long bestKbId = null;                                                       // 5. 最佳 ID
    double bestScore = -1;                                                      //    最佳分数
    for (Map.Entry<Long, float[]> entry : kbVectors.entrySet()) {               // 6. 遍历
        double score = cosine(questionVec, entry.getValue());                   //    余弦相似度
        if (score > bestScore) {                                                //    更高
            bestScore = score;                                                  //    更新
            bestKbId = entry.getKey();                                          //    更新
        }
    }

    log.info(">>> 路由结果：knowledgeBaseId={}, score={}", bestKbId, bestScore);// 7. 日志
    if (bestScore < MIN_SCORE) {                                                // 8. 低于阈值
        log.warn(">>> 路由相似度低于阈值 {}，不选知识库", MIN_SCORE);             //    日志
        return null;                                                            //    返回 null
    }
    return bestKbId;                                                            // 9. 返回最佳
}
```

---

### `AiChatSseEmitter.java`（逐行注释）

```java
package com.lenovo.ai.infra.sse;                                                // 包声明

import lombok.extern.slf4j.Slf4j;                                               // 日志
import org.springframework.http.MediaType;                                      // 媒体类型
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;        // SSE 基类

import java.io.IOException;                                                     // IO 异常
import java.util.concurrent.atomic.AtomicBoolean;                               // 原子布尔

@Slf4j                                                                          // 日志
public class AiChatSseEmitter extends SseEmitter {                              // SSE 封装

    /** 连接是否已关闭 */                                                        // 注释
    private final AtomicBoolean closed = new AtomicBoolean(false);              // 关闭标志（原子）

    /** 关闭时的回调，由 runner 注册，用于立即停止 LLM 流 */                        // 注释
    private volatile Runnable onCloseCallback;                                  // 关闭回调

    /**                                                                         // Javadoc
     * 构造：5 分钟超时，注册完成/超时回调。                                       // 说明
     */                                                                         // 结束
    public AiChatSseEmitter() {                                                 // 构造器
        super(300_000L);   // 5 分钟                                            // 超时 5 分钟

        // SseEmitter 生命周期结束（客户端断开、超时、服务端 complete）都会触发   // 注释
        super.onCompletion(() -> {                                              // 完成回调
            closed.set(true);                                                   //    标记关闭
            Runnable cb = onCloseCallback;                                      //    取回调
            if (cb != null) {                                                   //    存在
                try { cb.run(); } catch (Exception ignored) {}                  //    执行（忽略异常）
            }
        });
        super.onTimeout(() -> {                                                 // 超时回调
            closed.set(true);                                                   //    标记关闭
            Runnable cb = onCloseCallback;                                      //    取回调
            if (cb != null) {                                                   //    存在
                try { cb.run(); } catch (Exception ignored) {}                  //    执行（忽略异常）
            }
        });
    }

    /**                                                                         // Javadoc
     * 是否已关闭。                                                               // 说明
     */                                                                         // 结束
    public boolean isClosed() {                                                 // 方法
        return closed.get();                                                    // 返回标志
    }

    /**                                                                         // Javadoc
     * 注册关闭回调。若注册时已关闭，立即执行。                                     // 说明
     */                                                                         // 结束
    public void setOnCloseCallback(Runnable callback) {                         // 方法
        this.onCloseCallback = callback;                                        // 赋值
        // 如果注册之前就已经关闭了，立即回调                                       // 注释
        if (closed.get() && callback != null) {                                 //    已关闭且回调非空
            try { callback.run(); } catch (Exception ignored) {}                //    立即执行
        }
    }

    /** 发送纯文本 delta（事件名为前端约定的 delta） */                              // 注释
    public void send(String chunk) {                                            // 发送文本
        if (chunk == null || chunk.isEmpty()) return;                           // 1. 空返回
        if (closed.get()) return;                                               // 2. 已关闭返回
        try {                                                                   // 3. try
            super.send(SseEmitter.event()                                       //    发送
                    .name("delta")                                              //    事件名 delta
                    .data(chunk, MediaType.TEXT_PLAIN));                        //    文本类型
        } catch (IOException e) {                                               // 4. IO 异常
            log.warn("SSE send failed (IOException): {}", e.getMessage());      //    日志
            closed.set(true);                                                   //    标记关闭
        } catch (IllegalStateException e) {                                     // 5. 状态异常
            log.warn("SSE already closed, ignore send.");                       //    日志
            closed.set(true);                                                   //    标记关闭
        }
    }

    /** 发送带事件名的结构化事件（payload 由 Spring 序列化为 JSON） */                // 注释
    public void send(String eventName, Object data) {                           // 发送事件
        if (closed.get()) return;                                               // 1. 已关闭返回
        try {                                                                   // 2. try
            super.send(SseEmitter.event()                                       //    发送
                    .name(eventName == null || eventName.isEmpty() ? "message" : eventName) // 事件名
                    .data(data, MediaType.APPLICATION_JSON));                   //    JSON 类型
        } catch (IOException e) {                                               // 3. IO 异常
            log.warn("SSE send failed (IOException): {}", e.getMessage());      //    日志
            closed.set(true);                                                   //    标记关闭
        } catch (IllegalStateException e) {                                     // 4. 状态异常
            log.warn("SSE already closed, ignore send.");                       //    日志
            closed.set(true);                                                   //    标记关闭
        }
    }

    /**                                                                         // Javadoc
     * 发送原始 SseEventBuilder。                                                 // 说明
     */                                                                         // 结束
    public void send(SseEmitter.SseEventBuilder event) {                        // 发送
        if (closed.get()) return;                                               // 已关闭返回
        try {                                                                   // try
            super.send(event);                                                  //    发送
        } catch (IOException e) {                                               // IO 异常
            log.warn("SSE send failed (IOException): {}", e.getMessage());      //    日志
            closed.set(true);                                                   //    标记关闭
        } catch (IllegalStateException e) {                                     // 状态异常
            log.warn("SSE already closed, ignore send.");                       //    日志
            closed.set(true);                                                   //    标记关闭
        }
    }

    @Override                                                                   // 重写
    public void complete() {                                                    // 关闭
        if (!closed.compareAndSet(false, true)) return;                         // 1. CAS 保证幂等
        try {                                                                   // 2. try
            super.complete();                                                   //    调用父类关闭
        } finally {                                                             // 3. finally
            if (onCloseCallback != null) {                                      //    有回调
                try { onCloseCallback.run(); } catch (Exception ignored) {}     //    执行
                onCloseCallback = null;                                         //    置空（避免重复执行）
            }
        }
    }
}
```

---

## 四、关键设计要点总结

| 设计点                | 实现方式                                     | 目的                                     |
| ------------------ | ---------------------------------------- | -------------------------------------- |
| **会话串行**           | Redis 分布式锁 + 内存状态清理                      | 同一会话同时只有一个请求在跑                         |
| **锁安全释放**          | Lua 脚本比对 token                           | 避免误删他人锁                                |
| **SSE 幂等关闭**       | `AtomicBoolean closed` CAS               | 防止重复 complete 异常                       |
| **工具超时**           | `CompletableFuture.orTimeout(30s)`       | 防止工具阻塞整个 Loop                          |
| **ThreadLocal 清理** | `finally { AiToolContext.clear(); }`     | 防止线程池污染                                |
| **记忆召回降级**         | embedding 失败 → 跳过保存                      | 单条失败不影响整批                              |
| **知识库路由自愈**        | 后台定时 + 请求链路兜底                            | 启动失败后能自动恢复 |
| **LLM 脏数据防御**      | `looksLikeId` 检测 requestId               | 防止网关错误污染缓存/记忆                          |
| **上下文预算**          | 超 6 轮做摘要 + 24000 字符裁剪                    | 控制 token 成本                            |
| **系统提示注入**         | `messages.add(0, system(...))`           | 强制 LLM 优先调用知识库工具                       |
| **挂起/确认**          | Redis 存 PendingState + `tool_confirm` 事件 | 敏感操作等用户确认                              |

---

## 五、待办与已知问题

| 项                                         | 位置                                      | 说明                           |
| ----------------------------------------- | --------------------------------------- | ---------------------------- |
| PG 持久化                                    | `InMemoryChatHistoryStore`              | TODO 待 PG 表就绪后替换             |
| 政策状态                                      | `InMemoryChatHistoryStore.policyStatus` | 固定返回 first=true, over30=true |
| 批量活跃用户                                    | `MemoryStore.getAllActiveUsers`         | Redis 实现已提供，接口 default 抛异常   |
| `AgentLoop.memoryStore`                   | 字段未使用                                   | 可移除或预留                       |
| `AiAgentService.stop` 回调                  | 与 `onCompletion` 可能重复清理                 | 需确认幂等性                       |
| `LangChain4jLlmClient.onCompleteResponse` | `fullContent` 被覆盖                       | 流式增量与完整内容可能不一致，需确认           |

---

**文档版本**：1.0  
**最后更新**：2026-10-10  
**适用代码范围**：`com.lenovo.ai.*` 全部模块
