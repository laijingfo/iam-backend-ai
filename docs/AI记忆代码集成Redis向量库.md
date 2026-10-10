# 企业智能助手 AI Agent 模块 — 完整详细功能文档

> 版本：2.0
> 更新日期：2026-10-10
> 适用范围：`com.lenovo.ai.*` 全模块
> 目标读者：后端开发、架构师、运维、测试

---

## 目录

1. [模块总览](#1-模块总览)
2. [系统架构](#2-系统架构)
3. [核心业务流程](#3-核心业务流程)
4. [数据结构与模型](#4-数据结构与模型)
5. [模块详细设计](#5-模块详细设计)
6. [关键机制深度剖析](#6-关键机制深度剖析)
7. [配置项说明](#7-配置项说明)
8. [SSE 事件协议](#8-sse-事件协议)
9. [异常处理与降级策略](#9-异常处理与降级策略)
10. [性能与并发设计](#10-性能与并发设计)
11. [安全与权限](#11-安全与权限)
12. [部署与运维](#12-部署与运维)
13. [扩展指南](#13-扩展指南)
14. [已知问题与改进建议](#14-已知问题与改进建议)
15. [附录：接口签名速查](#15-附录接口签名速查)

---

## 1. 模块总览

### 1.1 模块定位

本模块是**企业级 AI Agent 对话系统**，位于 `iam-backend-ai` 项目中，为前端提供智能问答、业务查询、工具调用等能力，支持流式输出、长期记忆、知识库检索等特性。

### 1.2 核心能力矩阵

| 能力 | 技术实现 | 用户价值 |
|------|----------|----------|
| **流式对话** | Spring MVC SseEmitter + LangChain4j StreamingChatModel | 逐字输出，降低首字延迟 |
| **工具调用** | Function Calling（OpenAI 兼容协议） | LLM 可查知识库、建工单、算日期 |
| **长期记忆** | Redis + RediSearch HNSW 向量索引 | 记住用户偏好/事实，跨会话 |
| **对话摘要** | LLM 压缩 + Redis 缓存 | 长对话上下文不超预算 |
| **知识库路由** | Embedding + 余弦相似度 | 自动选最相关知识库 |
| **权限控制** | 角色过滤 + 二次校验 | 防止越权工具调用 |
| **并发控制** | Redis 分布式锁 + 专用线程池 | 会话串行、全局限流 |
| **挂起/确认** | Redis 状态持久化 | 敏感操作等用户确认 |
| **连接保活** | 心跳 + 超时回调 | 客户端断开可感知 |
| **多语言** | LocaleContextHolder | 支持国际化 |

### 1.3 技术栈

| 层次 | 技术 | 版本/说明 |
|------|------|-----------|
| Web 框架 | Spring Boot | 3.x |
| SSE | Spring MVC SseEmitter | 原生支持 |
| LLM 框架 | LangChain4j | 1.x |
| LLM 模型 | OpenAI 兼容（qwen3-max） | 通过 base-url 配置 |
| Embedding | text-embedding-v3 | 1024 维（可配） |
| 向量存储 | Redis + RediSearch | HNSW 索引 |
| 缓存 | Redis | Lettuce 客户端 |
| 序列化 | Jackson / FastJSON2 | 混合使用 |
| TLS | Conscrypt | 提供 TLS 1.3 |
| 线程池 | JDK ThreadPoolExecutor | 自定义 |
| 日志 | SLF4J + Logback | Lombok @Slf4j |

---

## 2. 系统架构

### 2.1 分层架构图

```
┌───────────────────────────────────────────────────────────────────┐
│                         前端（SSE 客户端）                          │
└───────────────────────────────┬───────────────────────────────────┘
                                │ HTTP POST /ai/agent/chat
                                │ SSE: delta / tool / tool_confirm / done / error
┌───────────────────────────────▼───────────────────────────────────┐
│  Controller 层                                                     │
│  AiAgentController.ChatRequest（sessionId, message, clientRequestId）│
└───────────────────────────────┬───────────────────────────────────┘
                                │
┌───────────────────────────────▼───────────────────────────────────┐
│  Service 层    AiAgentService                                      │
│  ├─ 状态管理（runningEmitters/RunIds/Tokens/RequestIds）           │
│  ├─ 生命周期（清理旧状态、加锁、注册回调、提交线程池）              │
│  └─ 停止控制（stop by clientRequestId）                            │
└───────────────────────────────┬───────────────────────────────────┘
                                │ AiChatExecutor.submit
┌───────────────────────────────▼───────────────────────────────────┐
│  Agent 层    AiAgentRunner → AgentLoop                             │
│  ├─ ContextBuilder（历史 + 记忆 + 系统提示）                       │
│  ├─ ToolRegistry（工具注册 + 权限过滤）                            │
│  ├─ KnowledgeRouter（知识库向量路由）                              │
│  ├─ PendingStateStore（挂起状态 Redis）                            │
│  └─ 工具执行（超时 + 异常保护 + ThreadLocal 清理）                  │
└───────────────────────────────┬───────────────────────────────────┘
                                │
┌───────────────────────────────▼───────────────────────────────────┐
│  LLM 层    LlmClient → LangChain4jLlmClient                        │
│  ├─ chatStream（流式 + 工具调用）                                  │
│  └─ chatSimple（非流式，供摘要/记忆抽取）                          │
└───────────────────────────────┬───────────────────────────────────┘
                                │
┌───────────────────────────────▼───────────────────────────────────┐
│  记忆层                                                            │
│  ├─ MemoryStore（RedisMemoryStore：向量 KNN + 衰减 + 失效）         │
│  ├─ MemoryExtractor（异步抽取）                                    │
│  ├─ SummaryUtil（摘要 + 抽取 + 校验）                              │
│  └─ EmbeddingClient（LangChain4jEmbeddingClient）                  │
└───────────────────────────────┬───────────────────────────────────┘
                                │
┌───────────────────────────────▼───────────────────────────────────┐
│  基础设施层                                                        │
│  ├─ ChatHistoryStore（InMemoryChatHistoryStore）                   │
│  ├─ RedisLockHelper（Lua 原子解锁）                                │
│  ├─ AiChatSseEmitter（SSE 封装）                                   │
│  ├─ AiChatExecutor（AI 线程池）                                    │
│  └─ AsyncConfig（memoryExecutor）                                  │
└───────────────────────────────────────────────────────────────────┘
```

### 2.2 依赖关系图

```
AiAgentService
  ├── ChatHistoryStore
  ├── RedisLockHelper
  ├── RedisUtils
  ├── AiChatExecutor
  └── AiAgentRunner
        ├── ContextBuilder
        │     ├── ChatHistoryStore
        │     └── SummaryUtil
        │           ├── LlmClient
        │           ├── RedisUtils
        │           └── ObjectMapper
        ├── AgentLoop
        │     ├── LlmClient
        │     ├── ToolRegistry
        │     │     └── List<AiTool>
        │     ├── ChatHistoryStore
        │     └── PendingStateStore
        ├── MemoryStore（RedisMemoryStore）
        ├── MemoryExtractor
        │     ├── MemoryStore
        │     ├── EmbeddingClient
        │     └── SummaryUtil
        ├── EmbeddingClient
        ├── ChatHistoryStore
        └── RedisLockHelper

QueryKnowledgeTool
  ├── KnowledgeService
  ├── KnowledgeTemplateRegistry
  ├── KnowledgeRegistrar
  └── KnowledgeRouter
        ├── KnowledgeService
        └── EmbeddingModel（LangChain4j）
```

---

## 3. 核心业务流程

### 3.1 完整对话时序图

```
前端              Service            Runner           AgentLoop        LLM           工具/Redis
 │                  │                  │                  │              │              │
 │─ POST chat ─────>│                  │                  │              │              │
 │                  │─ 清理旧状态 ─────────────────────────────────────────────────────>│
 │                  │─ 注册 emitter/runId/token/requestId ──────────────────────────────│
 │                  │─ tryLock ────────────────────────────────────────────────────────>│
 │                  │<─ locked ─────────────────────────────────────────────────────────│
 │                  │─ 会话初始化（exists/assertOwner）──>│              │              │
 │                  │─ capture AiToolContext ──────────────────────────────────────────>│
 │                  │─ nextMessageId × 2 ──────────────>│              │              │
 │                  │─ onCompletion/onTimeout 注册 ────>│              │              │
 │                  │─ chatExecutor.submit ───────────>│              │              │
 │<─ SSE emitter ───│                  │                  │              │              │
 │                  │                  │─ appendUser ────────────────────────────────>│
 │                  │                  │─ embed(message) ────────────────────────────>│
 │                  │                  │<─ qVec ──────────────────────────────────────│
 │                  │                  │─ searchByUser ──────────────────────────────>│
 │                  │                  │<─ memories ──────────────────────────────────│
 │                  │                  │─ contextBuilder.build ──────────────────────>│
 │                  │                  │<─ messages ──────────────────────────────────│
 │                  │                  │─ inject system prompt ──>│              │
 │                  │                  │─ new AgentContext ──────>│              │
 │                  │                  │─ agentLoop.run ────────>│              │
 │                  │                  │                  │─ chatStream ─>│       │
 │<─ delta ────────────────────────────────────────────────────│              │
 │<─ delta ────────────────────────────────────────────────────│              │
 │                  │                  │                  │<─ LlmResponse│       │
 │                  │                  │                  │  (tool_calls)│       │
 │                  │                  │                  │─ executeTool ────────>│
 │<─ tool(executing)──────────────────────────────────────────│              │
 │                  │                  │                  │<─ ToolResult ────────│
 │<─ tool(completed)──────────────────────────────────────────│              │
 │                  │                  │                  │─ chatStream ─>│       │
 │<─ delta ────────────────────────────────────────────────────│              │
 │                  │                  │                  │<─ final ─────│       │
 │                  │                  │                  │─ appendAssistant ────>│
 │<─ done ────────────────────────────────────────────────────│              │
 │                  │                  │<─ loop exit ─────│              │
 │                  │                  │─ memoryExtractor.extract（异步）──>│
 │                  │<─ run 结束 ──────│                  │              │
 │                  │─ unlock ────────────────────────────────────────────────────────>│
 │<─ SSE complete ──│                  │                  │              │              │
```

### 3.2 需确认工具流程

```
第 N 步：LLM 返回 tool_calls = [create_ticket]
   │
   ├─ requiresConfirmation() == true
   ├─ ctx.confirmed(call.id()) == false
   │
   ▼
replaceMessages(ctx.messages(), messages)  // 回写本地消息列表
pendingStateStore.save(ctx)                // 存 Redis，TTL 10 分钟
sendEvent("tool_confirm", {callId, tool, args})
emitter.complete()                          // 关闭 SSE，等待用户确认
   │
   ▼
用户确认后（外部接口）：
   ├─ pendingStateStore.load(runId)
   ├─ ctx.confirmedCalls.add(callId) 或 markConfirmed(callId)
   ├─ agentLoop.run(ctx)  // 重新进入循环
   └─ 此时 requiresConfirmation && !confirmed → false，正常执行
```

### 3.3 知识库路由流程

```
QueryKnowledgeTool.execute(args)
   │
   ├─ knowledgeBaseId 显式传入？
   │   ├─ 是 → parseKbId → isValidKnowledgeId（13位纯数字 + 已注册）
   │   │       ├─ 有效 → 直接用
   │   │       └─ 无效 → 回落自动路由
   │   └─ 否 → 自动路由
   │
   ▼
KnowledgeRouter.route(question)
   ├─ ensureReady()：缓存空则兜底重建
   ├─ embed(question) → questionVec
   ├─ 遍历 kbVectors，计算余弦相似度
   ├─ bestScore < MIN_SCORE(0.5) → 返回 null
   └─ 返回 bestKbId
   │
   ▼
queryOne(kbId, question)
   ├─ templateRegistry.get(knowledgeId)
   ├─ 不存在 → knowledgeRegistrar.register(knowledgeId)
   ├─ copyTemplate（避免污染模板）
   ├─ kb.setQuery(question)
   ├─ knowledgeService.queryAnswer(kb)
   └─ 拼接结果（最多 5 条，每条 800 字）
```

---

## 4. 数据结构与模型

### 4.1 核心数据模型

#### 4.1.1 Message（统一消息模型）

```java
public record Message(
    MessageRole role,              // SYSTEM / USER / ASSISTANT / TOOL
    String content,                // 文本内容
    String toolCallId,             // TOOL 角色时：对应的 tool_call id
    List<ToolCallRef> toolCalls    // ASSISTANT 角色时：工具调用列表
) {
    record ToolCallRef(String id, String name, Map<String, Object> args) {}
}
```

**工厂方法**：
| 方法 | role | 用途 |
|------|------|------|
| `system(c)` | SYSTEM | 系统提示 |
| `user(c)` | USER | 用户输入 |
| `assistant(c)` | ASSISTANT | 助手文本回答 |
| `assistantWithTools(c, calls)` | ASSISTANT | 助手工具调用 |
| `toolResult(callId, c)` | TOOL | 工具执行结果 |

#### 4.1.2 LlmResponse

```java
public record LlmResponse(
    String content,           // 文本内容（可能为空）
    List<ToolCall> toolCalls  // 工具调用列表
) {
    record ToolCall(String id, String name, Map<String, Object> args) {}
    boolean hasToolCalls() { return toolCalls != null && !toolCalls.isEmpty(); }
}
```

#### 4.1.3 ToolResult

```java
public record ToolResult(
    boolean success,                // 是否成功
    String content,                 // 结果内容（给 LLM 看）
    Map<String, Object> meta        // 元数据（如 knowledgeBaseId）
) {
    static ToolResult ok(String content);
    static ToolResult ok(String content, Map<String, Object> meta);
    static ToolResult fail(String msg);
}
```

#### 4.1.4 ToolSpec

```java
public record ToolSpec(
    String name,                          // 工具名（LLM 调用标识）
    String description,                   // 描述（LLM 判断何时调用）
    Map<String, Object> parameterSchema   // JSON Schema
) {}
```

#### 4.1.5 MemoryItem

```java
public record MemoryItem(
    String id,                  // UUID
    String userId,              // 所属用户
    String type,                // PREFERENCE / FACT / ENTITY
    String content,             // 记忆内容
    List<Double> embedding,     // 向量
    double importance,          // 重要性 [0, 1]
    long createdAt,             // 创建时间戳
    long updatedAt,             // 更新时间戳
    boolean invalid             // 是否失效
) {
    static MemoryItem of(userId, type, content, embedding, importance);
}
```

#### 4.1.6 PendingState

```java
public record PendingState(
    String runId,                  // 运行 ID
    String memoryId,               // userId:sessionId
    long assistantMessageId,       // 助手消息 ID
    String sessionId,              // 会话 ID
    List<Message> messages,        // 消息列表
    List<String> confirmedCalls,   // 已确认的工具调用 ID
    String userId                  // 用户 ID
) {}
```

### 4.2 Redis Key 设计

| Key 模式 | 类型 | TTL | 用途 |
|----------|------|-----|------|
| `ai:chat:lock:{userId}:{sessionId}` | String | 5s | 会话分布式锁 |
| `ai:pending:{runId}` | String(JSON) | 10min | 挂起状态 |
| `ai:memory:{userId}:{memoryId}` | Hash | 90d | 记忆主数据 |
| `ai:memory:user:{userId}` | Set | 90d | 用户记忆 ID 集合 |
| `ai:memory:reverse:{memoryId}` | String | 90d | 反向索引（memoryId → userId） |
| `ai:summary:{hash}` | String | 1h | 摘要缓存 |
| `idx:ai:memory` | RediSearch Index | - | 向量索引 |

### 4.3 RediSearch 索引 Schema

```
FT.CREATE idx:ai:memory
  ON HASH
  PREFIX 1 ai:memory:
  SCHEMA
    userId     TAG
    type       TAG
    content    TEXT
    importance NUMERIC
    createdAt  NUMERIC
    updatedAt  NUMERIC
    invalid    TAG
    embedding  VECTOR HNSW 6
                 TYPE FLOAT32
                 DIM {vectorDim}
                 DISTANCE_METRIC COSINE
```

**KNN 查询语句**：
```
(@userId:{userId} @invalid:{0})=>[KNN {topK} @embedding $vec AS score]
  PARAMS 2 vec {float32 bytes}
  SORTBY score
  DIALECT 2
  LIMIT 0 {topK}
```

---

## 5. 模块详细设计

### 5.1 AiAgentService（对话服务入口）

**职责**：Controller 与 Agent 之间的协调层，管理会话生命周期。

**状态字段**：
```java
Map<String, AiChatSseEmitter> runningEmitters;    // memoryId → SSE
Map<String, String> runningRunIds;                // memoryId → runId
Map<String, String> runningTokens;                // memoryId → 锁 token
Map<String, String> runningRequestIds;            // memoryId → clientRequestId
```

**chat() 方法流程**：
1. **清理旧状态**（顺序敏感！）
    - 先取 `oldToken` 并 `forceUnlock`（避免 `onCompletion` 提前清理 token）
    - 再关闭旧 `emitter`
2. **注册新状态**（emitter / token / runId / requestId）
3. **加锁**：`tryLock(lockKey, token, 5000L)`
    - 失败：清理状态、返回错误 SSE
4. **会话初始化**：
    - `newSession = !history.exists(sessionId)`
    - 非新会话：`history.assertOwner(sessionId, userId)`
    - `AiToolContext.capture(userId, redisUtils)`：从 Redis 读角色
    - 分配 `userMessageId` / `assistantMessageId`
5. **注册生命周期回调**：
    - `onCompletion`：清理四个 Map
    - `onTimeout`：关闭 emitter、清理 requestId、释放锁
6. **提交线程池**：`chatExecutor.execute(() -> runner.run(...))`

**stop() 方法流程**：
1. 校验参数
2. 比对 `clientRequestId`（不匹配则跳过，防止误停新请求）
3. 关闭 emitter（发送替换通知 + complete）
4. 释放 Redis 锁
5. 清理状态

**关键设计点**：
- **清理顺序**：`forceUnlock` 必须在 `emitter.complete()` 之前，否则 `onCompletion` 回调会提前清理 token，导致无法释放锁
- **requestId 匹配**：防止旧请求的 stop 误停新请求
- **回调幂等**：`AiChatSseEmitter.complete()` 内部 CAS 保证幂等

### 5.2 AiAgentRunner（运行编排）

**职责**：串联「落库 → 记忆召回 → 上下文构建 → 系统提示注入 → AgentLoop → 记忆抽取」全流程。

**run() 方法流程**：
```java
try {
    // 1. 落库 user 消息
    history.appendUser(userMessageId, sessionId, userId, request.message());
    
    // 2. 长期记忆召回
    List<Double> qVec = embeddingClient.embed(request.message());
    List<MemoryItem> memories = memoryStore.searchByUser(userId, qVec, 5);
    String memoryBlock = memories.stream()
        .map(m -> "- " + m.content())
        .collect(Collectors.joining("\n"));
    
    // 3. 构建上下文（历史 + 记忆）
    List<Message> messages = contextBuilder.build(sessionId, request.message(), memoryBlock);
    
    // 4. 注入系统提示词（最前面）
    messages.add(0, Message.system("""
        你是一个企业智能助手，必须严格遵守以下规则：
        1. 优先使用工具：业务数据、制度、流程、待办、审批等问题必须调用 query_knowledge
        2. 基于检索结果作答：工具返回结果是唯一可信来源
        3. 知识库 ID 规则：除非用户明确给出 13 位以上纯数字，否则不传 knowledgeBaseId
        4. 答案格式：先直接回答，再引用具体内容
        5. 审核/审批相关问题：必须调用知识库工具
        如果用户问题与业务无关，可以直接回答。
        """));
    
    // 5. 构造上下文并运行
    AgentContext ctx = new AgentContext(messages, toolContext, emitter, sessionId,
        assistantMessageId, runId, memoryId);
    agentLoop.run(ctx);
    
    // 6. 异步抽取长期记忆
    List<Message> turn = List.of(
        Message.user(request.message()),
        Message.assistant(history.loadAssistant(assistantMessageId))
    );
    memoryExtractor.extract(userId, turn);
    
} catch (Exception e) {
    log.error("AiAgentRunner 异常，runId={}", runId, e);
} finally {
    // 只释放自己持有的锁（Lua 比对 token）
    redisLockHelper.unlock(lockKey, token);
}
```

**关键设计点**：
- **系统提示注入**：在消息列表最前面插入，确保 LLM 优先看到规则
- **记忆召回失败降级**：`embeddingClient.embed` 抛异常时会被 catch 捕获，不影响主流程（但会丢失记忆召回）
- **锁释放保证**：`finally` 中释放，即使异常也能释放
- **记忆抽取异步**：不阻塞主流程

### 5.3 AgentLoop（Agent 主循环）

**职责**：ReAct 风格循环，最多 8 步，每步调用 LLM，若有工具调用则执行并回填。

**run() 方法流程**：
```java
try {
    List<Message> messages = new ArrayList<>(ctx.messages());
    List<ToolSpec> tools = toolRegistry.availableTools(ctx.toolContext());
    
    for (int step = 0; step < MAX_STEPS; step++) {
        // 1. SSE 关闭检测
        if (ctx.emitter().isClosed()) return;
        
        // 2. 调用 LLM（流式）
        LlmResponse resp = llmClient.chatStream(messages, tools, ctx.emitter());
        
        // 3. 无工具调用 → 最终答案
        if (!resp.hasToolCalls()) {
            history.appendAssistant(ctx.assistantMessageId(), ctx.sessionId(), resp.content());
            sendEvent(ctx.emitter(), "done", Map.of("messageId", ctx.assistantMessageId()));
            ctx.emitter().complete();
            return;
        }
        
        // 4. 有工具调用：转换格式并加入消息列表
        List<Message.ToolCallRef> refs = resp.toolCalls().stream()
            .map(tc -> new Message.ToolCallRef(tc.id(), tc.name(), tc.args()))
            .toList();
        messages.add(Message.assistantWithTools(resp.content(), refs));
        
        // 5. 逐个执行工具
        for (LlmResponse.ToolCall call : resp.toolCalls()) {
            AiTool tool = toolRegistry.get(call.name());
            
            // 5.1 工具不存在
            if (tool == null) {
                messages.add(Message.toolResult(call.id(), "工具不存在: " + call.name()));
                continue;
            }
            
            // 5.2 权限二次校验
            if (!toolRegistry.isAllowed(call.name(), ctx.toolContext())) {
                messages.add(Message.toolResult(call.id(), "无权限调用该工具"));
                sendEvent(ctx.emitter(), "tool", Map.of("name", call.name(), "status", "denied"));
                continue;
            }
            
            // 5.3 需确认工具
            if (tool.requiresConfirmation() && !ctx.confirmed(call.id())) {
                replaceMessages(ctx.messages(), messages);
                pendingStateStore.save(ctx);
                sendEvent(ctx.emitter(), "tool_confirm", Map.of(
                    "callId", call.id(), "tool", call.name(), "args", call.args()));
                ctx.emitter().complete();
                return;
            }
            
            // 5.4 执行工具
            sendEvent(ctx.emitter(), "tool", Map.of(
                "name", call.name(), "args", call.args(), "status", "executing"));
            ToolResult result = executeWithGuard(tool, call, ctx);
            sendEvent(ctx.emitter(), "tool", Map.of(
                "name", call.name(), "args", call.args(),
                "status", result.success() ? "completed" : "failed",
                "result", result.content()));
            messages.add(Message.toolResult(call.id(), result.content()));
        }
    }
    
    // 6. 达到最大步数
    history.appendAssistant(ctx.assistantMessageId(), ctx.sessionId(), "[已达到最大工具调用步数]");
    sendEvent(ctx.emitter(), "done", Map.of("messageId", ctx.assistantMessageId()));
    ctx.emitter().complete();
    
} catch (Exception e) {
    log.error("AgentLoop 异常, runId={}", ctx.runId(), e);
    try {
        sendEvent(ctx.emitter(), "error", Map.of(
            "code", "INTERNAL_ERROR", "message", "处理失败: " + e.getMessage()));
    } catch (Exception ignored) {}
    try { ctx.emitter().complete(); } catch (Exception ignored) {}
}
```

**executeWithGuard() 工具执行保护**：
```java
private ToolResult executeWithGuard(AiTool tool, LlmResponse.ToolCall call, AgentContext ctx) {
    try {
        return CompletableFuture.supplyAsync(() -> {
            AiToolContext.set(ctx.toolContext());  // 设置 ThreadLocal
            try {
                return tool.execute(call.args(), ctx.toolContext());
            } finally {
                AiToolContext.clear();  // 必须清理，防止线程池污染
            }
        })
        .orTimeout(TOOL_TIMEOUT_SECONDS, TimeUnit.SECONDS)  // 30s 超时
        .exceptionally(e -> {
            log.error("工具执行异常: {}", tool.name(), e);
            return ToolResult.fail("工具执行失败: " + e.getMessage());
        })
        .join();
    } catch (Exception e) {
        log.error("工具提交失败: {}", tool.name(), e);
        return ToolResult.fail("工具执行失败: " + e.getMessage());
    }
}
```

**关键设计点**：
- **MAX_STEPS = 8**：防止无限循环
- **TOOL_TIMEOUT = 30s**：防止工具阻塞
- **SSE 关闭检测**：每步开始检查，及时退出
- **ThreadLocal 清理**：`finally` 保证
- **需确认工具**：挂起状态存 Redis，发 `tool_confirm` 事件后退出

### 5.4 LangChain4jLlmClient（LLM 客户端）

**职责**：封装 LangChain4j，提供流式和非流式调用。

**chatStream() 流程**：
```java
public LlmResponse chatStream(List<Message> messages, List<ToolSpec> tools, AiChatSseEmitter emitter) {
    // 1. 格式转换
    List<ChatMessage> chatMessages = toChatMessages(messages);
    List<ToolSpecification> toolSpecs = toToolSpecifications(tools);
    
    // 2. 构建请求
    ChatRequest.Builder requestBuilder = ChatRequest.builder().messages(chatMessages);
    if (!toolSpecs.isEmpty()) requestBuilder.toolSpecifications(toolSpecs);
    ChatRequest request = requestBuilder.build();
    
    // 3. 异步结果容器
    CompletableFuture<LlmResponse> future = new CompletableFuture<>();
    StringBuilder fullContent = new StringBuilder();
    List<ToolExecutionRequest> toolRequests = new ArrayList<>();
    
    // 4. 流式调用
    streamingModel.chat(request, new StreamingChatResponseHandler() {
        @Override
        public void onPartialResponse(String partialResponse) {
            if (partialResponse != null && !partialResponse.isEmpty()) {
                fullContent.append(partialResponse);
                emitter.send("delta", Map.of("text", partialResponse));
            }
        }
        
        @Override
        public void onCompleteResponse(ChatResponse completeResponse) {
            try {
                AiMessage aiMessage = completeResponse.aiMessage();
                String content = aiMessage.text();
                if (content != null && !content.isEmpty()) {
                    fullContent.setLength(0);
                    fullContent.append(content);
                    emitter.send("done", Map.of("answer", content));
                }
                if (aiMessage.hasToolExecutionRequests()) {
                    toolRequests.addAll(aiMessage.toolExecutionRequests());
                    emitter.send("tool", Map.of("name", "tool_execution"));
                }
                future.complete(toLlmResponse(fullContent.toString(), toolRequests));
            } catch (Exception e) {
                future.completeExceptionally(e);
            }
        }
        
        @Override
        public void onError(Throwable error) {
            log.error("StreamingChatResponseHandler onError", error);
            emitter.send("error", Map.of("message", error.getMessage()));
            future.completeExceptionally(error);
        }
    });
    
    // 5. 等待结果（120s 超时）
    try {
        return future.get(STREAM_TIMEOUT_SECONDS, TimeUnit.SECONDS);
    } catch (Exception e) {
        log.error("chatStream 超时或异常", e);
        return new LlmResponse("", List.of());
    }
}
```

**toChatMessages() 转换规则**：
| 内部 role | 条件 | 转换为 L4j |
|-----------|------|-----------|
| SYSTEM | - | `SystemMessage` |
| USER | - | `UserMessage` |
| ASSISTANT | 有 toolCalls | `AiMessage.from(List<ToolExecutionRequest>)` |
| ASSISTANT | 无 toolCalls | `AiMessage.from(content)` |
| TOOL | - | `ToolExecutionResultMessage.from(callId, null, content)` |

**toToolSpecifications() 转换规则**：
- 将内部 `parameterSchema`（JSON Schema）转为 L4j `JsonObjectSchema`
- 支持类型：`string` / `integer` / `number` / `boolean`
- 支持 `required` 字段

**关键设计点**：
- **流式回调**：`onPartialResponse` 推送 delta 事件
- **完整内容覆盖**：`onCompleteResponse` 中 `fullContent.setLength(0)` 用完整内容覆盖（防止增量与完整不一致）
- **工具请求收集**：`aiMessage.toolExecutionRequests()`
- **超时兜底**：120s 后返回空响应

### 5.5 ContextBuilder（上下文构建）

**职责**：加载历史 + 注入记忆 + 摘要压缩 + 字符预算裁剪。

**build() 流程**：
```java
public List<Message> build(String sessionId, String newUserMsg, String longTermMemoryBlock) {
    // 1. 加载历史
    List<Message> stored = history.load(sessionId);
    List<Message> systemMsgs = stored.stream().filter(Message::isSystem).toList();
    List<Message> dialog = stored.stream().filter(m -> !m.isSystem()).toList();
    
    List<Message> result = new ArrayList<>(systemMsgs);
    
    // 2. 注入长期记忆
    if (longTermMemoryBlock != null && !longTermMemoryBlock.isBlank()) {
        result.add(Message.system("[关于用户的已知信息]\n" + longTermMemoryBlock));
    }
    
    // 3. 摘要压缩
    if (dialog.size() <= KEEP_RECENT_TURNS * 2) {  // 6 轮 = 12 条
        result.addAll(dialog);
    } else {
        int splitIdx = dialog.size() - KEEP_RECENT_TURNS * 2;
        List<Message> old = dialog.subList(0, splitIdx);
        List<Message> recent = dialog.subList(splitIdx, dialog.size());
        result.add(Message.system("[历史对话摘要]\n" + summaryUtil.summarize(old)));
        result.addAll(recent);
    }
    
    // 4. 追加本轮 user 消息（可选）
    if (newUserMsg != null && !newUserMsg.isBlank()) {
        result.add(Message.user(newUserMsg));
    }
    
    return trimToBudget(result);
}
```

**trimToBudget() 字符预算裁剪**：
```java
private List<Message> trimToBudget(List<Message> messages) {
    int total = messages.stream().mapToInt(m -> len(m.content())).sum();
    if (total <= MAX_CONTEXT_CHARS) return messages;  // 24000 字符
    
    List<Message> result = new ArrayList<>(messages);
    int i = 0;
    while (total > MAX_CONTEXT_CHARS && i < result.size()) {
        Message m = result.get(i);
        if (m.isSystem()) { i++; continue; }  // 跳过 system 消息
        total -= len(m.content());
        result.remove(i);
    }
    return result;
}
```

**关键设计点**：
- **KEEP_RECENT_TURNS = 6**：保留最近 6 轮对话
- **MAX_CONTEXT_CHARS = 24000**：总字符预算
- **system 消息不裁剪**：保证系统提示和记忆块始终存在
- **摘要缓存**：`SummaryUtil.summarize` 内部有 Redis 缓存

### 5.6 SummaryUtil（摘要与记忆抽取）

**职责**：LLM 摘要生成 + 长期记忆抽取 + 脏数据防御。

**summarize() 流程**：
```java
public String summarize(List<Message> messages, int maxWordSize) {
    // 1. 拼接对话文本
    String raw = messages.stream()
        .map(m -> m.role() + ": " + m.content())
        .collect(Collectors.joining("\n"));
    
    // 2. 缓存 key（内容 hash）
    String cacheKey = CACHE_PREFIX + Integer.toHexString(raw.hashCode());
    
    // 3. 读缓存
    try {
        Object cached = redisUtils.get(cacheKey);
        if (cached != null) {
            String cachedStr = String.valueOf(cached);
            if (!cachedStr.isBlank() && !looksLikeId(cachedStr)) {
                return cachedStr;
            }
            log.warn(">>> 缓存内容可疑，忽略：cacheKey={}, value={}", cacheKey, preview(cachedStr));
        }
    } catch (Exception e) {
        log.warn(">>> 读取 Redis 缓存失败", e);
    }
    
    // 4. 调用 LLM 摘要
    String prompt = """
        请把以下对话压缩成简洁摘要，保留：
        1. 用户的关键诉求和已确认的事实
        2. 已经执行过的操作及其结果
        3. 尚未解决的问题
        忽略寒暄和重复内容。控制在 %d 字以内，直接输出摘要内容。
        
        对话：
        %s
        """.formatted(maxWordSize, raw);
    
    String summary = stripCodeFence(llmClient.chatSimple(prompt));
    
    // 5. 防御：空或疑似 ID
    if (summary == null || summary.isBlank()) return "";
    if (looksLikeId(summary)) {
        log.warn(">>> LLM 摘要疑似返回了 ID，放弃：{}", preview(summary));
        return "";
    }
    
    // 6. 写缓存
    try {
        redisUtils.set(cacheKey, summary, Duration.ofHours(1).toSeconds());
    } catch (Exception e) {
        log.warn(">>> 保存摘要到 Redis 缓存失败", e);
    }
    
    return summary;
}
```

**extractLongTermMemoriesAsList() 流程**：
```java
public List<Map<String, Object>> extractLongTermMemoriesAsList(String dialog) {
    if (dialog == null || dialog.isBlank()) return Collections.emptyList();
    
    // 1. 构造 prompt
    String prompt = """
        从以下对话中抽取值得长期记住的用户信息。
        只输出 JSON 数组，不要任何解释。格式：
        [{"type":"PREFERENCE|FACT|ENTITY","content":"...","importance":0.0~1.0}]
        没有值得记住的信息则输出 []。
        
        判断标准：
        - PREFERENCE: 用户偏好（语言、风格、习惯）
        - FACT: 关于用户的稳定事实（部门、职位、常用系统）
        - ENTITY: 用户关注的具体对象（项目名、系统名）
        临时性问题不要抽取。
        
        对话：
        %s
        """.formatted(dialog);
    
    // 2. 调用 LLM
    String raw;
    try {
        raw = llmClient.chatSimple(prompt);
    } catch (Exception e) {
        log.warn(">>> LLM 抽取记忆调用失败：{}", e.getMessage(), e);
        return Collections.emptyList();
    }
    
    // 3. 清洗
    String cleaned = stripCodeFence(raw);
    if (cleaned == null || cleaned.isBlank()) return Collections.emptyList();
    if (!cleaned.startsWith("[")) {  // 必须是数组
        log.warn(">>> 抽取记忆返回非 JSON 数组，丢弃。raw={}", preview(raw));
        return Collections.emptyList();
    }
    
    // 4. 解析
    List<Map<String, Object>> items;
    try {
        items = objectMapper.readValue(cleaned, new TypeReference<List<Map<String, Object>>>() {});
    } catch (Exception e) {
        log.warn(">>> 抽取记忆 JSON 解析失败：{} | raw={}", e.getMessage(), preview(raw));
        return Collections.emptyList();
    }
    
    // 5. 逐条校验规范化
    return items.stream()
        .filter(this::isValidItem)
        .peek(this::normalizeItem)
        .collect(Collectors.toList());
}
```

**stripCodeFence() 清洗规则**：
1. 去 ` ```json ... ``` ` 或 ` ``` ... ``` `
2. 截取第一个 `[` 到最后一个 `]` 之间的内容

**looksLikeId() 脏数据防御**：
```java
private boolean looksLikeId(String s) {
    if (s == null) return false;
    String t = s.trim();
    if (t.isEmpty() || t.length() > 64) return false;
    // 纯 hex（允许连字符），长度 8~36
    return t.matches("(?i)^[0-9a-f]{8}(-[0-9a-f]{4}){0,4}([0-9a-f]{0,12})?$")
            && t.replace("-", "").length() >= 8;
}
```

**关键设计点**：
- **缓存防御**：缓存内容疑似 ID 时忽略
- **LLM 输出防御**：非数组、解析失败、空内容均归零为 `[]`
- **字段校验**：type 必须在 `PREFERENCE/FACT/ENTITY` 中，content 非空
- **importance 规范化**：非 Number 或越界时归 0.5 / 0~1

### 5.7 MemoryExtractor（记忆抽取）

**职责**：异步从一轮对话抽取长期记忆。

**extract() 流程**：
```java
@Async("memoryExecutor")
public void extract(String userId, List<Message> turn) {
    try {
        if (turn == null || turn.isEmpty()) return;
        
        // 1. 拼接对话
        String dialog = turn.stream()
            .map(m -> m.role() + ": " + m.content())
            .collect(Collectors.joining("\n"));
        
        // 2. LLM 抽取（带校验）
        List<Map<String, Object>> items = summaryUtil.extractLongTermMemoriesAsList(dialog);
        if (items == null || items.isEmpty()) {
            log.debug("[MemoryExtractor] 未检测到值得记住的信息");
            return;
        }
        
        // 3. 逐条保存
        for (Map<String, Object> it : items) {
            saveOne(userId, it);
        }
    } catch (Exception e) {
        log.warn("长期记忆抽取失败：{}", e.getMessage(), e);
    }
}
```

**saveOne() 流程**：
```java
private void saveOne(String userId, Map<String, Object> it) {
    try {
        // 1. 字段校验
        Object typeObj = it.get("type");
        Object contentObj = it.get("content");
        if (typeObj == null || contentObj == null) return;
        String type = String.valueOf(typeObj);
        String content = String.valueOf(contentObj);
        if (content.isBlank()) return;
        
        // 2. importance 解析
        double importance = 0.5;
        Object imp = it.get("importance");
        if (imp instanceof Number) importance = ((Number) imp).doubleValue();
        
        // 3. 去重（相同内容旧记忆失效）
        List<MemoryItem> existing = memoryStore.findByUserAndContentLike(userId, content);
        if (existing != null && !existing.isEmpty()) {
            memoryStore.invalidate(existing.get(0).id());
        }
        
        // 4. 向量化（失败则跳过）
        List<Double> embedding;
        try {
            embedding = embeddingClient.embed(content);
        } catch (Exception e) {
            log.warn(">>> 记忆 embedding 失败，跳过保存：content={}, err={}", content, e.toString());
            return;
        }
        if (embedding == null || embedding.isEmpty()) return;
        
        // 5. 保存
        memoryStore.save(MemoryItem.of(userId, type, content, embedding, importance));
        log.info(">>> 成功保存记忆：type={}, content={}", type, content.substring(0, Math.min(50, content.length())));
    } catch (Exception e) {
        log.warn(">>> 保存单条记忆失败：item={}, err={}", it, e.toString());
    }
}
```

**关键设计点**：
- **异步执行**：`@Async("memoryExecutor")`，不阻塞主流程
- **单条失败隔离**：`saveOne` 独立 try-catch
- **embedding 降级**：失败则跳过该条
- **去重**：相同内容旧记忆失效

### 5.8 RedisMemoryStore（记忆存储）

**职责**：基于 Redis + RediSearch 的向量记忆存储。

**核心方法**：

**save()**：
```java
public void save(MemoryItem item) {
    String key = key(item.userId(), item.id());  // ai:memory:{userId}:{memoryId}
    byte[] keyBytes = b(key);
    byte[] embeddingBytes = toFloatBytes(item.embedding());
    
    // 1. 主数据：原生 Lettuce 写 Hash
    try {
        if (nativeConnection != null) {
            RedisCommands<byte[], byte[]> cmd = nativeConnection.sync();
            writeFields((k, v) -> cmd.hset(keyBytes, k, v), item, embeddingBytes);
            cmd.expire(keyBytes, TTL_SECONDS);  // 90 天
        } else if (nativeClusterConnection != null) {
            // 集群模式
        } else {
            log.warn(">>> save: 原生连接未初始化");
            return;
        }
    } catch (Exception e) {
        log.error(">>> 保存记忆主数据失败, key={}", key, e);
        return;
    }
    
    // 2. 辅助索引：StringRedisTemplate
    try {
        String userSetKey = USER_SET_PREFIX + item.userId();  // ai:memory:user:{userId}
        String reverseKey = REVERSE_PREFIX + item.id();       // ai:memory:reverse:{memoryId}
        
        stringRedisTemplate.opsForSet().add(userSetKey, item.id());
        stringRedisTemplate.expire(userSetKey, TTL_SECONDS, TimeUnit.SECONDS);
        stringRedisTemplate.opsForValue().set(reverseKey, item.userId(), TTL_SECONDS, TimeUnit.SECONDS);
    } catch (Exception e) {
        log.error(">>> 保存记忆辅助索引失败, userId={}, id={}", item.userId(), item.id(), e);
    }
}
```

**searchByUser()**：
```java
public List<MemoryItem> searchByUser(String userId, List<Double> queryEmbedding, int topK) {
    if (queryEmbedding == null || queryEmbedding.isEmpty()) return List.of();
    if (queryEmbedding.size() != vectorDim) {
        log.warn(">>> 向量召回跳过：维度不符, 期望={}, 实际={}", vectorDim, queryEmbedding.size());
        return List.of();
    }
    
    byte[] qBytes = toFloatBytes(queryEmbedding);
    String query = String.format(
        "(@userId:{%s} @invalid:{0})=>[KNN %d @embedding $vec AS score]",
        escapeTag(userId), topK
    );
    
    try {
        List<byte[]> args = new ArrayList<>();
        args.add(b("FT.SEARCH"));
        args.add(b(INDEX_NAME));
        args.add(b(query));
        args.add(b("PARAMS"));   args.add(b("2"));
        args.add(b("vec"));      args.add(qBytes);
        args.add(b("SORTBY"));   args.add(b("score"));
        args.add(b("DIALECT"));  args.add(b("2"));
        args.add(b("LIMIT"));    args.add(b("0")); args.add(b(String.valueOf(topK)));
        
        Object raw = dispatch("FT.SEARCH", args, OutputType.NESTED);
        return parseSearchResult(raw);
    } catch (Exception e) {
        log.error(">>> 向量召回失败, userId={}", userId, e);
        return List.of();
    }
}
```

**getAllActiveUsers()**：
```java
public Set<String> getAllActiveUsers() {
    try {
        Set<String> activeUsers = new HashSet<>();
        String pattern = USER_SET_PREFIX + "*";  // ai:memory:user:*
        
        ScanOptions scanOptions = ScanOptions.scanOptions()
            .match(pattern).count(100).build();
        
        stringRedisTemplate.execute((RedisCallback<Void>) connection -> {
            Cursor<byte[]> cursor = connection.keyCommands().scan(scanOptions);
            try {
                while (cursor.hasNext()) {
                    byte[] keyBytes = cursor.next();
                    if (keyBytes != null) {
                        String key = new String(keyBytes, StandardCharsets.UTF_8);
                        if (key.startsWith(USER_SET_PREFIX)) {
                            String userId = key.substring(USER_SET_PREFIX.length());
                            if (!userId.isBlank()) activeUsers.add(userId);
                        }
                    }
                }
            } finally {
                if (cursor != null) cursor.close();
            }
            return null;
        });
        
        return activeUsers;
    } catch (Exception e) {
        log.warn(">>> 获取活跃用户失败", e);
        throw e;
    }
}
```

**关键设计点**：
- **双客户端**：主数据用原生 Lettuce（字节读写），辅助索引用 StringRedisTemplate
- **向量字节序**：`ByteOrder.LITTLE_ENDIAN`
- **TAG 转义**：`escapeTag` 处理特殊字符
- **SCAN 遍历**：避免 `KEYS` 阻塞

### 5.9 KnowledgeRouter（知识库路由）

**职责**：基于向量相似度自动选择最相关知识库。

**refresh() 流程**：
```java
private boolean refresh() {
    if (!refreshing.compareAndSet(false, true)) return false;  // CAS 互斥
    lastAttemptAt = System.currentTimeMillis();
    try {
        List<Long> kbIds = getAllKnowledgeBaseIds();
        int success = 0;
        for (Long kbId : kbIds) {
            try {
                String desc = buildDescription(kbId);
                if (desc == null || desc.isBlank()) continue;
                if (desc.length() > MAX_DESC_LENGTH) desc = desc.substring(0, MAX_DESC_LENGTH);
                float[] vec = embeddingModel.embed(desc).content().vector();
                kbVectors.put(kbId, vec);
                kbDescriptions.put(kbId, desc);
                success++;
            } catch (Exception e) {
                log.warn(">>> 知识库 {} 描述向量生成失败，将在下次刷新时重试", kbId, e);
            }
        }
        return true;
    } finally {
        refreshing.set(false);
    }
}
```

**ensureReady() 兜底重建**：
```java
private void ensureReady() {
    if (!kbVectors.isEmpty()) return;
    if (System.currentTimeMillis() - lastAttemptAt < retryBackoffMillis) return;  // 节流
    try {
        refresh();
    } catch (Exception e) {
        log.warn(">>> KnowledgeRouter 兜底重建向量失败", e);
    }
}
```

**route() 路由**：
```java
public Long route(String question) {
    if (question == null || question.isBlank()) return null;
    ensureReady();
    if (kbVectors.isEmpty()) return null;
    
    float[] questionVec = embeddingModel.embed(question).content().vector();
    
    Long bestKbId = null;
    double bestScore = -1;
    for (Map.Entry<Long, float[]> entry : kbVectors.entrySet()) {
        double score = cosine(questionVec, entry.getValue());
        if (score > bestScore) {
            bestScore = score;
            bestKbId = entry.getKey();
        }
    }
    
    if (bestScore < MIN_SCORE) return null;  // 0.5
    return bestKbId;
}
```

**buildDescription() 描述构建**：
```java
private String buildDescription(Long kbId) {
    StringBuilder sb = new StringBuilder();
    try {
        JSONObject docResp = knowledgeService.getDocuments(String.valueOf(kbId));
        JSONArray docs = docResp.getJSONArray("result");
        if (docs == null) docs = docResp.getJSONArray("data");
        if (docs != null && !docs.isEmpty()) {
            sb.append("知识库 ").append(kbId).append(" 包含文档：");
            int count = 0;
            for (int i = 0; i < docs.size() && count < 10; i++) {
                JSONObject o = docs.getJSONObject(i);
                JSONArray rows = o.getJSONArray("rows");
                if (rows == null) continue;
                for (int j = 0; j < rows.size() && count < 10; j++) {
                    String title = rows.getJSONObject(j).getString("originName");
                    if (title != null && !title.isBlank()) {
                        sb.append(title).append("；");
                        count++;
                    }
                }
            }
            sb.append("\n");
        }
    } catch (Exception e) {
        log.warn("获取知识库 {} 文档失败", kbId, e);
    }
    return sb.toString();
}
```

**关键设计点**：
- **后台预热**：`@PostConstruct` 启动定时任务，首次延迟 0
- **失败自愈**：请求链路兜底重建（带节流 + 互斥）
- **MIN_SCORE = 0.5**：低于阈值不选知识库
- **描述构建**：文档标题拼接，最多 10 个，截断 500 字

### 5.10 ToolRegistry（工具注册表）

**职责**：注册所有 `AiTool` Bean，提供按角色过滤的可用工具列表。

```java
@Component
public class ToolRegistry {
    private final Map<String, AiTool> tools = new ConcurrentHashMap<>();
    
    public ToolRegistry(List<AiTool> allTools) {
        for (AiTool t : allTools) {
            AiTool prev = tools.put(t.name(), t);
            if (prev != null) log.warn("工具名冲突: {}", t.name());
        }
        log.info(">>> 工具注册完成，共 {} 个: {}", tools.size(), tools.keySet());
    }
    
    public AiTool get(String name) { return tools.get(name); }
    
    /** 给 LLM 看的工具描述列表（已按角色过滤） */
    public List<ToolSpec> availableTools(AiToolContext ctx) {
        return tools.values().stream()
            .filter(t -> isAllowed(t, ctx))
            .map(t -> new ToolSpec(t.name(), t.description(), t.parameterSchema()))
            .toList();
    }
    
    /** 执行前二次校验 */
    public boolean isAllowed(String name, AiToolContext ctx) {
        AiTool t = tools.get(name);
        return t != null && isAllowed(t, ctx);
    }
    
    private boolean isAllowed(AiTool tool, AiToolContext ctx) {
        if (tool.allowedRoles().contains("*")) return true;
        if (ctx == null) return false;
        return tool.allowedRoles().stream().anyMatch(ctx::hasRole);
    }
}
```

**关键设计点**：
- **构造注入**：Spring 自动注入所有 `AiTool` Bean
- **冲突检测**：同名工具记录警告
- **二次校验**：`isAllowed` 在 AgentLoop 中调用，防止 LLM 幻觉

### 5.11 AiToolContext（工具上下文）

**职责**：ThreadLocal 保存当前用户 ID 和角色集合。

```java
public class AiToolContext {
    private static final ThreadLocal<AiToolContext> HOLDER = new ThreadLocal<>();
    
    private final String userId;
    private final RedisUtils redisUtils;
    private final Set<String> roleSet;
    
    /** 从 Redis 读取真实角色 */
    public static AiToolContext capture(String userId, RedisUtils redisUtils) {
        Set<String> roles = loadRolesFromRedis(userId, redisUtils);
        return new AiToolContext(userId, redisUtils, roles);
    }
    
    private static Set<String> loadRolesFromRedis(String userId, RedisUtils redisUtils) {
        if (userId == null || redisUtils == null) return new HashSet<>();
        try {
            String redisKey = userId + GlobalBusinessStatusEnum.REDIS_KEY_USER_ROLE.desc;
            Object raw = redisUtils.hget(redisKey, userId);
            if (raw instanceof Role roleContainer) {
                List<Role> roleList = roleContainer.getRoleList();
                if (roleList != null && !roleList.isEmpty()) {
                    return roleList.stream()
                        .map(Role::getName)
                        .filter(name -> name != null && !name.isBlank())
                        .collect(Collectors.toSet());
                }
            }
        } catch (Exception e) {
            log.warn("从 Redis 读取用户角色失败: userId={}", userId, e);
        }
        return new HashSet<>();
    }
    
    public static void set(AiToolContext context) { HOLDER.set(context); }
    public static AiToolContext get() { return HOLDER.get(); }
    public static void clear() { HOLDER.remove(); }
    
    public boolean hasRole(String role) {
        if (role == null) return false;
        if ("*".equals(role)) return true;
        return roleSet.contains(role);
    }
}
```

**关键设计点**：
- **ThreadLocal**：每个线程独立上下文
- **Redis 读取角色**：与 PermissionAspect / RoleUtils 同一来源
- **必须清理**：`AgentLoop.executeWithGuard` 的 `finally` 中调用 `clear()`

### 5.12 QueryKnowledgeTool（知识库问答工具）

**职责**：基于知识库的精准问答，支持自动路由。

**execute() 流程**：
```java
public ToolResult execute(Map<String, Object> args, AiToolContext ctx) {
    String question = String.valueOf(args.get("question"));
    if (question == null || question.isBlank() || "null".equals(question)) {
        return ToolResult.fail("问题不能为空");
    }
    
    // 1. 解析 knowledgeBaseId
    Long kbId = parseKbId(args.get("knowledgeBaseId"));
    if (kbId != null && !isValidKnowledgeId(kbId)) {
        log.warn("[query_knowledge] 无效 knowledgeBaseId={}，回落自动路由", kbId);
        kbId = null;
    }
    
    // 2. 自动路由
    if (kbId == null) {
        kbId = knowledgeRouter.route(question);
        if (kbId == null) {
            return ToolResult.ok("无法判断该问题属于哪个知识库，请明确指定知识库 ID。");
        }
        log.info("[query_knowledge] 自动路由到知识库 {}", kbId);
    }
    
    // 3. 查询
    String content = queryOne(kbId, question);
    return ToolResult.ok(content, Map.of("knowledgeBaseId", kbId));
}
```

**isValidKnowledgeId() 校验**：
```java
private boolean isValidKnowledgeId(Long id) {
    if (id == null) return false;
    String s = String.valueOf(id);
    if (s.length() < MIN_KB_ID_LENGTH || !s.matches("\\d+")) return false;  // 13 位纯数字
    return templateRegistry.get(s) != null;  // 已注册
}
```

**queryOne() 查询**：
```java
private String queryOne(Long knowledgeBaseId, String question) {
    String knowledgeId = String.valueOf(knowledgeBaseId);
    if (knowledgeId.length() < MIN_KB_ID_LENGTH || !knowledgeId.matches("\\d+")) {
        return "无法定位知识库，请稍后再试。";
    }
    
    // 1. 获取模板
    KnowledgeBase template = templateRegistry.get(knowledgeId);
    if (template == null) {
        try {
            knowledgeRegistrar.register(knowledgeId);
            template = templateRegistry.get(knowledgeId);
        } catch (Exception e) {
            return "无法加载该知识库，请稍后再试。";
        }
    }
    if (template == null) return "未找到相关知识库。";
    
    // 2. 拷贝 + 设置查询
    KnowledgeBase kb = copyTemplate(template);
    kb.setQuery(question);
    
    // 3. 校验配置
    if (kb.getProjectId() == null || kb.getRelation() == null || kb.getRelation().isEmpty()
            || kb.getRelation().get(0).getEmbedding() == null) {
        return "该知识库配置不完整，暂时无法检索。";
    }
    if (kb.getIndexMode() == null) kb.setIndexMode("vector");
    if (kb.getSimilarityTopK() == null) kb.setSimilarityTopK(10);
    
    // 4. 查询下游
    JSONArray arr = knowledgeService.queryAnswer(kb);
    if (arr == null || arr.isEmpty()) return "知识库中未找到相关答案。";
    
    // 5. 拼接结果（最多 5 条，每条 800 字）
    StringBuilder sb = new StringBuilder();
    int n = Math.min(arr.size(), MAX_RESULT_ITEMS);
    for (int i = 0; i < n; i++) {
        JSONObject o = arr.getJSONObject(i);
        String text = o.getString("text");
        if (text == null || text.isBlank()) continue;
        text = text.replaceAll("<!--.*?-->", "").trim();  // 去注释
        if (text.length() > MAX_TEXT_LEN) text = text.substring(0, MAX_TEXT_LEN) + "...";
        sb.append("- ").append(text).append("\n");
    }
    if (sb.length() == 0) return "知识库中未找到相关答案。";
    return "以下是与问题相关的内容：\n" + sb;
}
```

**关键设计点**：
- **knowledgeBaseId 可选**：未传或无效则自动路由
- **13 位纯数字**：`MIN_KB_ID_LENGTH = 13`
- **模板拷贝**：避免污染 `templateRegistry`
- **结果裁剪**：最多 5 条，每条 800 字

### 5.13 AiChatExecutor（AI 线程池）

**职责**：AI 对话专用线程池，限流 + 拒绝策略。

```java
@Component
@ConditionalOnProperty(prefix = "ai.chat", name = "enabled", havingValue = "true", matchIfMissing = true)
public final class AiChatExecutor {
    private final ThreadPoolExecutor executor;
    
    public AiChatExecutor(
            @Value("${ai.chat.max-concurrent:16}") int maxConcurrent,
            @Value("${ai.chat.queue-capacity:100}") int queueCapacity,
            @Value("${ai.chat.keep-alive-seconds:60}") long keepAliveSeconds) {
        // 参数校验
        if (maxConcurrent < 1) throw new IllegalArgumentException("AI 聊天最大并发数必须大于 0");
        if (queueCapacity < 1) throw new IllegalArgumentException("AI 聊天队列容量必须大于 0");
        
        // 命名线程工厂
        AtomicInteger sequence = new AtomicInteger();
        ThreadFactory defaultFactory = Executors.defaultThreadFactory();
        ThreadFactory namedFactory = task -> {
            Thread thread = defaultFactory.newThread(task);
            thread.setName("ai-chat-" + sequence.incrementAndGet());
            thread.setDaemon(false);
            return thread;
        };
        
        // 线程池
        this.executor = new ThreadPoolExecutor(
            0,                          // 核心线程 0
            maxConcurrent,              // 最大并发
            keepAliveSeconds,           // 空闲回收
            TimeUnit.SECONDS,
            new LinkedBlockingQueue<>(queueCapacity),  // 有界队列
            namedFactory,
            new ThreadPoolExecutor.AbortPolicy()       // 拒绝抛异常
        );
        this.executor.allowCoreThreadTimeOut(true);     // 核心线程也回收
    }
    
    /** 提交任务。被拒绝时返回 false，不抛异常。 */
    public boolean submit(Runnable task) {
        if (task == null) return false;
        try {
            executor.execute(task);
            return true;
        } catch (RejectedExecutionException e) {
            log.warn("AiChatExecutor 已满，任务被拒绝。activeCount={}, queueSize={}",
                    executor.getActiveCount(), executor.getQueue().size());
            return false;
        }
    }
    
    @PreDestroy
    public void shutdown() {
        executor.shutdown();
        try {
            if (!executor.awaitTermination(30, TimeUnit.SECONDS)) {
                log.warn("AiChatExecutor 30s 内未关闭，强制中断");
                executor.shutdownNow();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            executor.shutdownNow();
        }
    }
}
```

**关键设计点**：
- **核心线程 0**：完全按需创建，空闲回收
- **有界队列**：防止无界堆积
- **AbortPolicy**：拒绝抛异常，`submit` 捕获返回 false
- **优雅关闭**：等待 30s 后强制中断
- **命名线程**：`ai-chat-{n}` 便于排查

### 5.14 RedisLockHelper（分布式锁）

```java
@Component
@RequiredArgsConstructor
public class RedisLockHelper {
    private static final String UNLOCK_LUA =
        "if redis.call('get', KEYS[1]) == ARGV[1] then " +
        "return redis.call('del', KEYS[1]) else return 0 end";
    
    private final StringRedisTemplate redis;
    
    /** 尝试加锁 */
    public boolean tryLock(String key, String token, long ttlMillis) {
        Boolean ok = redis.opsForValue().setIfAbsent(key, token, Duration.ofMillis(ttlMillis));
        return Boolean.TRUE.equals(ok);
    }
    
    /** 解锁（Lua 比对 token） */
    public void unlock(String key, String token) {
        redis.execute(new DefaultRedisScript<>(UNLOCK_LUA, Long.class),
                Collections.singletonList(key), token);
    }
    
    /** 强制解锁 */
    public void forceUnlock(String key) {
        redis.delete(key);
    }
}
```

**关键设计点**：
- **原子加锁**：`setIfAbsent` + TTL
- **Lua 解锁**：比对 token 后删除，避免误删他人锁
- **强制解锁**：新请求替换旧请求时使用

### 5.15 AiChatSseEmitter（SSE 封装）

```java
public class AiChatSseEmitter extends SseEmitter {
    private final AtomicBoolean closed = new AtomicBoolean(false);
    private volatile Runnable onCloseCallback;
    
    public AiChatSseEmitter() {
        super(300_000L);  // 5 分钟超时
        
        super.onCompletion(() -> {
            closed.set(true);
            Runnable cb = onCloseCallback;
            if (cb != null) { try { cb.run(); } catch (Exception ignored) {} }
        });
        super.onTimeout(() -> {
            closed.set(true);
            Runnable cb = onCloseCallback;
            if (cb != null) { try { cb.run(); } catch (Exception ignored) {} }
        });
    }
    
    public boolean isClosed() { return closed.get(); }
    
    public void setOnCloseCallback(Runnable callback) {
        this.onCloseCallback = callback;
        if (closed.get() && callback != null) {  // 注册前已关闭，立即回调
            try { callback.run(); } catch (Exception ignored) {}
        }
    }
    
    public void send(String chunk) { /* delta 事件 */ }
    public void send(String eventName, Object data) { /* 结构化事件 */ }
    public void send(SseEmitter.SseEventBuilder event) { /* 原始事件 */ }
    
    @Override
    public void complete() {
        if (!closed.compareAndSet(false, true)) return;  // CAS 幂等
        try {
            super.complete();
        } finally {
            if (onCloseCallback != null) {
                try { onCloseCallback.run(); } catch (Exception ignored) {}
                onCloseCallback = null;
            }
        }
    }
}
```

**关键设计点**：
- **CAS 幂等**：`complete()` 只执行一次
- **回调注册前已关闭**：立即执行
- **IOException 自动标记关闭**：防止继续发送
- **5 分钟超时**：防止连接泄漏

---

## 6. 关键机制深度剖析

### 6.1 并发控制机制

#### 6.1.1 三层并发控制

| 层次 | 机制 | 目的 |
|------|------|------|
| **会话级** | Redis 分布式锁 | 同一会话串行 |
| **全局级** | AiChatExecutor 线程池 | 全局并发限流 |
| **请求级** | SSE closed 标志 + requestId | 单请求可中断 |

#### 6.1.2 会话替换流程

```
新请求到达（同一 memoryId）
   │
   ├─ 1. oldToken = runningTokens.remove(memoryId)
   ├─ 2. if (oldToken != null) forceUnlock(lockKey)
   ├─ 3. oldEmitter = runningEmitters.remove(memoryId)
   ├─ 4. if (oldEmitter != null) oldEmitter.send("error", "会话已被新请求替换")
   ├─ 5. oldEmitter.complete()
   │       └─ onCompletion 回调触发
   │           └─ runningEmitters.remove(memoryId, oldEmitter)  // 已被移除，无效
   │
   ├─ 6. runningEmitters.put(memoryId, newEmitter)
   ├─ 7. runningTokens.put(memoryId, newToken)
   ├─ 8. tryLock(lockKey, newToken, 5000L)
   └─ 9. chatExecutor.execute(...)
```

**关键顺序**：`forceUnlock` 必须在 `oldEmitter.complete()` 之前，否则 `onCompletion` 回调会提前清理 `runningTokens`，导致 `oldToken` 为 null。

### 6.2 锁安全机制

#### 6.2.1 误删他人锁问题

**问题场景**：
```
T1: 请求 A 加锁，TTL 5s
T2: 请求 A 执行超过 5s（LLM 慢）
T3: 锁自动过期，请求 B 加锁成功
T4: 请求 A 执行完毕，调用 unlock
T5: 请求 A 误删请求 B 的锁 ← BUG
```

**解决方案**：Lua 脚本比对 token
```lua
if redis.call('get', KEYS[1]) == ARGV[1] then
    return redis.call('del', KEYS[1])
else
    return 0
end
```

只有 token 匹配才删除，保证只删自己的锁。

### 6.3 SSE 生命周期

#### 6.3.1 状态转换

```
[创建] → [发送中] → [完成/超时/客户端断开] → [已关闭]
   │         │              │
   │         │              └─ onCompletion/onTimeout 回调
   │         │
   │         └─ send() 时 IOException / IllegalStateException → closed.set(true)
   │
   └─ complete() CAS 幂等
```

#### 6.3.2 事件协议

| 事件名 | 触发时机 | payload |
|--------|----------|---------|
| `delta` | LLM 流式输出 | `{text: "片段"}` |
| `tool` | 工具执行 | `{name, args, status, result?}` |
| `tool_confirm` | 需确认工具 | `{callId, tool, args}` |
| `done` | 完成 | `{messageId}` 或 `{answer}` |
| `error` | 异常 | `{code?, message}` |

### 6.4 长期记忆机制

#### 6.4.1 记忆生命周期

```
对话完成
   │
   ▼
MemoryExtractor.extract()（异步）
   │
   ├─ LLM 抽取 → List<Map>
   ├─ 逐条校验（type/content）
   ├─ 去重（相同内容旧记忆失效）
   ├─ 向量化（失败跳过）
   └─ MemoryStore.save()
        │
        ▼
   Redis Hash（主数据）+ Set（用户索引）+ 反向索引
        │
        ▼
   90 天后自动过期
        │
        ▼
   MemoryDecayJob（每日 3 点）
        ├─ decayAll(userId, 0.95)  // 重要性 × 0.95
        └─ deleteBelow(userId, 0.1)  // 删除重要性 < 0.1
```

#### 6.4.2 记忆召回

```
新对话开始
   │
   ▼
AiAgentRunner.run()
   │
   ├─ embeddingClient.embed(request.message())
   ├─ memoryStore.searchByUser(userId, qVec, 5)
   │     └─ RediSearch KNN 查询
   └─ 拼成 memoryBlock → 注入 ContextBuilder
```

### 6.5 知识库路由机制

#### 6.5.1 预热与自愈

```
应用启动
   │
   ▼
@PostConstruct init()
   │
   └─ scheduleWithFixedDelay(refreshQuietly, 0, 10min)
        │
        ▼
   refresh()
        ├─ getAllKnowledgeBaseIds()
        ├─ for each kbId:
        │     ├─ buildDescription(kbId)  // 文档标题拼接
        │     ├─ embeddingModel.embed(desc)
        │     └─ kbVectors.put(kbId, vec)
        └─ 失败：保留旧缓存，下次重试

请求链路
   │
   ▼
route(question)
   │
   ├─ ensureReady()  // 缓存空则兜底重建（节流 + 互斥）
   ├─ embed(question)
   └─ 遍历计算余弦相似度
```

#### 6.5.2 路由决策

```
bestScore >= 0.5 → 返回 bestKbId
bestScore <  0.5 → 返回 null（由调用方走默认知识库）
```

---

## 7. 配置项说明

### 7.1 LangChain4j 配置

```yaml
langchain4j:
  open-ai:
    base-url: https://api.example.com/v1
    api-key: sk-xxx
    chat-model:
      model-name: qwen3-max
    streaming-chat-model:
      model-name: qwen3-max
      temperature: 0.7
    embedding-model:
      model-name: text-embedding-v3
  http:
    timeout-seconds: 60
    max-retries: 3
    log-requests: false
    log-responses: false
```

### 7.2 AI 对话配置

```yaml
ai:
  chat:
    enabled: true              # 是否启用
    max-concurrent: 16         # 最大并发
    queue-capacity: 100        # 队列容量
    keep-alive-seconds: 60     # 空闲回收
  embedding:
    dimension: 1536            # 向量维度（需与 embedding 模型一致）
  knowledge-router:
    refresh-minutes: 10        # 刷新间隔
    retry-backoff-millis: 60000 # 兜底重建节流
```

### 7.3 配置项详解

| 配置项 | 默认值 | 说明 | 调优建议 |
|--------|--------|------|----------|
| `ai.chat.max-concurrent` | 16 | 最大并发对话数 | 根据 LLM 网关 QPS 调整 |
| `ai.chat.queue-capacity` | 100 | 排队队列 | 过大导致延迟高，过小拒绝多 |
| `ai.chat.keep-alive-seconds` | 60 | 线程空闲回收 | 高并发场景可减小 |
| `ai.embedding.dimension` | 1536 | 向量维度 | **必须与模型一致**，否则 KNN 失败 |
| `ai.knowledge-router.refresh-minutes` | 10 | 刷新间隔 | 知识库变更频繁时减小 |
| `ai.knowledge-router.retry-backoff-millis` | 60000 | 兜底节流 | 防止打爆下游 |
| `langchain4j.http.timeout-seconds` | 60 | HTTP 超时 | 流式场景建议 ≥ 120 |
| `langchain4j.http.max-retries` | 3 | 重试次数 | 流式模型不支持 |

---

## 8. SSE 事件协议

### 8.1 事件总览

```
event: delta
data: {"text":"你好"}

event: delta
data: {"text":"，我是"}

event: tool
data: {"name":"query_knowledge","args":{"question":"..."},"status":"executing"}

event: tool
data: {"name":"query_knowledge","args":{"question":"..."},"status":"completed","result":"..."}

event: done
data: {"messageId":12345}
```

### 8.2 事件类型详解

| 事件 | 频率 | payload 字段 | 前端处理 |
|------|------|--------------|----------|
| `delta` | 高 | `text` | 追加到当前消息 |
| `tool` | 中 | `name`, `args`, `status`, `result?` | 显示工具执行状态 |
| `tool_confirm` | 低 | `callId`, `tool`, `args` | 弹出确认框 |
| `done` | 一次 | `messageId` 或 `answer` | 结束当前消息 |
| `error` | 低 | `code?`, `message` | 显示错误 |

### 8.3 事件顺序保证

```
正常流程：
  delta* → tool(executing) → tool(completed) → delta* → done

需确认流程：
  delta* → tool_confirm → [SSE 关闭]

错误流程：
  delta* → error → [SSE 关闭]
```

---

## 9. 异常处理与降级策略

### 9.1 异常分层

| 层次 | 异常类型 | 处理策略 |
|------|----------|----------|
| **Controller** | 参数校验 | 返回 400 |
| **Service** | 加锁失败 | 返回错误 SSE |
| **Runner** | 落库/召回失败 | 日志 + 继续（记忆召回降级） |
| **AgentLoop** | LLM/工具异常 | 发 error 事件 + 关闭 SSE |
| **LLM** | 流式超时 | 返回空响应 |
| **MemoryStore** | embedding 失败 | 跳过该条记忆 |
| **KnowledgeRouter** | 刷新失败 | 保留旧缓存 |

### 9.2 降级矩阵

| 组件 | 故障 | 降级行为 | 影响 |
|------|------|----------|------|
| **Embedding** | 不可用 | 跳过记忆召回 | 无长期记忆，但对话正常 |
| **MemoryStore** | 不可用 | 返回空列表 | 无长期记忆 |
| **KnowledgeRouter** | 缓存空 | 返回 null | 由调用方走默认知识库 |
| **LLM** | 超时 | 返回空响应 | 对话失败 |
| **工具** | 超时/异常 | `ToolResult.fail` | LLM 收到失败结果，可重新决策 |
| **Redis** | 不可用 | 加锁失败 | 返回错误 SSE |
| **SSE** | 关闭 | 检测退出 | 提前结束 |

### 9.3 兜底策略

```java
// 1. 记忆召回降级
List<Double> qVec = embeddingClient.embed(request.message());  // 抛异常 → catch 捕获

// 2. 工具超时
CompletableFuture.supplyAsync(...).orTimeout(30, SECONDS)

// 3. LLM 超时
future.get(120, TimeUnit.SECONDS);

// 4. ThreadLocal 清理
try { ... } finally { AiToolContext.clear(); }

// 5. 锁释放
finally { redisLockHelper.unlock(lockKey, token); }

// 6. SSE 关闭
try { emitter.send(...) } catch (IOException e) { closed.set(true); }

// 7. 记忆抽取单条隔离
for (Map<String, Object> it : items) {
    try { saveOne(userId, it); } catch (Exception e) { log.warn(...); }
}
```

---

## 10. 性能与并发设计

### 10.1 性能指标

| 指标 | 目标 | 说明 |
|------|------|------|
| 首字延迟 | < 2s | LLM 流式输出 |
| 工具执行 | < 30s | 超时保护 |
| 上下文构建 | < 100ms | 含摘要缓存 |
| 记忆召回 | < 200ms | KNN 查询 |
| 知识库路由 | < 100ms | 余弦相似度 |

### 10.2 并发设计

| 层面 | 机制 | 参数 |
|------|------|------|
| **会话级** | Redis 锁 | TTL 5s |
| **全局级** | 线程池 | 16 并发 + 100 队列 |
| **异步记忆** | memoryExecutor | 2-4 线程 + 200 队列 |
| **向量刷新** | 单线程定时 | 10 分钟间隔 |

### 10.3 资源消耗

| 资源 | 消耗 | 说明 |
|------|------|------|
| **Redis 连接** | 2 个（String + Native） | 主数据 + 辅助索引 |
| **线程** | 16（对话）+ 4（记忆） | 可配 |
| **内存** | 上下文 24000 字符/会话 | 约 50KB |
| **Redis 存储** | 记忆 ~1KB/条 | 90 天 TTL |

### 10.4 优化建议

1. **LLM 缓存**：相同问题可缓存响应
2. **工具并发**：多个工具可并行执行（需确认无依赖）
3. **记忆预加载**：会话开始时批量召回
4. **上下文压缩**：更激进的摘要策略
5. **向量索引优化**：调整 HNSW 参数

---

## 11. 安全与权限

### 11.1 权限模型

```
用户 → 角色（Redis）→ AiToolContext.roleSet
   │
   ▼
ToolRegistry.availableTools(ctx)
   └─ 过滤 allowedRoles 不匹配的工具
   │
   ▼
AgentLoop 执行前二次校验
   └─ toolRegistry.isAllowed(name, ctx)
```

### 11.2 权限校验点

| 校验点 | 位置 | 目的 |
|--------|------|------|
| **工具列表过滤** | `availableTools` | 不给 LLM 未授权工具 |
| **执行前校验** | `AgentLoop` | 防止 LLM 幻觉调用 |
| **会话 owner** | `assertOwner` | 防止越权访问会话 |
| **锁 token** | `unlock` Lua | 防止误删他人锁 |
| **requestId** | `stop` | 防止误停新请求 |

### 11.3 安全风险与防护

| 风险 | 防护 |
|------|------|
| **Prompt Injection** | 系统提示强约束 + 工具权限 |
| **越权工具调用** | 二次校验 + 角色过滤 |
| **会话劫持** | sessionId + userId 绑定 |
| **锁误删** | Lua token 比对 |
| **SSE 连接泄漏** | 5 分钟超时 + 回调清理 |
| **LLM 脏数据** | `looksLikeId` 检测 |
| **向量维度攻击** | 维度校验 |

---

## 12. 部署与运维

### 12.1 依赖服务

| 服务 | 版本要求 | 用途 |
|------|----------|------|
| **Redis** | 7.0+ | 缓存 + 锁 + 记忆 |
| **RediSearch** | 2.6+ | 向量索引 |
| **LLM 网关** | OpenAI 兼容 | 对话 + Embedding |

### 12.2 部署检查清单

- [ ] Redis 已安装 RediSearch 模块
- [ ] `ai.embedding.dimension` 与实际模型一致
- [ ] LLM 网关可达（base-url + api-key）
- [ ] Conscrypt 已安装（TLS 1.3）
- [ ] 线程池参数已调优
- [ ] 日志级别合理（生产 INFO）

### 12.3 监控指标

| 指标 | 来源 | 告警阈值 |
|------|------|----------|
| 活跃对话数 | `AiChatExecutor.getActiveCount()` | > 14 |
| 队列长度 | `AiChatExecutor.getQueueSize()` | > 80 |
| 完成任务数 | `AiChatExecutor.getCompletedTaskCount()` | 监控趋势 |
| 工具超时率 | 日志统计 | > 5% |
| LLM 错误率 | 日志统计 | > 1% |
| Redis 连接数 | Redis 监控 | > 80% |

### 12.4 日志排查

**关键日志标记**：

| 标记 | 含义 | 排查方向 |
|------|------|----------|
| `>>> AgentLoop 启动` | 循环开始 | 检查工具数 |
| `>>> AgentLoop step={}` | 步数 | 检查是否超过 8 |
| `>>> 路由结果` | 知识库路由 | 检查 score |
| `>>> 成功获取锁` | 加锁成功 | 检查锁冲突 |
| `>>> 强制释放旧 Redis 锁` | 会话替换 | 检查并发 |
| `AiAgentRunner 异常` | 运行异常 | 查看堆栈 |
| `chatStream 超时或异常` | LLM 超时 | 检查网关 |

### 12.5 常见问题

| 问题 | 原因 | 解决 |
|------|------|------|
| 对话无响应 | LLM 网关不可达 | 检查 base-url/api-key |
| 记忆不生效 | embedding 维度不符 | 检查配置 |
| 知识库路由失败 | 缓存为空 | 检查知识库服务 |
| 锁冲突频繁 | LLM 慢导致锁过期 | 增加锁 TTL 或减少 LLM 延迟 |
| SSE 连接泄漏 | 客户端未断开 | 检查心跳机制 |
| 工具超时 | 下游慢 | 增加超时或优化下游 |

---

## 13. 扩展指南

### 13.1 新增工具

**步骤**：
1. 实现 `AiTool` 接口
2. 加 `@Component` 注解
3. 定义 `name` / `description` / `parameterSchema` / `execute`
4. 定义 `allowedRoles`（默认 `*`）
5. 定义 `requiresConfirmation`（默认 `false`）

**示例**：
```java
@Component
public class MyTool implements AiTool {
    @Override public String name() { return "my_tool"; }
    
    @Override
    public String description() {
        return "工具描述，告诉 LLM 何时调用";
    }
    
    @Override
    public Map<String, Object> parameterSchema() {
        return Map.of(
            "type", "object",
            "properties", Map.of(
                "param1", Map.of("type", "string", "description", "参数1")
            ),
            "required", List.of("param1")
        );
    }
    
    @Override
    public Set<String> allowedRoles() { return Set.of("USER", "ADMIN"); }
    
    @Override
    public boolean requiresConfirmation() { return false; }
    
    @Override
    public ToolResult execute(Map<String, Object> args, AiToolContext ctx) {
        // 实现逻辑
        return ToolResult.ok("结果");
    }
}
```

### 13.2 替换历史存储

**步骤**：
1. 实现 `ChatHistoryStore` 接口
2. 加 `@Component` 注解
3. 替换 `InMemoryChatHistoryStore`

**关键方法**：
- `appendUser` / `appendAssistant`：落库
- `load`：加载会话消息
- `loadAssistant`：读取助手回答
- `exists` / `assertOwner`：会话校验

### 13.3 新增记忆类型

**步骤**：
1. 修改 `SummaryUtil.ALLOWED_TYPES`
2. 修改 prompt 中的判断标准
3. 可选：调整 `MemoryItem` 结构

### 13.4 自定义系统提示

**位置**：`AiAgentRunner.run()` 中 `Message.system(...)`

**建议**：
- 明确工具使用规则
- 定义输出格式
- 处理边界情况

---

## 14. 已知问题与改进建议

### 14.1 已知问题

| 问题 | 位置 | 影响 | 建议 |
|------|------|------|------|
| **历史存储内存实现** | `InMemoryChatHistoryStore` | 重启丢失 | 待 PG 表就绪后替换 |
| **政策状态硬编码** | `InMemoryChatHistoryStore.policyStatus` | 固定返回 | 待政策表就绪 |
| **AgentLoop.memoryStore 未使用** | `AgentLoop` | 冗余字段 | 移除或预留 |
| **stop() 与 onCompletion 可能重复清理** | `AiAgentService` | 幂等性 | 已用 `remove(key, value)` 保证 |
| **LangChain4jLlmClient 完整内容覆盖** | `onCompleteResponse` | 增量与完整不一致 | 需验证模型行为 |
| **System 消息不裁剪可能超预算** | `ContextBuilder.trimToBudget` | 极端情况 | 可考虑裁剪记忆块 |
| **工具串行执行** | `AgentLoop` | 多工具慢 | 无依赖时可并行 |
| **锁 TTL 5s 可能不够** | `AiAgentService.chat` | 长对话锁过期 | 可续期或增大 TTL |

### 14.2 改进建议

#### 14.2.1 短期（1-2 周）

1. **历史存储持久化**：接入 PG
2. **政策状态完善**：接入政策表
3. **清理冗余代码**：移除 `AgentLoop.memoryStore`
4. **监控告警**：接入 Prometheus + Grafana

#### 14.2.2 中期（1-2 月）

1. **工具并行执行**：无依赖工具用 `CompletableFuture.allOf`
2. **LLM 缓存**：相同问题缓存响应
3. **锁续期**：长对话自动续期
4. **记忆预加载**：会话开始批量召回
5. **上下文压缩**：更激进的摘要策略

#### 14.2.3 长期（3-6 月）

1. **多模型路由**：根据问题类型选模型
2. **Agent 编排**：多 Agent 协作
3. **知识图谱**：结构化知识 + 向量混合检索
4. **RLHF**：基于用户反馈优化
5. **多模态**：支持图片/语音输入

---

## 15. 附录：接口签名速查

### 15.1 AiAgentService

```java
public SseEmitter chat(ChatRequest request, String userId);
public void stop(String sessionId, String userId, String clientRequestId);
```

### 15.2 AiAgentRunner

```java
public void run(ChatRequest request, AiToolContext toolContext, String userId,
                boolean newSession, String sessionId,
                long userMessageId, long assistantMessageId,
                Locale locale, String memoryId, String lockKey,
                String token, AiChatSseEmitter emitter, String runId);
```

### 15.3 AgentLoop

```java
public void run(AgentContext ctx);
```

### 15.4 LlmClient

```java
LlmResponse chatStream(List<Message> messages, List<ToolSpec> tools, AiChatSseEmitter emitter);
String chatSimple(String prompt);
```

### 15.5 MemoryStore

```java
void save(MemoryItem item);
List<MemoryItem> searchByUser(String userId, List<Double> queryEmbedding, int topK);
List<MemoryItem> findByUserAndContentLike(String userId, String keyword);
void invalidate(String memoryId);
void decayAll(String userId, double factor);
void deleteBelow(String userId, double threshold);
Set<String> getAllActiveUsers();
```

### 15.6 ChatHistoryStore

```java
boolean exists(String sessionId);
void assertOwner(String sessionId, String userId);
long nextMessageId();
void append(String sessionId, String userId, long messageId, String role, String content, String extra);
AiChatPolicyResponse policyStatus(String currentUserId);
void acceptPolicy(String currentUserId);
List<AiChatSessionResponse> sessions(String currentUserId);
String loadAssistant(long assistantMessageId);
List<Message> load(String sessionId);
void appendUser(long userMessageId, String sessionId, String userId, String message);
void appendAssistant(long assistantMessageId, String sessionId, String content);
```

### 15.7 RedisLockHelper

```java
boolean tryLock(String key, String token, long ttlMillis);
void unlock(String key, String token);
void forceUnlock(String key);
```

### 15.8 AiChatSseEmitter

```java
boolean isClosed();
void setOnCloseCallback(Runnable callback);
void send(String chunk);
void send(String eventName, Object data);
void send(SseEmitter.SseEventBuilder event);
void complete();
```

### 15.9 AiChatExecutor

```java
boolean submit(Runnable task);
int getActiveCount();
int getQueueSize();
long getCompletedTaskCount();
void shutdown();
```

### 15.10 AiTool

```java
String name();
String description();
Map<String, Object> parameterSchema();
ToolResult execute(Map<String, Object> args, AiToolContext ctx);
default Set<String> allowedRoles() { return Set.of("*"); }
default boolean requiresConfirmation() { return false; }
```

### 15.11 ToolRegistry

```java
AiTool get(String name);
List<ToolSpec> availableTools(AiToolContext ctx);
boolean isAllowed(String name, AiToolContext ctx);
```

### 15.12 AiToolContext

```java
static AiToolContext capture(String userId, RedisUtils redisUtils);
static AiToolContext capture(String userId, Set<String> roleSet, RedisUtils redisUtils);
static void set(AiToolContext context);
static AiToolContext get();
static void clear();
String getUserId();
RedisUtils getRedisUtils();
boolean hasRole(String role);
Set<String> roleSet();
```

### 15.13 ContextBuilder

```java
List<Message> build(String sessionId, String newUserMsg, String longTermMemoryBlock);
```

### 15.14 SummaryUtil

```java
String summarize(List<Message> messages);
String summarize(List<Message> messages, int maxWordSize);
String extractLongTermMemories(String dialog);
List<Map<String, Object>> extractLongTermMemoriesAsList(String dialog);
String stripCodeFence(String s);
```

### 15.15 MemoryExtractor

```java
@Async("memoryExecutor")
void extract(String userId, List<Message> turn);
```

### 15.16 KnowledgeRouter

```java
Long route(String question);
List<Long> routeTopN(String question, int n);
```

### 15.17 PendingStateStore

```java
void save(AgentContext ctx);
PendingState load(String runId);
void remove(String runId);
```

---

**文档结束**

> 本文档基于 `com.lenovo.ai.*` 全模块代码梳理，如有变更请同步更新。
> 最后更新：2026-10-10