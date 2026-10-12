#  AI 智能体系统开发文档（详细版）

> 版本：1.1
> 日期：2026-10-12
> 项目：iam-backend-ai
> 作者：laifo

---

## 目录

- [第 1 章 系统概述](#第-1-章-系统概述)
- [第 2 章 整体架构](#第-2-章-整体架构)
- [第 3 章 模块详解](#第-3-章-模块详解)
- [第 4 章 核心业务流程](#第-4-章-核心业务流程)
- [第 5 章 长期记忆子系统](#第-5-章-长期记忆子系统)
- [第 6 章 多 Agent 编排子系统](#第-6-章-多-agent-编排子系统)
- [第 7 章 精准中断子系统](#第-7-章-精准中断子系统)
- [第 8 章 SSE 协议规范](#第-8-章-sse-协议规范)
- [第 9 章 Redis 数据结构设计](#第-9-章-redis-数据结构设计)
- [第 10 章 完整代码清单](#第-10-章-完整代码清单)
- [第 11 章 关键问题与修复记录](#第-11-章-关键问题与修复记录)
- [第 12 章 测试与验收](#第-12-章-测试与验收)
- [第 13 章 配置说明](#第-13-章-配置说明)
- [第 14 章 性能与容量](#第-14-章-性能与容量)
- [第 15 章 后续优化路线图](#第-15-章-后续优化路线图)
- [附录 A：关键类依赖关系](#附录-a关键类依赖关系)
- [附录 B：故障排查速查表](#附录-b故障排查速查表)
- [附录 C：术语表](#附录-c术语表)

---

## 第 1 章 系统概述

### 1.1 项目定位

**iam-backend-ai** 是一套面向企业内网的 **AI 智能助手后端服务**，为前端 UAR（United AI Robot）智能助手提供对话、知识库问答、长期记忆、多 Agent 编排能力。

### 1.2 核心能力矩阵

| 能力             | 说明                        | 落地方式                                      |
| -------------- | ------------------------- | ----------------------------------------- |
| **流式对话**       | 用户提问后，AI 逐字返回，实时可见        | SSE（Server-Sent Events）                   |
| **知识库问答**      | 自动路由到相关企业知识库，检索答案         | `QueryKnowledgeTool` + `KnowledgeRouter`  |
| **长期记忆**       | 跨会话记住用户信息                 | `UserMemoryStore` + Redis                 |
| **多 Agent 编排** | 复杂任务拆解为多个子任务，由不同 Agent 协同 | `PlannerAgent` + `Orchestrator`           |
| **精准中断**       | 用户可以随时停止当前生成              | `clientRequestId` + `AiAgentService.stop` |
| **思考过程可见**     | 展示 LLM 的推理过程、工具调用状态       | SSE `thinking` / `status` / `tool` 事件     |

### 1.3 技术选型

| 层         | 技术                    | 版本    | 用途       |
| --------- | --------------------- | ----- | -------- |
| 前端框架      | Vue 3                 | 3.4+  | UI       |
| UI 组件     | Ant Design Vue        | 4.x   | 通用组件     |
| HTTP      | Fetch API             | 原生    | SSE 流式请求 |
| 后端框架      | Spring Boot           | 3.x   | 应用容器     |
| 语言        | Java                  | 17    | 后端逻辑     |
| AI 框架     | LangChain4j           | 0.35+ | LLM 集成   |
| LLM       | kimi-k2.6             | -     | 对话/推理    |
| Embedding | text-embedding-v3     | -     | 1024 维向量 |
| 存储        | Redis                 | 6.x+  | 记忆/锁/历史  |
| 序列化       | Jackson               | 2.14+ | JSON     |
| 异步        | Spring `@Async` + 线程池 | -     | 记忆抽取     |

### 1.4 关键设计原则

1. **SSE 优先**：所有 AI 输出流式返回，用户体验优先。
2. **requestId 精准寻址**：每个请求有唯一 `requestId`，支持精准停止。
3. **异步不阻塞主流**：记忆抽取、持久化等放到独立线程池。
4. **失败降级**：记忆召回失败、embedding 失败不阻塞主流程。
5. **去重优先**：写入前两级去重（精确 + 语义），避免记忆污染。

---

## 第 2 章 整体架构

### 2.1 分层架构（完整版）

```
┌─────────────────────────────────────────────────────────────────────────┐
│                          浏览器 (Vue 3)                                  │
│                                                                          │
│  ┌────────────────┐  ┌────────────────┐  ┌────────────────────┐        │
│  │  index.vue      │  │  ai.js          │  │  history.vue        │        │
│  │  - 消息列表      │  │  - chatAiApi    │  │  - 会话列表         │        │
│  │  - 输入框        │  │  - stopAiChatApi│  │                    │        │
│  │  - 停止按钮      │  │  - SSE 解析      │  │                    │        │
│  └────────┬───────┘  └────────┬────────┘  └────────────────────┘        │
│           │                    │                                          │
│           │    ① POST /ai/chat { sessionId, message,                     │
│           │                     clientRequestId, agentName }             │
│           │                    │                                          │
│           │◄── ② SSE Stream ───┤                                          │
│           │    event: meta     │                                          │
│           │    event: thinking │                                          │
│           │    event: delta    │                                          │
│           │    event: done     │                                          │
│           │                    │                                          │
│           │    ③ POST /ai/chat/stop { sessionId, clientRequestId }       │
│           │                    │                                          │
└───────────┼────────────────────┼──────────────────────────────────────────┘
            │                    │
            ▼                    ▼
┌─────────────────────────────────────────────────────────────────────────┐
│                       Nginx / Gateway                                    │
│   proxy_buffering off;                                                   │
│   proxy_read_timeout 300s;                                               │
│   proxy_set_header X-Accel-Buffering no;                                │
└──────────────────────────────┬──────────────────────────────────────────┘
                               │
                               ▼
┌─────────────────────────────────────────────────────────────────────────┐
│                     Controller 层                                        │
│  ┌───────────────────────────────────────────────────────────────────┐  │
│  │  AiAgentController                                                 │  │
│  │   - POST /ai/chat           → chat()  (SSE)                        │  │
│  │   - POST /ai/chat/stop      → stopChat()                           │  │
│  │   - GET  /ai/sessions       → sessions()                           │  │
│  │   - GET  /ai/policy         → policy()                             │  │
│  │   - POST /ai/policy/accept  → acceptPolicy()                       │  │
│  └───────────────────────────────────────────────────────────────────┘  │
└──────────────────────────────┬──────────────────────────────────────────┘
                               │
                               ▼
┌─────────────────────────────────────────────────────────────────────────┐
│                     Service 层                                           │
│  ┌───────────────────────────────────────────────────────────────────┐  │
│  │  AiAgentService                                                    │  │
│  │   状态 Map:                                                        │  │
│  │    - emittersByRequest  Map<requestId, AiChatSseEmitter>          │  │
│  │    - runIdsByRequest    Map<requestId, runId>                      │  │
│  │    - tokensByRequest    Map<requestId, token>                      │  │
│  │    - memoryIdsByRequest Map<requestId, memoryId>                   │  │
│  │    - activeRequestByMemory Map<memoryId, requestId>                │  │
│  │                                                                    │  │
│  │   chat(): 注册 → 加锁 → 提交线程池 → 返回 emitter                  │  │
│  │   stop(): 按 clientRequestId 精准关闭                              │  │
│  └───────────────────────────────────────────────────────────────────┘  │
└──────────────────────────────┬──────────────────────────────────────────┘
                               │ chatExecutor.execute(...)
                               ▼
┌─────────────────────────────────────────────────────────────────────────┐
│                     Agent 编排层 (AiAgentRunner)                         │
│                                                                          │
│   步骤 0: send meta 帧 (requestId, sessionId)  ──► 前端存 id             │
│   步骤 1: history.appendUser (落库用户消息)                             │
│   步骤 2: userMemoryStore.recall (记忆召回)                             │
│   步骤 3: contextBuilder.build (构建 messages)                          │
│   步骤 4: agentRouter.route (选 Agent)                                  │
│   步骤 5: new AgentContext (运行上下文)                                 │
│   步骤 6: blackboardFactory.forRun (挂黑板)                             │
│   步骤 7: agent.execute / orchestrator.execute (执行)                    │
│   步骤 8: memoryExtractor.extract (异步记忆抽取)                         │
│                                                                          │
└─────┬──────────────────┬──────────────────┬──────────────────┬──────────┘
      │                  │                  │                  │
      ▼                  ▼                  ▼                  ▼
┌──────────┐  ┌──────────────┐  ┌──────────────┐  ┌──────────────────┐
│ 记忆子系统 │  │ Agent 子系统  │  │ 工具子系统    │  │ 黑板子系统        │
│          │  │              │  │              │  │                  │
│ UserMem  │  │ Planner      │  │ QueryKn      │  │ Blackboard       │
│ oryStore │  │ Knowledge    │  │ DateTool     │  │ (Redis 存储)     │
│ MemoryEx │  │ General      │  │              │  │                  │
│ tractor  │  │ Ticket       │  │              │  │                  │
│ RedisMem │  │ Synthesizer  │  │              │  │                  │
│ oryVec   │  │              │  │              │  │                  │
│          │  │ AgentLoop    │  │              │  │                  │
│          │  │ Orchestr    │  │              │  │                  │
│          │  │ ator         │  │              │  │                  │
└────┬─────┘  └──────┬───────┘  └──────┬───────┘  └────────┬─────────┘
     │               │                  │                   │
     └───────────────┴──────────────────┴───────────────────┘
                     │
                     ▼
        ┌──────────────────────────────────────┐
        │              Redis                   │
        │  ai:memory:exact:{md5}                │
        │  ai:memory:vec:{userId}               │
        │  ai:chat:lock:{memoryId}              │
        │  ai:chat:history:{sessionId}          │
        └──────────────────────────────────────┘
```

### 2.2 部署拓扑

```
┌──────────────┐
│  浏览器       │
└──────┬───────┘
       │ HTTP/HTTPS
       ▼
┌──────────────┐    ┌─────────────────┐
│  Nginx       │───►│  Spring Boot     │
│  (静态资源)   │    │  iam-backend-ai  │
└──────────────┘    └────────┬────────┘
                             │
                             ├──────────────────┐
                             │                  │
                             ▼                  ▼
                    ┌─────────────┐    ┌──────────────┐
                    │  Redis      │    │  阿里云百炼   │
                    │  (6.x)      │    │  LLM API     │
                    └─────────────┘    └──────────────┘
```

### 2.3 线程模型

| 线程池      | 名称                                 | 用途                           | 核心/最大     |
| -------- | ---------------------------------- | ---------------------------- | --------- |
| Web 容器   | `http-nio-8088-exec-*`             | 处理 HTTP 请求                   | Tomcat 默认 |
| AI 对话    | `ai-chat-*`                        | 执行 `AiAgentRunner.run`       | 4/16      |
| 记忆抽取     | `memory-*`                         | 执行 `MemoryExtractor.extract` | 2/2       |
| ForkJoin | `ForkJoinPool.commonPool-worker-*` | LangChain4j HTTP 流式          | JVM 默认    |
| 定时任务     | `knowledge-router-refresher`       | 刷新知识库路由                      | 1         |

---

## 第 3 章 模块详解

### 3.1 Controller 层

**文件**：`AiAgentController.java`

| 方法               | 路由                       | 用途          |
| ---------------- | ------------------------ | ----------- |
| `chat()`         | `POST /ai/chat`          | SSE 流式对话主入口 |
| `stopChat()`     | `POST /ai/chat/stop`     | 精准停止        |
| `sessions()`     | `GET /ai/sessions`       | 会话列表        |
| `policy()`       | `GET /ai/policy`         | 法律提示状态      |
| `acceptPolicy()` | `POST /ai/policy/accept` | 接受法律提示      |

**`ChatRequest` 结构**：

```java
public record ChatRequest(
        String sessionId,        // 会话 ID, 前端生成 UUID
        String message,          // 用户消息
        String knowledgeId,      // 知识库 ID (可选, 一般不走)
        String clientRequestId,  // ★ 前端生成 UUID, 用于 stop 精准匹配
        String agentName         // 指定 Agent (可选, "planner" 走多 Agent)
) {}
```

### 3.2 Service 层

**文件**：`AiAgentService.java`

**核心职责**：

1. **状态管理**：维护 5 个 Map，把 `requestId` 与 emitter、token、memoryId 关联。
2. **锁管理**：`memoryId` 粒度的分布式锁（`userId:sessionId`），防止同一会话并发。
3. **生命周期**：`onCompletion` / `onTimeout` / `onError` 回调清理状态。
4. **精准停止**：`stop()` 按 `clientRequestId` 找到 emitter 并关闭。

**关键设计**：

- **为什么用 `requestId` 而不是 `memoryId` 作为 Map key**？
  一个会话（`sessionId`）可能有多个请求并发（比如用户快速点了两次发送），旧版本用 `memoryId` 做 key，第二次会覆盖第一次，导致 stop 时无法精确匹配。改成 `requestId` 维度就能同时追踪多个。

- **`activeRequestByMemory` 的作用**？
  记录"某会话当前活跃的请求 ID"。新请求进来时先关掉旧请求，保证同一会话的 AI 回复不会串。

- **锁为什么要 5 秒 TTL**？
  防止进程崩溃后锁无法释放。正常执行流会在 `finally` 里主动 `unlock`。

### 3.3 编排层

**文件**：`AiAgentRunner.java`

**核心职责**：

1. **协议帧**：第一帧发送 `meta`，把 `requestId` 告知前端。
2. **消息落库**：`history.appendUser`。
3. **记忆召回**：`userMemoryStore.recall(userId, message, 5)`。
4. **上下文构建**：`contextBuilder.build(sessionId, message, memoryBlock)`。
5. **路由**：`agentRouter.route(...)` 选出 Agent。
6. **执行**：单 Agent 走 `agent.execute`，多 Agent 走 `orchestrator.execute`。
7. **记忆抽取**：异步提交 `memoryExtractor.extract`。
8. **锁释放**：`finally` 里 `redisLockHelper.unlock`。

**关键代码片段**：

```java
// 步骤 7: 执行 —— 判断依据: 前端显式指定 planner, 或路由返回 planner
String requested = request.agentName();
boolean isMulti = MULTI_AGENT.equalsIgnoreCase(
        requested == null ? "" : requested.trim());

if (isMulti || MULTI_AGENT.equals(agent.name())) {
    orchestrator.execute(ctx);
} else {
    agent.execute(ctx);
}
```

### 3.4 Agent 层

**抽象基类**：`AbstractAgent`

**四个核心方法**：

| 方法                   | 用途                            |
| -------------------- | ----------------------------- |
| `name()`             | Agent 唯一标识 (小写字符串)            |
| `rolePrompt()`       | 系统提示词 (注入到 messages 的 system) |
| `allowedToolNames()` | 该 Agent 可用的工具列表               |
| `intents()`          | 该 Agent 对应的意图列表 (供 Router 使用) |

**`AbstractAgent.execute()`** 的默认实现：

```
1. 把 rolePrompt 作为 system message 加到 messages 前面
2. 通过 allowedToolNames 过滤工具集
3. 调 AgentLoop 执行 (内部循环: LLM → 工具 → LLM → ...)
4. 直到 LLM 不再调工具, 输出最终答案
```

**Agent 一览**：

| Agent              | name()        | rolePrompt 摘要       | 工具                         | 意图                     |
| ------------------ | ------------- | ------------------- | -------------------------- | ---------------------- |
| `PlannerAgent`     | `planner`     | 任务规划器, 输出 JSON 任务列表 | 无                          | `plan`                 |
| `KnowledgeAgent`   | `knowledge`   | 企业知识库助手             | query_knowledge, date_tool | `approval`、`knowledge` |
| `GeneralAgent`     | `general`     | 通用助手                | date_tool                  | `general`              |
| `TicketAgent`      | `ticket`      | 工单处理                | ticket_tool                | `ticket`               |
| `SynthesizerAgent` | `synthesizer` | 汇总多个子任务结果           | 无                          | -                      |

### 3.5 记忆层

见 [第 5 章](#第-5-章-长期记忆子系统)。

### 3.6 工具层

**接口**：`AiTool`（LangChain4j 规范）

**已实现工具**：

| 工具                | 描述    | 参数                                         |
| ----------------- | ----- | ------------------------------------------ |
| `query_knowledge` | 知识库检索 | `question`, `knowledgeBaseId`(可选)          |
| `date_tool`       | 日期工具  | `action`, `startDate`, `endDate`, `zoneId` |

**工具调用流程**：

```
LLM 决策 ─► 返回 tool_calls ─► 后端执行工具 ─► 结果作为 tool message 回灌 ─► LLM 继续
```

### 3.7 黑板层

**接口**：`Blackboard`

**实现**：`RedisBlackboard`

**用途**：多 Agent 场景下，子 Agent 之间共享的中间结果/事实。单 Agent 也支持，用于存临时数据。

**Redis Key 结构**：

```
ai:blackboard:{boardId}:tasks:pending    List   待执行任务
ai:blackboard:{boardId}:tasks:done       Hash   已完成任务
ai:blackboard:{boardId}:results          Hash   中间结果
ai:blackboard:{boardId}:facts            List   事实
ai:blackboard:{boardId}:final            String 最终答案
```

---

## 第 4 章 核心业务流程

### 4.1 完整对话时序（含记忆）

```
 前端                    Service                Runner                Memory           Redis           LLM
  │                        │                      │                     │                │              │
  │─POST /ai/chat ────────►│                      │                     │                │              │
  │  {sessionId,           │                      │                     │                │              │
  │   message,             │                      │                     │                │              │
  │   clientRequestId}     │                      │                     │                │              │
  │                        │                      │                     │                │              │
  │                        │─emittersByRequest    │                     │                │              │
  │                        │   .put(reqId, em)    │                     │                │              │
  │                        │                      │                     │                │              │
  │                        │─redisLockHelper      │                     │                │              │
  │                        │   .tryLock(memId) ───┼────────────────────►│                │              │
  │                        │                      │                     │                │              │
  │                        │─chatExecutor         │                     │                │              │
  │                        │   .execute() ───────►│                     │                │              │
  │                        │                      │                     │                │              │
  │◄───SSE: meta ──────────┼──────────────────────│                     │                │              │
  │   {requestId, sid}     │                      │                     │                │              │
  │                        │                      │                     │                │              │
  │                        │                      │─history.appendUser─►│                │              │
  │                        │                      │                     │                │              │
  │                        │                      │─recall(userId, q)──►│                │              │
  │                        │                      │                     │─embed(q) ─────►│              │
  │                        │                      │                     │                │──HTTP───────►│
  │                        │                      │                     │                │◄──1024 维向量│
  │                        │                      │                     │─search(userId)►│              │
  │                        │                      │                     │  HGETALL       │              │
  │                        │                      │                     │◄──所有记忆──────│              │
  │                        │                      │                     │─算余弦───┐    │              │
  │                        │                      │                     │◄──────────┘    │              │
  │                        │                      │◄──memories[N]───────│                │              │
  │                        │                      │                     │                │              │
  │                        │                      │─build messages─────►│                │              │
  │                        │                      │  (含 memoryBlock)   │                │              │
  │                        │                      │                     │                │              │
  │                        │                      │─route(message) ────►│                │              │
  │                        │                      │◄──agent─────────────│                │              │
  │                        │                      │                     │                │              │
  │                        │                      │─agent.execute(ctx)──┼────────────────┼─────────────►│
  │◄───SSE: thinking ──────┼──────────────────────│                     │                │              │
  │◄───SSE: status ────────┼──────────────────────│                     │                │◄─流式返回────│
  │◄───SSE: tool ──────────┼──────────────────────│                     │                │              │
  │                        │                      │                     │                │              │
  │                        │                      │  (若 tool_call:                  │              │
  │                        │                      │   执行工具 → 回灌 → 继续)         │              │
  │                        │                      │                     │                │              │
  │◄───SSE: delta ─────────┼──────────────────────│                     │                │              │
  │◄───SSE: done ──────────┼──────────────────────│                     │                │              │
  │                        │                      │                     │                │              │
  │                        │                      │─extract(userId,turn)►│                │              │
  │                        │                      │  (异步)              │                │              │
  │                        │                      │                     │─LLM 抽取──────►│              │
  │                        │                      │                     │◄──JSON────────│              │
  │                        │                      │                     │─去重→embedding─►│              │
  │                        │                      │                     │─HSET──────────►│              │
  │                        │                      │                     │                │              │
  │                        │                      │─finally: unlock─────┼────────────────►│              │
  │                        │                      │                     │                │              │
  │                        │◄─onCompletion────────│                     │                │              │
  │                        │  cleanup(reqId)      │                     │                │              │
```

### 4.2 消息持久化时序

```
    Runner                  ChatHistoryStore              Redis
      │                            │                         │
      │─appendUser(msgId, sid,     │                         │
      │    userId, content)───────►│                         │
      │                            │─LPUSH ai:chat:history:  │
      │                            │    {sid} ──────────────►│
      │                            │                         │
      │                            │─HSET ai:chat:meta ─────►│
      │                            │    {msgId, role, ts}    │
      │                            │                         │
      │◄────────────────────────────│                         │
      │                            │                         │
      │  (Agent 执行, 生成回复)      │                         │
      │                            │                         │
      │─loadAssistant(asstMsgId)──►│                         │
      │                            │─HGET ai:chat:meta ────►│
      │                            │◄──assistant content──────│
      │◄──assistant message────────│                         │
      │                            │                         │
      │─extract(userId, turn)────► MemoryExtractor (异步)    │
```

### 4.3 异常处理流程

```
Runner.run() try-catch-finally:

try {
    步骤 1-8
} catch (Exception e) {
    log.error("AiAgentRunner 异常", e);
    // 注意: 不重新抛, 让 finally 释放锁
    // 但 emitter 会保持打开, 需要调用方处理
} finally {
    redisLockHelper.unlock(lockKey, token);
    // 保证锁一定释放
}
```

**关键点**：

- **异常吞掉**：Agent 内部错误不会导致 HTTP 500，前端已经收到部分 SSE 流。
- **emitter 补全**：如果异常发生在中间，emitter 可能未 `complete`，由 `onError` 回调兜底。
- **锁释放**：`finally` 保证不泄漏锁。

---

## 第 5 章 长期记忆子系统

### 5.1 子系统职责

| 职责           | 类                                                |
| ------------ | ------------------------------------------------ |
| 记忆项定义        | `MemoryItem`                                     |
| 命中记录         | `MemoryHit`                                      |
| 存储抽象         | `MemoryVectorStore`                              |
| 存储实现 (Redis) | `RedisMemoryVectorStore`                         |
| 统一入口         | `UserMemoryStore`                                |
| 异步抽取         | `MemoryExtractor`                                |
| 向量化          | `EmbeddingClient` / `LangChain4jEmbeddingClient` |

### 5.2 数据模型

**`MemoryItem`（Java record）**：

| 字段           | 类型             | 说明                                            |
| ------------ | -------------- | --------------------------------------------- |
| `id`         | `String`       | UUID，唯一标识                                     |
| `userId`     | `String`       | 所属用户 (隔离维度)                                   |
| `type`       | `String`       | `PREFERENCE` / `FACT` / `ENTITY` / `QUESTION` |
| `content`    | `String`       | 记忆内容                                          |
| `embedding`  | `List<Double>` | 1024 维向量                                      |
| `importance` | `double`       | 重要性 (0.0~1.0)                                 |
| `createdAt`  | `long`         | 创建时间戳                                         |
| `updatedAt`  | `long`         | 更新时间戳                                         |
| `invalid`    | `boolean`      | 是否失效 (软删除)                                    |

**记忆类型说明**：

| type         | 说明      | 示例               |
| ------------ | ------- | ---------------- |
| `PREFERENCE` | 用户偏好    | "用户偏好中文回答"       |
| `FACT`       | 稳定事实    | "用户姓名：李四；部门：财务部" |
| `ENTITY`     | 关注的具体对象 | "用户关注 UAR 系统"    |
| `QUESTION`   | 关注的主题   | "审核人信息"          |

### 5.3 存储结构

**Redis Key 一览**：

| Key                      | 类型     | 内容                           | TTL  |
| ------------------------ | ------ | ---------------------------- | ---- |
| `ai:memory:exact:{md5}`  | String | "1"                          | 30 天 |
| `ai:memory:vec:{userId}` | Hash   | `memoryId → MemoryItem JSON` | 无    |

**`ai:memory:vec:{userId}` 示例**：

```
HSET ai:memory:vec:laijf2
  "a1b2c3d4-..."  "{\"id\":\"a1b2...\",\"userId\":\"laijf2\",\"type\":\"FACT\",...}"
  "e5f6g7h8-..."  "{\"id\":\"e5f6...\",\"userId\":\"laijf2\",\"type\":\"QUESTION\",...}"
```

### 5.4 保存流程（详细）

**入口**：`UserMemoryStore.save(userId, type, content, importance)`

```
┌─────────────────────────────────────────────────────────┐
│  入参: userId, type, content, importance                 │
└─────────────────────┬───────────────────────────────────┘
                      ▼
┌─────────────────────────────────────────────────────────┐
│  ① 精确去重                                               │
│     md5 = md5(userId + "|" + type + "|" + content.trim()) │
│     key = "ai:memory:exact:" + md5                       │
│     SETNX key "1" EX 30d                                 │
│     ├── 返回 false → 已存在 → return                     │
│     └── 返回 true  → 继续                                │
└─────────────────────┬───────────────────────────────────┘
                      ▼
┌─────────────────────────────────────────────────────────┐
│  ② 向量化                                                 │
│     emb = embeddingClient.embed(content)                 │
│     ├── 失败或空 → return (降级, 不保存)                  │
│     └── 成功 → 得到 1024 维向量                            │
└─────────────────────┬───────────────────────────────────┘
                      ▼
┌─────────────────────────────────────────────────────────┐
│  ③ 语义去重                                               │
│     hits = vectorStore.search(userId, emb, 1)            │
│     if (hits[0].score >= 0.88) {                         │
│         vectorStore.update(hits[0].item.withHit())       │
│         → return (命中累加)                              │
│     }                                                     │
└─────────────────────┬───────────────────────────────────┘
                      ▼
┌─────────────────────────────────────────────────────────┐
│  ④ 真入库                                                 │
│     item = MemoryItem.of(userId, type, content, emb, imp)│
│     vectorStore.add(userId, item, emb)                   │
│       └── HSET ai:memory:vec:{userId} {item.id} {json}   │
└─────────────────────────────────────────────────────────┘
```

### 5.5 召回流程（详细）

**入口**：`UserMemoryStore.recall(userId, query, topK)`

```
┌─────────────────────────────────────────────────────────┐
│  入参: userId, query, topK=5                             │
└─────────────────────┬───────────────────────────────────┘
                      ▼
┌─────────────────────────────────────────────────────────┐
│  ① 向量化 query                                           │
│     emb = embeddingClient.embed(query)                   │
│     ├── 失败 → return []                                 │
│     └── 成功 → 1024 维向量                                 │
└─────────────────────┬───────────────────────────────────┘
                      ▼
┌─────────────────────────────────────────────────────────┐
│  ② 全量拉取该用户所有记忆                                  │
│     all = HGETALL ai:memory:vec:{userId}                 │
│     ├── 空 → log "库内 0 条" → return []                  │
│     └── N 条 → 继续                                       │
└─────────────────────┬───────────────────────────────────┘
                      ▼
┌─────────────────────────────────────────────────────────┐
│  ③ 逐条计算余弦相似度                                      │
│     for each item:                                       │
│         if (item.invalid) continue                       │
│         score = cosine(queryEmb, item.embedding)         │
│         hits.add(new MemoryHit(item, score))             │
│     hits.sort(by score desc)                             │
└─────────────────────┬───────────────────────────────────┘
                      ▼
┌─────────────────────────────────────────────────────────┐
│  ④ 阈值过滤 + 截断                                        │
│     result = hits.stream()                               │
│              .filter(h -> h.score >= 0.45)               │
│              .map(MemoryHit::item)                       │
│              .limit(topK)                                │
│              .toList()                                   │
│                                                          │
│     log "[memory-recall] 命中={N}, scores=[...]"          │
└─────────────────────┬───────────────────────────────────┘
                      ▼
┌─────────────────────────────────────────────────────────┐
│  返回 List<MemoryItem>                                   │
└─────────────────────────────────────────────────────────┘
```

### 5.6 记忆抽取 Prompt 现状

**当前 Prompt（在 `SummaryUtil.extractLongTermMemoriesAsList` 里）**：

```
从以下对话中抽取值得长期记住的用户信息。
只输出 JSON 数组，不要任何解释。格式：
[{"type":"PREFERENCE|FACT|ENTITY|QUESTION","content":"...","importance":0.0~1.0}]
没有值得记住的信息则输出 []。

判断标准：
- PREFERENCE: 用户偏好（语言、风格、习惯）
- FACT: 关于用户的稳定事实（部门、职位、常用系统）
- ENTITY: 用户关注的具体对象（项目名、系统名）
- QUESTION: 用户询问或持续关注的业务主题（如审核流程、权限管理、审批规则等）

注意：
1. 即使用户只问了一次，只要是**具体业务主题**（不是寒暄、不是闲聊），
   就应抽取为 QUESTION 类型，content 写清楚"用户关注 XXX 主题"。
2. 只有纯粹的寒暄（如"你好""谢谢"）和与业务无关的闲聊才不抽取。
3. 不要把助手回答的内容当作记忆，只记"用户关注什么"。
```

**问题**：

- LLM 每次生成的 `content` 措辞略有差异（`"用户关注 审核人信息 主题"` vs `"用户关注审核人查询主题"`）
- 同义内容因措辞不同 → 精确去重失效 → 依赖语义去重（阈值 0.88）

**建议优化**：

```
... (省略前面的规则)

关键格式要求:
- FACT: 抽取成 "key:value; key:value" 结构化格式
  示例: {"type":"FACT","content":"姓名:李四; 部门:财务部","importance":0.9}
- QUESTION: content 直接写主题本身, 不要加 "用户关注 XX 主题" 包装
  示例: {"type":"QUESTION","content":"审核人信息","importance":0.7}
```

---

## 第 6 章 多 Agent 编排子系统

### 6.1 触发条件

`AiAgentRunner` 中：

```java
String requested = request.agentName();
boolean isMulti = "planner".equalsIgnoreCase(
        requested == null ? "" : requested.trim());

if (isMulti || "planner".equals(agent.name())) {
    orchestrator.execute(ctx);      // 多 Agent
} else {
    agent.execute(ctx);              // 单 Agent
}
```

**触发多 Agent 的两种方式**：

| 方式                         | 优先级 | 说明          |
| -------------------------- | --- | ----------- |
| 前端指定 `agentName="planner"` | 高   | 直通，绕过路由     |
| 路由返回 `PlannerAgent`        | 中   | 靠意图判定       |
| 兜底：意图是 `plan`              | 低   | Router 内部规则 |

### 6.2 编排流程

```
Orchestrator.execute(ctx)
  │
  ├── 阶段 1: 规划 (PlannerAgent)
  │     ├── 把用户原始问题作为输入
  │     ├── LLM 输出 JSON: [{"agentName":"knowledge","instruction":"..."}]
  │     └── 解析为 List<Task>
  │
  ├── 阶段 2: 执行 (子 Agent)
  │     ├── 遍历 Tasks
  │     ├── 每个 Task 新建子 ctx (instruction 作为 user message)
  │     ├── agentRouter.route(task.agentName) 得到具体 Agent
  │     ├── agent.execute(subCtx)
  │     └── 收集结果到 blackboard
  │
  └── 阶段 3: 综合 (SynthesizerAgent)
        ├── 拉取 blackboard 所有子任务结果
        ├── 拼成 synthesized context
        ├── LLM 生成最终答案
        └── 流式返回给前端
```

### 6.3 PlannerAgent 提示词

```java
@Override public String rolePrompt() {
    return """
        你是任务规划器。把用户问题拆成若干子任务。
        每个子任务必须指定 agentName（knowledge / ticket / general）和 instruction。
        只输出 JSON 数组，不要任何解释、不要 markdown 代码块：
        [{"agentName":"knowledge","instruction":"..."}]
        如果问题简单，输出只含一个元素的数组。
        """;
}
```

**约束**：

- `allowedToolNames() = List.of()`：不调工具，纯生成 JSON。
- `maxSteps() = 1`：只跑一轮 LLM。
- `streamToUser() = false`：不流式给用户（内部使用）。

### 6.4 触发多 Agent 的示例问题

| 问题                        | 结果                    |
| ------------------------- | --------------------- |
| "帮我把这个任务拆成几步"             | ✅ 触发                  |
| "我需要一个完整方案，分步骤说明"         | ✅ 触发                  |
| "查下财务审批流程，再找相关制度，最后整理成表格" | ✅ 大概率触发               |
| "审核人信息"                   | ❌ 走 knowledge 单 Agent |
| "今天几号"                    | ❌ 走 general 单 Agent   |
| 前端传 `agentName="planner"` | ✅ 必触发                 |

---

## 第 7 章 精准中断子系统

### 7.1 设计目标

**问题**：传统中断方案下，用户点"停止"，只能：

- 前端 `abort()` 断开连接；
- 但后端 LLM 还在跑，浪费 token。

**本系统方案**：

1. 前端每次 chat 生成唯一的 `clientRequestId`
2. 后端用它做 emitter Map 的 key
3. SSE 第一帧 `meta` 把 `requestId` 下发
4. 用户点停止 → 前端带 `clientRequestId` 调 stop
5. 后端精准匹配 → 关闭对应 emitter + 释放锁

### 7.2 关键数据结构

```java
Map<String, AiChatSseEmitter> emittersByRequest;   // requestId → emitter
Map<String, String> tokensByRequest;               // requestId → lock token
Map<String, String> memoryIdsByRequest;            // requestId → memoryId
Map<String, String> activeRequestByMemory;         // memoryId → 当前活跃的 requestId
```

**为什么不用 `memoryId` 做 key**：

- 同一会话可能有多个请求并发（用户快速连发）
- `memoryId` 会被覆盖
- `requestId` 精确定位

### 7.3 stop 流程

```
前端                                    后端
 │                                       │
 │  (SSE 连接进行中)                       │
 │                                       │
 │  用户点击"停止"                          │
 │                                       │
 │──POST /ai/chat/stop ─────────────────►│
 │  {sessionId, clientRequestId}         │
 │                                       │
 │                                       │  AiAgentService.stop()
 │                                       │    ├─ memoryId = userId:sessionId
 │                                       │    ├─ emitter = emittersByRequest.remove(reqId)
 │                                       │    ├─ if (emitter == null) → 忽略
 │                                       │    ├─ emitter.send("error", {message})
 │                                       │    ├─ emitter.complete()
 │                                       │    ├─ token = tokensByRequest.remove(reqId)
 │                                       │    ├─ redisLockHelper.unlock(lockKey, token)
 │                                       │    └─ 清理其他 Map
 │                                       │
 │◄──SSE 流关闭 ──────────────────────────│
 │                                       │
```

### 7.4 幂等性

- stop 找不到 emitter → 忽略，不报错。
- stop 的 `clientRequestId` 为空 → 忽略。
- 请求已经结束 → 前端不应再调 stop（前端 `finally` 里清 `currentRequestId`）。

---

## 第 8 章 SSE 协议规范

### 8.1 SSE 帧格式

```
event: {eventName}
data: {jsonPayload}
\n
```

（每个事件之间用空行分隔）

### 8.2 事件清单

| event       | payload                  | 触发时机   | 前端处理         |
| ----------- | ------------------------ | ------ | ------------ |
| `meta`      | `{requestId, sessionId}` | 请求开始   | 存 requestId  |
| `start`     | `{sessionId}`            | 会话初始化  | 更新 sessionId |
| `status`    | `{text}`                 | 状态更新   | 显示在思考框       |
| `tool`      | `{name}`                 | 工具调用   | 显示工具名        |
| `thinking`  | `{text, reset?}`         | 推理流式输出 | 追加到思考正文      |
| `delta`     | `{text, reset?}`         | 答案流式输出 | 追加到答案        |
| `km_source` | `{...}`                  | 知识库来源  | 展示引用         |
| `done`      | `{answer, interrupted?}` | 完成     | 收尾           |
| `error`     | `{message}`              | 错误     | 显示错误         |

### 8.3 `meta` 事件详解

**用途**：把后端本次请求的 `requestId` 告知前端，让前端能在 stop 时带上正确的 id。

**payload**：

```json
{
  "requestId": "abc-123-def-456",
  "sessionId": "e12ac37c-0710-4d32-ac09-dc00cd856011"
}
```

**前端接收**：

```javascript
onMeta: (data) => {
  if (data?.requestId) currentRequestId = data.requestId;
  if (data?.sessionId) currentConversation.value.sessionId = data.sessionId;
}
```

### 8.4 `done` 事件详解

**payload**：

```json
{
  "answer": "完整答案文本...",
  "interrupted": false
}
```

**`interrupted` 的意义**：如果是 true，说明这次是服务端主动中断（超时等），前端应展示"已停止"标记。

---

## 第 9 章 Redis 数据结构设计

### 9.1 Key 命名规范

```
ai:{module}:{submodule}:{identifier}
```

| 模块  | Key 前缀           | 用途         |
| --- | ---------------- | ---------- |
| 记忆  | `ai:memory:`     | 长期记忆       |
| 会话  | `ai:chat:`       | 对话相关       |
| 黑板  | `ai:blackboard:` | 多 Agent 黑板 |
| 锁   | `ai:chat:lock:`  | 分布式锁       |

### 9.2 完整 Key 清单

| Key                                     | 类型     | 内容              | TTL   | 说明     |
| --------------------------------------- | ------ | --------------- | ----- | ------ |
| `ai:memory:exact:{md5}`                 | String | "1"             | 30 天  | 精确去重标记 |
| `ai:memory:vec:{userId}`                | Hash   | memoryId → JSON | 无     | 用户全部记忆 |
| `ai:chat:lock:{userId}:{sessionId}`     | String | token           | 5s    | 会话锁    |
| `ai:chat:history:{sessionId}`           | List   | 消息 JSON         | 无     | 会话历史   |
| `ai:blackboard:{boardId}:tasks:pending` | List   | AgentTask JSON  | 30min | 待执行任务  |
| `ai:blackboard:{boardId}:tasks:done`    | Hash   | taskId → result | 30min | 已完成任务  |
| `ai:blackboard:{boardId}:results`       | Hash   | k → v           | 30min | 中间结果   |
| `ai:blackboard:{boardId}:facts`         | List   | String          | 30min | 事实列表   |
| `ai:blackboard:{boardId}:final`         | String | 答案              | 30min | 最终答案   |

### 9.3 内存估算

假设 1000 个用户，每个用户平均 50 条记忆：

| 数据                 | 大小                        |
| ------------------ | ------------------------- |
| 单条 MemoryItem JSON | ~5 KB (含 1024 维 float 数组) |
| 单用户记忆              | 250 KB                    |
| 1000 用户            | 250 MB                    |
| 精确去重标记             | 50 KB                     |

**建议**：Redis `maxmemory` 设 2 GB+，`maxmemory-policy allkeys-lru`。

---

## 第 10 章 完整代码清单

（此处省略，同上一版文档 6.1~6.13 节内容。代码部分完全一致。）

### 10.14 补充：`ChatHistoryStore`（关键接口）

```java
public interface ChatHistoryStore {
    boolean exists(String sessionId);
    void assertOwner(String sessionId, String userId);
    long nextMessageId();
    void appendUser(long messageId, String sessionId, String userId, String content);
    String loadAssistant(long assistantMessageId);
    List<AiChatSessionResponse> sessions(String userId);
    AiChatPolicyResponse policyStatus(String userId);
    void acceptPolicy(String userId);
}
```

### 10.15 补充：`ContextBuilder`

```java
public interface ContextBuilder {
    /**
     * 构建 messages
     * @param sessionId   会话 ID
     * @param userMessage 本轮问题
     * @param memoryBlock 记忆块 (可为空)
     */
    List<Message> build(String sessionId, String userMessage, String memoryBlock);
}
```

**预期实现**：

```java
messages = [
    system: "<memoryBlock 不为空时: [关于用户的已知信息]\n" + memoryBlock + ">",
    ... 历史对话（最近 N 轮）,
    user: userMessage
]
```

### 10.16 补充：`AgentRouter`

```java
public interface AgentRouter {
    Agent route(RoutingRequest request);
}

public record RoutingRequest(
        String message,
        String userId,
        String sessionId,
        String agentName,      // 前端指定, 可为 null
        Set<String> roleSet
) {}
```

**预期实现（HybridAgentRouter）**：

```
1. if (agentName != null) → 按 name 直通
2. 意图识别 (关键词表 + LLM)
3. 按意图映射到 Agent
4. 兜底 general
```

---

## 第 11 章 关键问题与修复记录

### 11.1 问题全景表

| #   | 问题                            | 严重度     | 根因                                        | 修复                                              |
| --- | ----------------------------- | ------- | ----------------------------------------- | ----------------------------------------------- |
| 1   | `@RequiredArgsConstructor` 报错 | 🔴 编译失败 | `RedisBlackboard` 手写构造器与 Lombok 冲突        | 删除手写构造器                                         |
| 2   | Bean 缺失: `MemoryVectorStore`  | 🔴 启动失败 | 只有接口无实现                                   | 新建 `RedisMemoryVectorStore`                     |
| 3   | 召回始终 0 条                      | 🔴 功能失效 | `RedisMemoryStore` 依赖 RediSearch 索引，索引未创建 | 改用 `UserMemoryStore` + `RedisMemoryVectorStore` |
| 4   | 老链路还在走                        | 🔴 功能失效 | `AiAgentRunner` 注入 `MemoryStore`          | 注入改为 `UserMemoryStore`                          |
| 5   | 短文本召回 0 条                     | 🟡 体验   | 相似度 0.4~0.6 < 阈值 0.55                     | 阈值降至 0.45                                       |
| 6   | 记忆重复写入                        | 🟡 数据质量 | LLM 抽取措辞不稳定                               | 加语义去重 (≥0.88)                                   |
| 7   | `MemoryItem.withHit()` 缺失     | 🔴 编译失败 | record 不自动生成                              | 手动添加                                            |
| 8   | stop 不生效                      | 🔴 功能失效 | Map key 不一致                               | 统一用前端传的 `requestId`                             |
| 9   | 前端拿不到 requestId               | 🟡 体验   | 未下发 meta 帧                                | Runner 首帧发 meta                                 |
| 10  | 应用关闭时 Redis 报错                | 🟢 副作用  | 定时任务与容器销毁竞争                               | 加 `@PreDestroy`                                 |

### 11.2 关键问题详解

#### 问题 #3：召回始终 0 条

**现象**：

```
io.lettuce.core.RedisCommandExecutionException: SEARCH_INDEX_NOT_FOUND
Index not found: idx:ai:memory
    at RedisMemoryStore.searchByUser(RedisMemoryStore.java:257)
```

**根因分析**：

`RedisMemoryStore.searchByUser` 内部使用 RediSearch 的 `FT.SEARCH` 命令：

```
FT.SEARCH idx:ai:memory "*=>[KNN 5 @embedding $vec AS score]"
    PARAMS 2 vec {binary}
    SORTBY score
```

但索引 `idx:ai:memory` **从未被创建**。RediSearch 是 Redis 的一个扩展模块，不是默认安装的。

**错误处理**：异常被 `catch` 后返回 `List.of()`，导致每次都返回 0 条。

**修复方案**：

改走 `UserMemoryStore` + `RedisMemoryVectorStore`，用**应用层余弦相似度**，不依赖 RediSearch 模块。

#### 问题 #5：短文本召回 0 条

**现象**：

- 存：`"用户关注审核人信息主题"` (12 字)
- 查：`"审核人信息"` (5 字)
- 相似度：0.45~0.55 (短文本 embedding 相似度普遍偏低)
- 阈值 0.55 → 卡住召不回

**解决**：

1. 阈值降到 0.45
2. 建议优化记忆抽取 Prompt，让 `content` 是纯主题词

#### 问题 #8：stop 不生效

**现象**：

```
>>> 停止会话: memoryId=laijf2:xxx, clientRequestId=b97a18f4
stop() clientRequestId 不匹配，跳过关闭: stored=cd79d513, requested=b97a18f4
```

**根因**：

- 第 1 轮 chat：前端生成 `clientRequestId=b97a18f4`
- 第 2 轮 chat：前端生成 `clientRequestId=cd79d513` → 覆盖了 Map
- 用户点 stop：前端仍用旧 id `b97a18f4`
- 后端比对：`stored=cd79d513 != requested=b97a18f4` → 拒绝

**修复**：

1. Map key 从 `memoryId` 改成 `requestId`（不同请求独立存）
2. 前端 `finally` 里才清 `currentRequestId`
3. 后端首帧发 `meta`，前端用最新 id

---

## 第 12 章 测试与验收

### 12.1 记忆功能测试

#### 12.1.1 跨会话召回（核心验收）

**步骤**：

1. **会话 A**：发送 "我叫李四，在财务部工作"
2. 等待 3 秒（异步抽取完成）
3. **新建会话 B**：直接问 "我是谁？我在哪个部门？"
4. **预期**：AI 回答 "李四 / 财务部"

**验收日志**（第 2 次提问）：

```
[memory-vec] search: userId=laijf2, 库内 N 条, 返回 topN
[memory-recall] userId=laijf2, query=我是谁？我在哪个部门？, topK=5, 命中=1, scores=[0.7xx]
>>> 记忆召回: userId=laijf2, 召回 1 条, 内容=[用户姓名：李四，所属部门：财务部]
```

**prompt 验证**：

发送给 LLM 的 messages 里应有：

```json
{
  "role": "system",
  "content": "[关于用户的已知信息]\n- [FACT] 用户姓名：李四，所属部门：财务部"
}
```

#### 12.1.2 精确去重

**步骤**：

1. 说 "我叫李四"（2 次，content 完全一致）
2. 看第二次的日志

**预期**：

```
[memory-save] 精确重复, 跳过: userId=laijf2, type=FACT, content=用户姓名：李四
```

#### 12.1.3 语义去重

**步骤**：

1. 说 "我叫李四"
2. 说 "我是李四"

**预期**（相似度 ≥ 0.88）：

```
[memory-save] 语义重复(score=0.91), 累加命中: userId=laijf2, content=...
```

**验证**：`HLEN ai:memory:vec:laijf2` 只增加 1 条。

### 12.2 Stop 功能测试

**步骤**：

1. 发一个会耗时的请求（如 "总结公司权限管理制度"）

2. 前端 Network 面板查看 chat 响应，第一帧应是：
   
   ```
   event: meta
   data: {"requestId":"abc-123","sessionId":"..."}
   ```

3. 生成中点"停止"

4. 前端发 stop 请求，body：
   
   ```json
   {"sessionId":"...","clientRequestId":"abc-123"}
   ```

5. **验收日志**：
   
   ```
   >>> 停止会话: memoryId=laijf2:xxx, clientRequestId=abc-123
   >>> 已关闭 emitter: requestId=abc-123
   ```
   
   **不应出现**：`stop() 未找到 emitter, 忽略`

### 12.3 多 Agent 测试

**步骤 1：前端指定**

前端传 `agentName: "planner"` → 必然触发。

**步骤 2：自然语言触发**

```
帮我规划一下新系统上线的步骤，分几步说明
```

**验收日志**：

```
>>> 路由结果: agent.name()=[planner], 前端指定=[]
>>> 进入多 Agent 编排, runId=...
```

### 12.4 回归测试清单

| #   | 测试项   | 预期            |
| --- | ----- | ------------- |
| 1   | 单轮对话  | SSE 流式正常，答案正确 |
| 2   | 多轮对话  | 历史上下文生效       |
| 3   | 知识库问答 | 命中知识库，答案合理    |
| 4   | 记忆保存  | 日志出现 `成功保存记忆` |
| 5   | 跨会话召回 | 新会话能答出上会话信息   |
| 6   | 停止生成  | 精准停止，不再输出     |
| 7   | 连发两条  | 旧请求被替换，新请求正常  |
| 8   | 会话列表  | 显示所有会话        |
| 9   | 删除会话  | 删除成功，切到新会话    |
| 10  | 重命名会话 | 标题更新          |

---

## 第 13 章 配置说明

### 13.1 application.yml

```yaml
server:
  port: 8088

spring:
  application:
    name: iam-backend-ai

  # Redis 配置
  data:
    redis:
      host: ${REDIS_HOST:localhost}
      port: ${REDIS_PORT:6379}
      password: ${REDIS_PASSWORD:}
      database: ${REDIS_DB:0}
      lettuce:
        pool:
          max-active: 16
          max-idle: 8
          min-idle: 2

  # 线程池
  task:
    execution:
      pool:
        core-size: 4
        max-size: 16
        queue-capacity: 200
        keep-alive: 60s
      thread-name-prefix: ai-chat-

# LangChain4j 配置
langchain4j:
  open-ai:
    chat-model:
      base-url: ${LLM_BASE_URL}
      api-key: ${LLM_API_KEY}
      model-name: kimi-k2.6
      temperature: 0.7
      timeout: 120s
      log-requests: true
      log-responses: true
    embedding-model:
      base-url: ${LLM_BASE_URL}
      api-key: ${LLM_API_KEY}
      model-name: text-embedding-v3
      timeout: 30s

# 记忆模块配置
memory:
  recall:
    min-score: 0.45          # 召回阈值
    top-k: 5                 # 召回条数
  semantic-dup:
    score: 0.88              # 语义去重阈值
  exact-ttl-days: 30         # 精确去重 key TTL

# 日志配置
logging:
  level:
    root: INFO
    com.lenovo.ai.memory: DEBUG
    com.lenovo.ai.service: DEBUG
    com.lenovo.ai.agent: DEBUG
    dev.langchain4j: INFO
```

### 13.2 Nginx 关键配置

```nginx
location /itsc/ai/ {
    proxy_pass http://backend;
    proxy_http_version 1.1;
    proxy_set_header Connection "";
    proxy_set_header Host $host;
    proxy_set_header X-Real-IP $remote_addr;

    # SSE 关键配置
    proxy_buffering off;
    proxy_cache off;
    proxy_read_timeout 300s;
    proxy_send_timeout 300s;
    chunked_transfer_encoding on;
    proxy_set_header X-Accel-Buffering no;
}
```

### 13.3 环境变量

| 变量               | 说明          | 示例                          |
| ---------------- | ----------- | --------------------------- |
| `REDIS_HOST`     | Redis 地址    | localhost                   |
| `REDIS_PORT`     | Redis 端口    | 6379                        |
| `REDIS_PASSWORD` | Redis 密码    | -                           |
| `LLM_BASE_URL`   | 大模型 API 地址  | https://ws-xxx.aliyuncs.com |
| `LLM_API_KEY`    | 大模型 API Key | sk-xxx                      |

---

## 第 14 章 性能与容量

### 14.1 单次对话耗时分解

| 阶段                   | 平均耗时        |
| -------------------- | ----------- |
| 记忆召回（embedding + 余弦） | 200~300 ms  |
| 上下文构建                | <10 ms      |
| 路由                   | <50 ms      |
| 首 token（LLM 首字返回）    | 500~1500 ms |
| 完整对话（含工具调用）          | 5~30 s      |
| 异步记忆抽取               | 3~5 s（不阻塞）  |

### 14.2 容量评估

| 资源        | 估算          | 说明              |
| --------- | ----------- | --------------- |
| 单用户记忆数    | 50~200 条    | 长期积累            |
| 单条记忆 JSON | ~5 KB       | 含 1024 维向量      |
| 1000 用户记忆 | 250 MB~1 GB | Redis 内存        |
| 并发对话      | 16          | `ai-chat` 线程池上限 |
| 单机 QPS    | 5~10        | LLM 调用的瓶颈       |

### 14.3 优化建议

| 场景             | 优化                      |
| -------------- | ----------------------- |
| 单用户记忆 > 2000 条 | 引入 RediSearch / Milvus  |
| 并发量 > 100 QPS  | 引入 LLM 请求队列 + 限流        |
| 记忆召回慢          | 加 Redis 缓存层 (热数据)       |
| 记忆存储大          | embedding 用 float16 或量化 |

---

## 第 15 章 后续优化路线图

### 15.1 P0（1 周内）

- [ ] 优化 `MemoryExtractor` 抽取 Prompt，FACT 结构化
- [ ] `UserMemoryStore.SEMANTIC_DUP_SCORE` 从 0.88 降到 0.85
- [ ] 加监控指标：召回命中率、保存成功率、stop 成功率

### 15.2 P1（1 个月内）

- [ ] 记忆管理接口：`GET /ai/memory`、`DELETE /ai/memory/{id}`
- [ ] 记忆衰减任务 `MemoryDecayJob`
- [ ] 引入关键词兜底召回
- [ ] 前端 UI：记忆展示、修改、删除

### 15.3 P2（3 个月内）

- [ ] 引入 RediSearch / Milvus 替换应用层遍历
- [ ] 多路召回（向量 + 关键词 + 规则）
- [ ] 记忆分层（短期 / 长期）
- [ ] 用户画像自动构建

### 15.4 P3（远期）

- [ ] 主动记忆（从用户行为中学习）
- [ ] 多租户隔离
- [ ] 记忆图谱
- [ ] 端到端可观测性

---

## 附录 A：关键类依赖关系

```
AiAgentController
    └── AiAgentService
            ├── AiChatExecutor
            ├── RedisLockHelper
            ├── ChatHistoryStore
            └── AiAgentRunner
                    ├── ContextBuilder
                    ├── UserMemoryStore ★
                    │       ├── StringRedisTemplate
                    │       ├── EmbeddingClient
                    │       │       └── LangChain4jEmbeddingClient
                    │       └── MemoryVectorStore
                    │               └── RedisMemoryVectorStore ★
                    ├── MemoryExtractor
                    │       ├── UserMemoryStore (同上)
                    │       └── SummaryUtil
                    ├── AgentRouter
                    │       └── HybridAgentRouter
                    ├── BlackboardFactory
                    ├── Orchestrator
                    └── Agent (多实现)
                            ├── PlannerAgent ★ (name="planner")
                            ├── KnowledgeAgent (name="knowledge")
                            ├── GeneralAgent (name="general")
                            ├── TicketAgent (name="ticket")
                            └── SynthesizerAgent (name="synthesizer")
```

## 附录 B：故障排查速查表

| 现象                    | 排查步骤                                                                                                                   |
| --------------------- | ---------------------------------------------------------------------------------------------------------------------- |
| **应用启动失败**            | 1. 搜 `BeanCreationException`<br>2. 检查 `@Component` 是否加上<br>3. 检查构造器参数是否都能注入                                            |
| **召回 0 条**            | 1. 看 `[memory-vec] search` 的"库内 N 条"<br>2. 若 0 → 保存失败（看 save 日志）<br>3. 若 N > 0 → 看 scores 判断阈值                         |
| **记忆重复**              | 1. `HLEN ai:memory:vec:{userId}` 数一下<br>2. 超过预期 → 检查 `SEMANTIC_DUP_SCORE`<br>3. 看 save 日志有没有"语义重复"                     |
| **stop 不生效**          | 1. 前端 Network 看 chat 首帧是不是 `event: meta`<br>2. 看 stop 请求的 `clientRequestId` 是否等于 meta 里的<br>3. 后端日志搜 "clientRequestId" |
| **planner 不触发**       | 1. 看 `>>> 路由结果: agent.name()=[...]`<br>2. 是否等于 "planner"<br>3. 前端传 `agentName="planner"` 测试                            |
| **SSE 断流**            | 1. 检查 Nginx `proxy_buffering off`<br>2. `proxy_read_timeout` ≥ 300s<br>3. `X-Accel-Buffering no`                       |
| **LLM 超时**            | 1. 检查 `langchain4j.open-ai.chat-model.timeout`<br>2. 单次 max-tokens 是否过大<br>3. 网络到阿里云的延迟                                |
| **记忆抽取慢**             | 1. 检查 `memory-*` 线程池大小<br>2. LLM 抽取耗时（正常 3~5s）<br>3. 是否与主流程竞争资源                                                        |
| **Redis 报 destroyed** | 1. 应用关闭时定时任务仍在跑<br>2. 加 `@PreDestroy` 关闭 scheduler                                                                     |
| **embedding 失败**      | 1. 检查阿里云 API Key<br>2. 检查模型名 text-embedding-v3<br>3. 单条文本是否过长                                                          |

## 附录 C：术语表

| 术语                  | 说明                                             |
| ------------------- | ---------------------------------------------- |
| **Agent**           | 智能体，封装了 role prompt、可用工具、执行逻辑                  |
| **AgentLoop**       | 单 Agent 的执行循环（LLM → 工具 → LLM → ...）            |
| **Orchestrator**    | 多 Agent 编排器，负责 Planner + 子 Agent + Synthesizer |
| **Blackboard**      | 黑板，多 Agent 场景下共享中间结果的存储                        |
| **memoryId**        | `userId:sessionId` 的组合，会话唯一标识                  |
| **clientRequestId** | 前端生成的 UUID，用于 stop 精准取消                        |
| **memoryBlock**     | 召回的记忆拼成的文本块，注入到 prompt                         |
| **SSE**             | Server-Sent Events，服务器推送事件                     |
| **HNSW**            | 一种高效的近似最近邻检索算法                                 |
| **RediSearch**      | Redis 的向量检索扩展模块                                |
| **精确去重**            | 完全相同的 content 直接跳过                             |
| **语义去重**            | 相似度超过阈值视为同一条，累加命中                              |
| **冷启动**             | 用户第一次提问，无历史记忆                                  |

---

## 文档更新记录

| 版本  | 日期         | 更新内容                       | 更新人   |
| --- | ---------- | -------------------------- | ----- |
| 1.0 | 2026-10-12 | 初版                         | laifo |
| 1.1 | 2026-10-12 | 详细化，补充第 5/6/7/8/9 章、附录 B/C | laifo |

---

**文档结束。**

> 本文档为 iam-backend-ai 项目的完整开发文档，涵盖系统架构、模块设计、核心流程、代码清单、问题修复、测试方案、配置说明等。
> 如有疑问或补充，请更新此文档并提交 PR。
> 维护者：laifo
