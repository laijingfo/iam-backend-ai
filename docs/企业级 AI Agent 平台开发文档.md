# 企业级 AI Agent 平台开发文档

> 基于对 `iam-backend-ai` 项目全部代码的深度审查编写
> 版本：v1.0 | 日期：2026-10-14

---

## 目录

1. [项目概述](#一项目概述)
2. [整体架构](#二整体架构)
3. [模块设计](#三模块设计)
4. [核心流程](#四核心流程)
5. [数据模型](#五数据模型)
6. [接口协议](#六接口协议)
7. [配置说明](#七配置说明)
8. [已知问题与技术债](#八已知问题与技术债)
9. [演进路线](#九演进路线)

---

## 一、项目概述

### 1.1 项目定位

`iam-backend-ai` 是一个**企业级 AI Agent 平台**，基于 Spring Boot + LangChain4j 构建，面向企业内部提供智能问答、知识库检索、工单创建等能力。

**核心特性**：

| 特性         | 说明                                                   |
| ---------- | ---------------------------------------------------- |
| 多 Agent 协作 | Router → Agent → Orchestrator，支持单 Agent / 多 Agent 编排 |
| 意图路由       | 关键词 + LLM 双级路由，支持显式指定                                |
| 工具调用       | Function Calling + 工具白名单 + 角色权限 + 二次确认               |
| 长期记忆       | RediSearch 向量检索 + 语义去重 + 衰减机制                        |
| 知识库 RAG    | 向量路由 + 精准问答 + 模板注册                                   |
| Token 计费   | 多维用量统计（用户/模型/日/月）+ 成本计算 + 配额控制                       |
| 流式输出       | SSE 实时推送 delta/done/tool 事件                          |
| 会话管理       | 历史持久化 + 摘要压缩 + 上下文裁剪                                 |

### 1.2 技术栈

```
┌──────────────────────────────────────────────┐
│  Web 层     │ Spring MVC + SSE                │
├──────────────────────────────────────────────┤
│  业务层      │ Agent / Service / Memory         │
├──────────────────────────────────────────────┤
│  LLM 层      │ LangChain4j (OpenAI 兼容协议)     │
├──────────────────────────────────────────────┤
│  存储层      │ Redis (RediSearch/Lettuce)        │
│              │ 待接入 PG (历史/配置)              │
├──────────────────────────────────────────────┤
│  监控层      │ Micrometer + Prometheus           │
└──────────────────────────────────────────────┘
```

**依赖版本**：

- JDK 17+
- Spring Boot 3.x
- LangChain4j（`StreamingChatModel` / `ChatModel` / `EmbeddingModel`）
- Redis 7.x（RediSearch 模块）
- Lettuce（原生连接用于 RediSearch 协议）
- Conscrypt（TLS 1.3）

### 1.3 部署形态

- **单实例**：当前内存态（`InMemoryChatHistoryStore`、`AiAgentService` 的 Map）只能单实例运行
- **多实例**：需先修复 P0 级数据一致性问题（见第八章）
- **容器化**：建议 Docker + K8s，Redis 独立部署

---

## 二、整体架构

### 2.1 分层架构

```
┌───────────────────────────────────────────────────────────┐
│                      前端 / 客户端                          │
│               (SSE 长连接 / REST 请求)                      │
└────────────────────────┬──────────────────────────────────┘
                         │
                         ▼
┌───────────────────────────────────────────────────────────┐
│  接入层  service/AiAgentService                            │
│  ├─ 配额检查 (TokenQuotaService)                           │
│  ├─ 会话状态管理 (emittersByRequest 等 5 个 Map)            │
│  ├─ 分布式锁 (RedisLockHelper)                              │
│  └─ 线程池提交 (AiChatExecutor)                            │
└────────────────────────┬──────────────────────────────────┘
                         │
                         ▼
┌───────────────────────────────────────────────────────────┐
│  编排层  agent/AiAgentRunner                               │
│  ├─ 消息落库 (ChatHistoryStore)                            │
│  ├─ 记忆召回 (MemoryFacade.recall)                         │
│  ├─ 上下文组装 (ContextBuilder)                            │
│  ├─ 上下文裁剪 (ContextTrimmer)                            │
│  ├─ Agent 路由 (HybridAgentRouter)                         │
│  ├─ 模型路由 (ModelRouter)                                 │
│  └─ 执行分发 (单 Agent / Orchestrator)                     │
└────────────────────────┬──────────────────────────────────┘
                         │
        ┌────────────────┼────────────────┐
        ▼                ▼                ▼
┌─────────────┐  ┌─────────────┐  ┌─────────────┐
│  单 Agent    │  │  多 Agent    │  │  工具执行    │
│  AgentLoop   │  │ Orchestrator│  │  ToolRegistry│
└──────┬──────┘  └──────┬──────┘  └──────┬──────┘
       │                │                │
       └────────────────┼────────────────┘
                        ▼
┌───────────────────────────────────────────────────────────┐
│  能力层                                                     │
│  ├─ llm/LangChain4jLlmClient       (流式/非流式调用)        │
│  ├─ memory/RedisMemoryStore        (向量记忆)              │
│  ├─ knowledge/KnowledgeRegistrar   (知识库)                │
│  ├─ token/RedisTokenCounter        (计费)                  │
│  └─ tools/impl/*                   (具体工具)              │
└────────────────────────┬──────────────────────────────────┘
                         │
                         ▼
┌───────────────────────────────────────────────────────────┐
│  基础设施层                                                 │
│  ├─ infra/sse/AiChatSseEmitter     (SSE 封装)              │
│  ├─ infra/history/ChatHistoryStore (历史存储)              │
│  ├─ infra/lock/RedisLockHelper     (分布式锁)              │
│  └─ config/LangChain4jConfig       (模型 Bean)            │
└───────────────────────────────────────────────────────────┘
```

### 2.2 模块依赖图

```
                    ┌──────────────┐
                    │  AiAgentService│
                    └───────┬──────┘
                            │
                    ┌───────▼──────┐
                    │ AiAgentRunner │
                    └───────┬──────┘
                            │
        ┌───────────────────┼───────────────────┐
        ▼                   ▼                   ▼
  ┌──────────┐      ┌──────────┐      ┌──────────┐
  │  Router  │      │  Memory  │      │  Tools   │
  └────┬─────┘      └────┬─────┘      └────┬─────┘
       │                 │                 │
       ▼                 ▼                 ▼
  ┌──────────┐      ┌──────────┐      ┌──────────┐
  │  Agent   │◄─────│Knowledge │      │  Tool    │
  │  (core)  │      │          │      │  Impl    │
  └────┬─────┘      └────┬─────┘      └────┬─────┘
       │                 │                 │
       └─────────────────┼─────────────────┘
                         ▼
                  ┌──────────────┐
                  │  LLM Client  │
                  └──────┬───────┘
                         │
                  ┌──────▼───────┐
                  │  LangChain4j │
                  └──────────────┘
```

### 2.3 关键设计决策

| 决策       | 选型                                 | 理由             |
| -------- | ---------------------------------- | -------------- |
| Agent 模型 | 黑板模式 + 编排器                         | 支持多 Agent 共享状态 |
| 上下文管理    | 摘要 + 保留最近 N 轮 + 裁剪                 | 平衡成本与效果        |
| 记忆存储     | RediSearch 向量 + Hash               | 支持语义检索 + 精确去重  |
| 计费粒度     | 微元（micro CNY）                      | 避免浮点误差累积       |
| 并发控制     | 会话级 Redis 锁                        | 避免同会话并发污染      |
| SSE 事件   | delta / done / tool / error / meta | 覆盖全生命周期        |

---

## 三、模块设计

### 3.1 Agent 模块（`agent/`）

#### 3.1.1 核心接口

```java
public interface Agent {
    String name();
    String rolePrompt();
    List<String> allowedToolNames();
    List<String> intents();
    default int maxSteps() { return 8; }
    default boolean streamToUser() { return true; }
    void execute(AgentContext ctx);
}
```

#### 3.1.2 已实现 Agent

| Agent         | 角色    | 工具白名单                          | 意图                         | 可见  |
| ------------- | ----- | ------------------------------ | -------------------------- | --- |
| `general`     | 通用助手  | `["*"]`                        | chitchat/general           | ✅   |
| `knowledge`   | 知识库助手 | `query_knowledge`, `date_tool` | business_query/policy/...  | ✅   |
| `ticket`      | 工单助手  | `create_ticket`, `date_tool`   | create_ticket/report_issue | ✅   |
| `planner`     | 任务规划器 | `[]`                           | plan                       | ❌   |
| `synthesizer` | 结果汇总器 | `[]`                           | synthesize                 | ❌   |

#### 3.1.3 路由策略

```
HybridAgentRouter.route()
    │
    ├─ 1. 显式指定？→ 直接用
    │
    ├─ 2. 复杂问题？→ planner
    │     (长度 > 80 / 多问号 / 含连接词)
    │
    ├─ 3. 意图匹配？→ 唯一候选
    │     (关键词命中)
    │
    ├─ 4. 多候选？→ LLM 选择
    │
    └─ 5. 兜底 → general
```

### 3.2 LLM 模块（`llm/`）

#### 3.2.1 接口

```java
public interface LlmClient {
    LlmResponse chatStream(List<Message> messages,
                           List<ToolSpec> tools,
                           AiChatSseEmitter emitter);
    String chatSimple(String prompt);
}
```

#### 3.2.2 数据模型

```java
public record Message(
    MessageRole role,        // SYSTEM/USER/ASSISTANT/TOOL
    String content,
    String toolCallId,
    List<ToolCallRef> toolCalls
) {}

public record LlmResponse(
    String content,
    List<ToolCall> toolCalls,
    int inputTokens,
    int outputTokens,
    int totalTokens
) {}
```

### 3.3 Token 模块（`token/`）

#### 3.3.1 计费模型

```
成本 = 输入成本 + 输出成本
输入成本 = (normalInput / 1000) * price.input
         + (cachedInput / 1000) * price.input * CACHED_DISCOUNT
输出成本 = (outputTokens / 1000) * price.output
```

#### 3.3.2 Redis Key 设计

| Key                                    | 类型   | 说明        | TTL  |
| -------------------------------------- | ---- | --------- | ---- |
| `ai:token:usage:{userId}:{yyyyMMdd}`   | Hash | 日用量       | 90天  |
| `ai:token:usage:{userId}:{yyyyMM}`     | Hash | 月用量       | 400天 |
| `ai:token:cost:v2:{userId}:{yyyyMMdd}` | Hash | 日成本（微元）   | 90天  |
| `ai:token:model:{userId}:{yyyyMMdd}`   | Hash | 模型维度      | 90天  |
| `ai:token:records:{userId}:{yyyyMMdd}` | List | 明细（最新100） | 90天  |
| `ai:token:quota:{userId}`              | Hash | 配额配置      | 永久   |

#### 3.3.3 配额分级

| Tier     | 日限  | 月限   | 单请求上限 |
| -------- | --- | ---- | ----- |
| FREE     | 50万 | 500万 | 8000  |
| BASIC    | 可配置 | 可配置  | 可配置   |
| PREMIUM  | 可配置 | 可配置  | 可配置   |
| VIP      | 可配置 | 可配置  | 可配置   |
| DISABLED | 0   | 0    | 0     |

### 3.4 Memory 模块（`memory/`）

#### 3.4.1 记忆类型

```java
public enum MemoryType {
    PREFERENCE,  // 偏好
    FACT,        // 事实（身份/职位/部门）
    ENTITY,      // 关注对象（项目/系统）
    QUESTION     // 关注主题
}
```

#### 3.4.2 存储结构

```
Redis Hash: ai:memory:{userId}:{memoryId}
├─ id          : UUID
├─ userId      : 用户ID
├─ type        : PREFERENCE/FACT/ENTITY/QUESTION
├─ content     : 记忆内容
├─ importance  : 0.0~1.0
├─ createdAt   : 创建时间戳
├─ updatedAt   : 更新时间戳
├─ invalid     : 0/1
└─ embedding   : float32 二进制（1024维）

辅助索引：
├─ Set  ai:memory:user:{userId}      → memoryId 集合
└─ String ai:memory:reverse:{memoryId} → userId

RediSearch 索引：idx:ai:memory
├─ userId     TAG
├─ type       TAG
├─ content    TEXT
├─ importance NUMERIC
├─ createdAt  NUMERIC
├─ updatedAt  NUMERIC
├─ invalid    TAG
└─ embedding  VECTOR (HNSW, FLOAT32, DIM=1024, COSINE)
```

#### 3.4.3 生命周期

```
写入流程：
  用户对话 → MemoryExtractor.extract (异步)
    → LLM 抽取 → MemoryParser 解析
    → UserMemoryStore.save
      ├─ 精确去重 (md5)
      ├─ embedding 计算
      ├─ 语义去重 (余弦 >= 0.95)
      └─ RedisMemoryStore.save

召回流程：
  用户提问 → UserMemoryStore.recall
    → embedding 计算
    → KNN 检索 (topK=5)
    → 过滤 score >= 0
    → 返回 MemoryItem 列表

衰减流程（每日 3:00）：
  MemoryDecayJob.decay
    → 遍历活跃用户
    → decayAll(0.95)  衰减 5%
    → deleteBelow(0.1) 清理低分
```

### 3.5 Tools 模块（`tools/`）

#### 3.5.1 工具接口

```java
public interface AiTool {
    String name();
    String description();
    Map<String, Object> parameterSchema();  // JSON Schema
    ToolResult execute(Map<String, Object> args, AiToolContext ctx);
    default Set<String> allowedRoles() { return Set.of("*"); }
    default boolean requiresConfirmation() { return false; }
}
```

#### 3.5.2 已实现工具

| 工具                | 功能    | 角色         | 需确认 |
| ----------------- | ----- | ---------- | --- |
| `query_knowledge` | 知识库问答 | `*`        | 否   |
| `create_ticket`   | 创建工单  | USER/ADMIN | ✅   |
| `date_tool`       | 日期计算  | `*`        | 否   |

#### 3.5.3 权限模型

```
双层过滤：
  1. 角色过滤：tool.allowedRoles() ∩ ctx.roleSet()
  2. Agent 白名单：agent.allowedToolNames()

执行前二次校验：
  ToolRegistry.isAllowed(name, ctx)
```

### 3.6 Knowledge 模块（`knowledge/`）

#### 3.6.1 知识库注册

```java
KnowledgeRegistrar.register(knowledgeId)
  → 校验 knowledgeId 合法性
  → 查询下游知识库列表
  → 构建 KnowledgeBase 模板
  → KnowledgeTemplateRegistry.put()
```

#### 3.6.2 知识库路由

```
KnowledgeRouter.route(question)
  → 遍历缓存的 kbVectors
  → 计算余弦相似度
  → 返回最高分（>= 0.5）

后台刷新：
  @Scheduled 每 10 分钟
  → 拉取所有知识库
  → 生成描述 embedding
  → 更新 kbVectors
```

### 3.7 Infra 模块（`infra/`）

#### 3.7.1 SSE 封装

```java
public class AiChatSseEmitter extends SseEmitter {
    // 5 分钟超时
    // 静默：mute() / unmute()
    // 抑制关闭：setSuppressComplete()
    // 关闭回调：setOnCloseCallback()
}
```

**事件类型**：

| 事件             | 数据                             | 说明    |
| -------------- | ------------------------------ | ----- |
| `meta`         | `{requestId, sessionId}`       | 请求元信息 |
| `delta`        | `{text}`                       | 增量文本  |
| `done`         | `{messageId}`                  | 完成    |
| `tool`         | `{name, args, status, result}` | 工具执行  |
| `tool_confirm` | `{callId, tool, args}`         | 需要确认  |
| `error`        | `{code, message}`              | 错误    |

#### 3.7.2 分布式锁

```java
RedisLockHelper.tryLock(key, token, ttlMillis)
RedisLockHelper.unlock(key, token)   // Lua 保证原子
RedisLockHelper.forceUnlock(key)
```

#### 3.7.3 历史存储

```java
public interface ChatHistoryStore {
    boolean exists(String sessionId);
    void assertOwner(String sessionId, String userId);
    long nextMessageId();
    void appendUser(long messageId, String sessionId, String userId, String message);
    void appendAssistant(long messageId, String sessionId, String content);
    List<Message> load(String sessionId);
    String loadAssistant(long messageId);
    List<AiChatSessionResponse> sessions(String currentUserId);
    // ...
}
```

**当前实现**：`InMemoryChatHistoryStore`（**仅单机，待替换**）

---

## 四、核心流程

### 4.1 单 Agent 对话流程

```
┌──────────┐
│  前端    │
└────┬─────┘
     │ POST /ai/chat (SSE)
     ▼
┌───────────────────────────┐
│ AiAgentService.chat()     │
│ 1. 配额检查                │
│ 2. 会话状态注册            │
│ 3. 分布式锁                │
│ 4. 提交线程池              │
└─────────┬─────────────────┘
          │
          ▼
┌───────────────────────────┐
│ AiAgentRunner.run()       │
│ 1. 发送 meta 帧            │
│ 2. 落库 user 消息          │
│ 3. 记忆召回                │
│ 4. 上下文构建 + 裁剪       │
│ 5. Agent 路由              │
│ 6. 模型路由                │
│ 7. 创建 AgentContext       │
│ 8. 创建 Blackboard         │
│ 9. 执行 Agent              │
└─────────┬─────────────────┘
          │
          ▼
┌───────────────────────────┐
│ AbstractAgent.execute()   │
│ 1. 注入 rolePrompt        │
│ 2. 设置工具白名单          │
│ 3. 委托 AgentLoop         │
└─────────┬─────────────────┘
          │
          ▼
┌───────────────────────────┐
│ AgentLoop.run()           │
│ for step in 0..maxSteps:  │
│   1. 调用 LLM (流式)      │
│   2. 记录 token           │
│   3. 二次配额校验          │
│   4. 无工具调用 → done     │
│   5. 有工具调用 → 执行     │
│       ├─ 权限校验          │
│       ├─ 二次确认？        │
│       └─ 执行工具          │
└─────────┬─────────────────┘
          │
          ▼
      SSE 事件推送
      delta / tool / done
```

### 4.2 多 Agent 编排流程

```
┌───────────────────────────┐
│ Orchestrator.execute()    │
│                           │
│ 1. Planner 规划           │
│    ├─ 静默执行            │
│    ├─ 输出 JSON 任务列表  │
│    └─ 存入 Blackboard     │
│                           │
│ 2. 循环执行子任务         │
│    for task in pending:   │
│      ├─ fork AgentContext │
│      ├─ 执行子 Agent      │
│      └─ 记录 AgentResult  │
│                           │
│ 3. Synthesizer 汇总       │
│    ├─ 静默执行            │
│    ├─ 整合子任务结果      │
│    └─ 生成最终答案        │
│                           │
│ 4. 推送最终答案           │
│    ├─ send delta          │
│    ├─ send done           │
│    └─ complete            │
└───────────────────────────┘
```

### 4.3 工具确认流程（含缺口）

```
AgentLoop 遇到 requiresConfirmation=true 的工具
    │
    ▼
┌───────────────────────────┐
│ 1. 保存 PendingState      │
│    - messages             │
│    - confirmedCalls       │
│    - toolContext          │
│    → Redis (TTL 10分钟)   │
│                           │
│ 2. 发送 tool_confirm 事件 │
│                           │
│ 3. 关闭 SSE 连接          │
└───────────────────────────┘
    │
    ▼
  用户确认（前端发起新请求）
    │
    ▼
┌───────────────────────────┐
│ ❌ 缺口：无恢复入口        │
│    - 新请求的 runId 不同   │
│    - 无法加载 PendingState │
│    - 无法从断点继续        │
└───────────────────────────┘
```

### 4.4 Token 计费流程

```
每次 LLM 调用
    │
    ▼
LangChain4jLlmClient.chatStream()
    │
    ├─ onCompleteResponse
    │   └─ 提取 TokenUsage
    │
    ▼
AgentLoop
    │
    ├─ TokenUsage.fromLlmResponse()
    │   └─ ⚠️ cachedTokens 永远 0
    │
    ▼
RedisTokenCounter.record()
    │
    ├─ CostCalculator.calculate()
    │   └─ 计算成本
    │
    ├─ Pipeline 写入 Redis
    │   ├─ usage day/month
    │   ├─ cost day/month (微元)
    │   ├─ model day
    │   ├─ records (LIFO, 100条)
    │   └─ TTL 设置
    │
    └─ TokenMetrics 上报
```

---

## 五、数据模型

### 5.1 核心实体

```java
// Agent 上下文
public class AgentContext {
    List<Message> messages;
    AiToolContext toolContext;
    AiChatSseEmitter emitter;
    String sessionId;
    long assistantMessageId;
    String runId;
    String memoryId;
    String agentName;
    List<String> allowedToolNames;
    int maxSteps;
    Blackboard blackboard;
    boolean streamToUser;
    String modelName;
    Set<String> confirmedCalls;
}

// 记忆项
public record MemoryItem(
    String id, String userId, String type, String content,
    List<Double> embedding, double importance,
    long createdAt, long updatedAt, boolean invalid
) {}

// 工具调用
public record ToolCall(String id, String name, Map<String, Object> args) {}

// 计费记录
public record TokenUsageRecord(
    String recordId, String userId, String sessionId, String runId,
    String modelName, int inputTokens, int outputTokens, int totalTokens,
    int cachedTokens, double costCny, long timestamp
) {}
```

### 5.2 Redis Key 汇总

| 模块          | Key 模式                              | 类型     | TTL  |
| ----------- | ----------------------------------- | ------ | ---- |
| Token-用量    | `ai:token:usage:{userId}:{date}`    | Hash   | 90天  |
| Token-成本    | `ai:token:cost:v2:{userId}:{date}`  | Hash   | 90天  |
| Token-配额    | `ai:token:quota:{userId}`           | Hash   | 永久   |
| Memory-主数据  | `ai:memory:{userId}:{memoryId}`     | Hash   | 90天  |
| Memory-用户集  | `ai:memory:user:{userId}`           | Set    | 90天  |
| Memory-反向   | `ai:memory:reverse:{memoryId}`      | String | 90天  |
| Memory-精确去重 | `ai:memory:exact:{md5}`             | String | 30天  |
| 会话锁         | `ai:chat:lock:{userId}:{sessionId}` | String | 5秒⚠️ |
| 挂起状态        | `ai:pending:{runId}`                | String | 10分钟 |
| 黑板          | `ai:blackboard:{runId}:*`           | 多种     | 30分钟 |
| 摘要缓存        | `ai:summary:{hashCode}`             | String | 1小时  |

---

## 六、接口协议

### 6.1 REST 接口

#### 6.1.1 发起对话

```http
POST /ai/chat
Content-Type: application/json
Accept: text/event-stream

{
  "sessionId": "sess-xxx",
  "message": "查询报销流程",
  "knowledgeId": null,
  "clientRequestId": "req-xxx",
  "agentName": null
}
```

**SSE 响应**：

```
event: meta
data: {"requestId":"req-xxx","sessionId":"sess-xxx"}

event: delta
data: {"text":"根据"}

event: delta
data: {"text":"公司制度..."}

event: tool
data: {"name":"query_knowledge","status":"executing","args":{...}}

event: tool
data: {"name":"query_knowledge","status":"completed","result":"..."}

event: done
data: {"messageId":12345}
```

#### 6.1.2 停止会话

```http
POST /ai/chat/stop
{
  "sessionId": "sess-xxx",
  "clientRequestId": "req-xxx"
}
```

#### 6.1.3 查询用量

```http
GET /ai/token/summary
→ {
  "inputTokens": 12345,
  "outputTokens": 6789,
  "totalTokens": 19134,
  "cachedTokens": 0,
  "requestCount": 42,
  "costCny": 0.15,
  "byModel": {"qwen-max": 15000, "kimi-k2.6": 4134}
}
```

### 6.2 SSE 事件契约

| 事件名            | 数据 Schema                      | 时机       |
| -------------- | ------------------------------ | -------- |
| `meta`         | `{requestId, sessionId}`       | 请求开始     |
| `delta`        | `{text}`                       | LLM 流式输出 |
| `tool`         | `{name, args, status, result}` | 工具调用     |
| `tool_confirm` | `{callId, tool, args}`         | 需要确认     |
| `done`         | `{messageId}`                  | 完成       |
| `error`        | `{code, message}`              | 错误       |

**错误码**：

| Code               | 说明    |
| ------------------ | ----- |
| `QUOTA_EXCEEDED`   | 配额超限  |
| `INTERNAL_ERROR`   | 内部错误  |
| `SESSION_REPLACED` | 会话被替换 |
| `MANUALLY_STOPPED` | 手动停止  |

---

## 七、配置说明

### 7.1 核心配置

```yaml
# LangChain4j 模型
langchain4j:
  open-ai:
    base-url: https://api.example.com/v1
    api-key: ${AI_API_KEY}
    chat-model:
      model-name: qwen3-max
      temperature: 0.3
      enable-thinking: false
    streaming-chat-model:
      model-name: qwen3-max
      temperature: 0.7
    embedding-model:
      model-name: text-embedding-v3
      dimension: 1024
  http:
    timeout-seconds: 60
    max-retries: 3
    log-requests: false
    log-responses: false

# Token 计费
token:
  enabled: true
  default-tier: FREE
  max-prompt-tokens: 6000
  context-reserve-ratio: 0.2
  estimator:
    type: heuristic  # heuristic | tiktoken
  cost:
    qwen-max: {input: 0.02, output: 0.06}
    kimi-k2.6: {input: 0.012, output: 0.012}
    qwen-turbo: {input: 0.0003, output: 0.0006}
  quota:
    FREE: {daily: 500000, monthly: 5000000, maxRequest: 8000}
    BASIC: {daily: 5000000, monthly: 50000000, maxRequest: 32000}
    VIP: {daily: -1, monthly: -1, maxRequest: 128000}

# 线程池
ai:
  chat:
    enabled: true
    max-concurrent: 16
    queue-capacity: 100
    keep-alive-seconds: 60
  knowledge-router:
    refresh-minutes: 10
    retry-backoff-millis: 60000
```

### 7.2 环境变量

| 变量               | 说明          | 必填  |
| ---------------- | ----------- | --- |
| `AI_API_KEY`     | LLM API Key | ✅   |
| `REDIS_HOST`     | Redis 地址    | ✅   |
| `REDIS_PORT`     | Redis 端口    | ✅   |
| `REDIS_PASSWORD` | Redis 密码    | 否   |

---

## 八、已知问题与技术债

> 本章汇总所有代码审查中发现的问题，按优先级排序。

### 8.1 P0 级问题（生产必炸，共 27 项）

#### 8.1.1 数据一致性

| #   | 模块                         | 问题                           | 影响     |
| --- | -------------------------- | ---------------------------- | ------ |
| 1   | `InMemoryChatHistoryStore` | 进程内存储，多实例数据丢失                | 数据丢失   |
| 2   | `InMemoryChatHistoryStore` | `load()` 全表扫描                | 性能     |
| 3   | `InMemoryChatHistoryStore` | `appendAssistant` owner 可能为空 | 越权拒绝   |
| 4   | `AiAgentService`           | 5 个 Map 无清理机制                | 内存泄漏   |
| 5   | `AiAgentService`           | 锁 TTL 5秒 < LLM 120秒          | 并发失控   |
| 6   | `AiAgentService`           | requestId/runId 混用           | key 撞车 |
| 7   | `RedisMemoryStore`         | 主数据与辅助索引非原子                  | 记忆找不到  |
| 8   | `UserMemoryStore`          | exactKey 先写后失败               | 记忆丢失   |

#### 8.1.2 SSE / 流式

| #   | 模块                     | 问题                         | 影响         |
| --- | ---------------------- | -------------------------- | ---------- |
| 9   | `LangChain4jLlmClient` | `done` 事件重复发送              | 前端双份内容     |
| 10  | `LangChain4jLlmClient` | token 计费异常路径丢失             | 计费漏单       |
| 11  | `LangChain4jLlmClient` | 超时返回空而非抛异常                 | 用户收到空回复    |
| 12  | `LangChain4jLlmClient` | Tool message 的 name 传 null | OpenAI 400 |
| 13  | `AiChatSseEmitter`     | `complete()` 回调执行 2-3 次    | 逻辑错乱       |
| 14  | `AiChatSseEmitter`     | `onCloseCallback` 只支持单回调   | 功能受限       |
| 15  | `AiAgentRunner`        | 异常时 emitter 未关闭            | 前端挂起       |

#### 8.1.3 Tools

| #   | 模块                 | 问题                           | 影响           |
| --- | ------------------ | ---------------------------- | ------------ |
| 16  | `ToolRegistry`     | 空列表 = 全部工具（语义反了）             | Planner 误调工具 |
| 17  | `ToolRegistry`     | `isAllowed` 不校验 Agent 白名单    | 越权调用         |
| 18  | `CreateTicketTool` | 是 mock 实现                    | 生产假数据        |
| 19  | `AgentLoop`        | `requiresConfirmation` 无恢复入口 | 功能不可用        |

#### 8.1.4 Token 计费

| #   | 模块                  | 问题                  | 影响    |
| --- | ------------------- | ------------------- | ----- |
| 20  | `TokenUsage`        | `cachedTokens` 永远 0 | 用户多付钱 |
| 21  | `RedisTokenCounter` | pipeline 不检查返回值     | 账单有洞  |
| 22  | `RedisTokenCounter` | cost 与 usage 非原子    | 账单不一致 |
| 23  | `AgentLoop`         | 配额检查在 LLM 调用之后      | 超限仍消耗 |

#### 8.1.5 Memory

| #   | 模块                 | 问题              | 影响                |
| --- | ------------------ | --------------- | ----------------- |
| 24  | `RedisMemoryStore` | score 被回查覆盖     | 语义去重/排序失效         |
| 25  | `RedisMemoryStore` | RETURN 被注释      | 带宽浪费              |
| 26  | `RedisMemoryStore` | 索引维度硬编码         | 换模型后写失败           |
| 27  | `Message`          | 缺 `toolName` 字段 | 无法构造 tool message |

### 8.2 P1 级问题（共 25 项）

| 模块                      | 问题                           | 影响       |
| ----------------------- | ---------------------------- | -------- |
| `LangChain4jConfig`     | 全局 Conscrypt 副作用             | 可能冲突     |
| `LangChain4jConfig`     | `toChatMessages` 可能多个 System | 部分模型 400 |
| `KnowledgeRegistrar`    | check-then-act 竞态            | 重复调用     |
| `KnowledgeRouter`       | `MIN_SCORE` 硬编码              | 需重打包     |
| `SummaryService`        | hashCode 缓存 key              | 摘要串号     |
| `SummaryService`        | 同步阻塞请求链路                     | 请求慢      |
| `ContextBuilder`        | 摘要是 system，不受预算              | 上下文爆     |
| `MemoryDecayJob`        | 计数器语义错误                      | 监控误判     |
| `RedisMemoryStore`      | `decayAll` 逐条读写              | 性能差      |
| `RedisMemoryStore`      | RESP3 解析脆弱                   | 兼容性      |
| `DefaultCostCalculator` | 缓存不失效                        | 价格错      |
| `ChatService`           | `appendUser` 在 LLM 之后        | 消息丢失     |
| `ChatService`           | 与 `AiAgentService` 职责重叠      | 代码混乱     |
| `TiktokenEstimator`     | 编码与模型不匹配                     | 估算误差     |
| `AiToolContext`         | ThreadLocal 冗余               | 复杂度      |
| `AgentLoop`             | 工具结果未截断                      | 上下文爆     |
| `AiToolContext`         | 角色读取假设类型                     | 角色全空     |
| `QueryKnowledgeTool`    | `String.valueOf(null)` 陷阱    | 边界       |
| `MemoryParser`          | `peek` 里修改集合                 | 函数式      |
