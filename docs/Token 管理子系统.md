# Token 管理子系统 - 详细设计文档 + 完整代码

> 版本：1.0
> 日期：2026-10-12
> 模块：`iam-backend-ai` / token-management
> 目标：**计数 / 配额 / 成本 / 上下文裁剪 / 模型路由 / 缓存优化**

---

## 目录

1. [设计目标](#1-设计目标)
2. [整体架构](#2-整体架构)
3. [数据模型](#3-数据模型)
4. [Redis 存储设计](#4-redis-存储设计)
5. [核心流程](#5-核心流程)
6. [完整代码](#6-完整代码)
7. [接入点与改动清单](#7-接入点与改动清单)
8. [配置](#8-配置)
9. [API 接口](#9-api-接口)
10. [监控指标](#10-监控指标)
11. [测试方案](#11-测试方案)
12. [上线检查清单](#12-上线检查清单)

---

## 1. 设计目标

### 1.1 业务目标

| 目标        | 说明                     | 可量化指标                  |
| --------- | ---------------------- | ---------------------- |
| **成本可见**  | 每用户、每天、每模型的 token 消耗可查 | 100% 覆盖                |
| **成本可控**  | 超配额立即拒绝，防止恶意刷单         | <10 ms 检查耗时            |
| **上下文可控** | 长对话自动裁剪，不超模型窗口         | 单次 prompt < 8000 token |
| **模型经济**  | 简单问题走小模型，复杂问题走大模型      | 成本降低 30%+              |
| **缓存友好**  | 提升 prompt 缓存命中率        | cache hit > 50%        |

### 1.2 非目标

- 不做实时账单（异步、准实时即可）
- 不做多租户分账（后续扩展）
- 不做代付/退款（业务侧处理）

---

## 2. 整体架构

```
┌──────────────────────────────────────────────────────────────────┐
│                       AiAgentService.chat()                      │
│                          ① 入口检查配额                            │
└─────────────────────────┬────────────────────────────────────────┘
                          ▼
┌──────────────────────────────────────────────────────────────────┐
│                    AiAgentRunner.run()                           │
│                                                                  │
│   ┌─────────────────────────────────────────────────────────┐   │
│   │  ② ContextTrimmer.trim(history, maxTokens)               │   │
│   │     裁剪上下文, 保留 system + 最近 N 轮                    │   │
│   └─────────────────────────────────────────────────────────┘   │
│                                                                  │
│   ┌─────────────────────────────────────────────────────────┐   │
│   │  ③ ModelRouter.select(agentName, message)                │   │
│   │     简单问题 → 小模型, 复杂问题 → 大模型                    │   │
│   └─────────────────────────────────────────────────────────┘   │
│                                                                  │
└─────────────────────────┬────────────────────────────────────────┘
                          ▼
┌──────────────────────────────────────────────────────────────────┐
│                      AgentLoop / Agent                           │
│                                                                  │
│   ┌─────────────────────────────────────────────────────────┐   │
│   │  ④ LLM 调用                                              │   │
│   │     ChatResponse resp = model.chat(messages);            │   │
│   │     TokenUsage usage = resp.tokenUsage();  ★ 提取         │   │
│   └─────────────────────────────────────────────────────────┘   │
│                                                                  │
│   ┌─────────────────────────────────────────────────────────┐   │
│   │  ⑤ TokenCounter.record(userId, runId, model, usage)      │   │
│   │     写入 Redis: HINCRBY usage / cost                     │   │
│   └─────────────────────────────────────────────────────────┘   │
│                                                                  │
│   ┌─────────────────────────────────────────────────────────┐   │
│   │  ⑥ QuotaService.deductAndCheck(userId, tokens)           │   │
│   │     扣减 + 再校验, 超了就中断                             │   │
│   └─────────────────────────────────────────────────────────┘   │
│                                                                  │
└─────────────────────────┬────────────────────────────────────────┘
                          ▼
┌──────────────────────────────────────────────────────────────────┐
│                    Redis 存储                                    │
│   ai:token:usage:{userId}:{yyyyMMdd}   ← 日用量 (Hash)           │
│   ai:token:usage:{userId}:{yyyyMM}     ← 月用量 (Hash)           │
│   ai:token:quota:{userId}              ← 配额配置 (Hash)          │
│   ai:token:records:{userId}:{yyyyMMdd} ← 详细记录 (List, 可选)    │
└──────────────────────────────────────────────────────────────────┘
```

---

## 3. 数据模型

### 3.1 核心实体

#### 3.1.1 `TokenUsage`（单次 usage 快照）

```java
public record TokenUsage(
        int inputTokens,        // prompt_tokens
        int outputTokens,       // completion_tokens
        int totalTokens,        // total_tokens
        int cachedTokens        // prompt_tokens_details.cached_tokens (缓存命中)
) {
    public static TokenUsage zero() {
        return new TokenUsage(0, 0, 0, 0);
    }
}
```

#### 3.1.2 `TokenUsageRecord`（落库记录）

```java
public record TokenUsageRecord(
        String recordId,        // UUID
        String userId,
        String sessionId,
        String runId,           // 一次对话的 runId
        String modelName,       // kimi-k2.6 / qwen-turbo ...
        int inputTokens,
        int outputTokens,
        int totalTokens,
        int cachedTokens,
        double costCny,         // 本次成本 (¥)
        long timestamp
) {}
```

#### 3.1.3 `TokenQuota`（配额配置）

```java
public record TokenQuota(
        String userId,
        String tier,            // FREE / BASIC / PREMIUM / VIP
        long dailyTokenLimit,   // 日限额
        long monthlyTokenLimit, // 月限额
        int  maxRequestTokens,  // 单次上限
        boolean enabled         // 是否启用 (可临时禁用)
) {
    public static TokenQuota defaultFree() {
        return new TokenQuota("", "FREE", 500_000L, 5_000_000L, 8_000, true);
    }
}
```

#### 3.1.4 `QuotaStatus`（查询结果）

```java
public record QuotaStatus(
        String userId,
        String tier,
        long dailyUsed,
        long dailyLimit,
        long dailyRemaining,
        long monthlyUsed,
        long monthlyLimit,
        long monthlyRemaining,
        double todayCostCny,
        double monthCostCny,
        boolean exceeded,
        String reason             // 若超限, 说明原因
) {}
```

### 3.2 用户分级与配额

| tier       | 日限额          | 月限额          | 单次上限  | 允许功能      |
| ---------- | ------------ | ------------ | ----- | --------- |
| `FREE`     | 50 万 token   | 500 万 token  | 8000  | 基础对话      |
| `BASIC`    | 200 万 token  | 2000 万 token | 12000 | + 多 Agent |
| `PREMIUM`  | 1000 万 token | 1 亿 token    | 20000 | + 全部      |
| `VIP`      | 无限           | 无限           | 32000 | 全部        |
| `DISABLED` | 0            | 0            | 0     | 拒绝所有      |

---

## 4. Redis 存储设计

### 4.1 Key 清单

| Key                                    | 类型     | 内容              | TTL   |
| -------------------------------------- | ------ | --------------- | ----- |
| `ai:token:usage:{userId}:{yyyyMMdd}`   | Hash   | 日用量             | 90 天  |
| `ai:token:usage:{userId}:{yyyyMM}`     | Hash   | 月用量             | 400 天 |
| `ai:token:cost:{userId}:{yyyyMMdd}`    | String | 日成本 (¥)         | 90 天  |
| `ai:token:cost:{userId}:{yyyyMM}`      | String | 月成本 (¥)         | 400 天 |
| `ai:token:quota:{userId}`              | Hash   | 配额配置            | 无     |
| `ai:token:records:{userId}:{yyyyMMdd}` | List   | 详细记录 (最多 100 条) | 90 天  |
| `ai:token:model:{userId}:{yyyyMMdd}`   | Hash   | 各模型用量           | 90 天  |

### 4.2 Hash 字段设计

**`ai:token:usage:{userId}:{yyyyMMdd}`**：

| field          | 值    | 说明          |
| -------------- | ---- | ----------- |
| `inputTokens`  | Long | 输入 token 累计 |
| `outputTokens` | Long | 输出 token 累计 |
| `totalTokens`  | Long | 总 token 累计  |
| `cachedTokens` | Long | 缓存命中 token  |
| `requestCount` | Long | 请求次数        |

**`ai:token:quota:{userId}`**：

| field          | 值                  |
| -------------- | ------------------ |
| `tier`         | FREE / BASIC / ... |
| `dailyLimit`   | Long               |
| `monthlyLimit` | Long               |
| `maxRequest`   | Int                |
| `enabled`      | "1"/"0"            |

**示例操作**：

```
HINCRBY ai:token:usage:laijf2:20261012 inputTokens 500
HINCRBY ai:token:usage:laijf2:20261012 outputTokens 200
HINCRBYFLOAT ai:token:cost:laijf2:20261012 0.0084
EXPIRE ai:token:usage:laijf2:20261012 7776000
```

---

## 5. 核心流程

### 5.1 入口配额检查

```
AiAgentService.chat()
  │
  ├── quotaService.check(userId)
  │     ├── HGETALL ai:token:quota:{userId}
  │     │   ├── 无 → 用默认 FREE 配额
  │     │   └── 有 → 用配置
  │     │
  │     ├── GET ai:token:usage:{userId}:{today}
  │     │   └── 拿 dailyUsed
  │     │
  │     ├── 检查 dailyUsed >= dailyLimit ?
  │     │   ├── 是 → 抛 QuotaExceededException
  │     │   └── 否 → 继续
  │     │
  │     └── 返回 QuotaStatus
  │
  └── if (!status.exceeded()) 继续正常流程
```

### 5.2 LLM 调用后计数

```
AgentLoop / Agent 内
  │
  └── ChatResponse resp = model.chat(messages)
        │
        ├── resp.aiMessage()       ← 拿 AI 回答
        ├── resp.tokenUsage()      ← 拿 usage ★
        │
        └── tokenCounter.record(userId, sessionId, runId, modelName, usage)
              │
              ├── ① 写日用量
              │     HINCRBY ai:token:usage:{userId}:{today} ...
              │     EXPIRE 90d
              │
              ├── ② 写月用量
              │     HINCRBY ai:token:usage:{userId}:{month} ...
              │     EXPIRE 400d
              │
              ├── ③ 算成本
              │     cost = costCalculator.calc(modelName, usage)
              │     INCRBYFLOAT ai:token:cost:{userId}:{today} cost
              │
              ├── ④ 按模型统计
              │     HINCRBY ai:token:model:{userId}:{today} {model} {total}
              │
              └── ⑤ 存详细记录 (可选, 仅前 100 条)
                    LPUSH ai:token:records:{userId}:{today} {json}
                    LTRIM 0 99
```

### 5.3 上下文裁剪

```
ContextTrimmer.trim(history, systemPrompt, userMessage, maxTokens)
  │
  ├── ① 保留 system prompt（必须）
  │     tokens = estimate(systemPrompt)
  │
  ├── ② 保留本轮 user message（必须）
  │     tokens += estimate(userMessage)
  │
  ├── ③ 从后往前遍历 history
  │     for (i = history.size - 1; i >= 0; i--) {
  │         t = estimate(history[i].content)
  │         if (tokens + t > maxTokens * 0.8) break;   // 80% 水位
  │         result.add(0, history[i])
  │         tokens += t
  │     }
  │
  └── ④ 返回裁剪后的 messages
```

**注意**：优先保留最近的对话，最老的丢弃。

### 5.4 模型路由

```
ModelRouter.select(agentName, message, historyLength)
  │
  ├── agentName == "planner"        → 大模型 (复杂推理)
  ├── message.length < 20           → 小模型 (简单问题)
  ├── historyLength > 10            → 中模型
  └── default                        → 中模型
```

---

## 6. 完整代码

### 6.1 `TokenUsage.java`

```java
package com.lenovo.ai.token;

import dev.langchain4j.model.output.TokenUsage;

/**
 * Token 用量快照 (对 LangChain4j TokenUsage 的封装)
 */
public record TokenUsage(
        int inputTokens,
        int outputTokens,
        int totalTokens,
        int cachedTokens
) {
    public static TokenUsage zero() {
        return new TokenUsage(0, 0, 0, 0);
    }

    /**
     * 从 LangChain4j 的 TokenUsage 转换
     * 兼容 dev.langchain4j.model.output.TokenUsage
     */
    public static TokenUsage from(dev.langchain4j.model.output.TokenUsage lc) {
        if (lc == null) return zero();
        Integer in = lc.inputTokenCount();
        Integer out = lc.outputTokenCount();
        Integer total = lc.totalTokenCount();
        return new TokenUsage(
                in == null ? 0 : in,
                out == null ? 0 : out,
                total == null ? 0 : total,
                0    // LangChain4j 不直接暴露 cachedTokens, 由调用方从原始响应解析
        );
    }

    public TokenUsage withCached(int cached) {
        return new TokenUsage(inputTokens, outputTokens, totalTokens, cached);
    }
}
```

### 6.2 `TokenUsageRecord.java`

```java
package com.lenovo.ai.token;

import lombok.Builder;

@Builder
public record TokenUsageRecord(
        String recordId,
        String userId,
        String sessionId,
        String runId,
        String modelName,
        int inputTokens,
        int outputTokens,
        int totalTokens,
        int cachedTokens,
        double costCny,
        long timestamp
) {}
```

### 6.3 `TokenQuota.java`

```java
package com.lenovo.ai.token;

public record TokenQuota(
        String userId,
        String tier,
        long dailyTokenLimit,
        long monthlyTokenLimit,
        int maxRequestTokens,
        boolean enabled
) {
    public static final String TIER_FREE    = "FREE";
    public static final String TIER_BASIC   = "BASIC";
    public static final String TIER_PREMIUM = "PREMIUM";
    public static final String TIER_VIP     = "VIP";
    public static final String TIER_DISABLED = "DISABLED";

    public static TokenQuota defaultFree(String userId) {
        return new TokenQuota(userId, TIER_FREE, 500_000L, 5_000_000L, 8_000, true);
    }

    public static TokenQuota byTier(String userId, String tier) {
        return switch (tier) {
            case TIER_BASIC   -> new TokenQuota(userId, tier, 2_000_000L, 20_000_000L, 12_000, true);
            case TIER_PREMIUM -> new TokenQuota(userId, tier, 10_000_000L, 100_000_000L, 20_000, true);
            case TIER_VIP     -> new TokenQuota(userId, tier, Long.MAX_VALUE, Long.MAX_VALUE, 32_000, true);
            case TIER_DISABLED -> new TokenQuota(userId, tier, 0L, 0L, 0, false);
            default -> defaultFree(userId);
        };
    }
}
```

### 6.4 `QuotaStatus.java`

```java
package com.lenovo.ai.token;

public record QuotaStatus(
        String userId,
        String tier,
        long dailyUsed,
        long dailyLimit,
        long dailyRemaining,
        long monthlyUsed,
        long monthlyLimit,
        long monthlyRemaining,
        double todayCostCny,
        double monthCostCny,
        boolean exceeded,
        String reason
) {
    public static QuotaStatus exceeded(String userId, String tier, String reason) {
        return new QuotaStatus(userId, tier, 0, 0, 0, 0, 0, 0, 0, 0, true, reason);
    }
}
```

### 6.5 `QuotaExceededException.java`

```java
package com.lenovo.ai.token;

public class QuotaExceededException extends RuntimeException {
    private final String userId;
    private final String reason;

    public QuotaExceededException(String userId, String reason) {
        super("Token 配额超限: userId=" + userId + ", reason=" + reason);
        this.userId = userId;
        this.reason = reason;
    }

    public String getUserId() { return userId; }
    public String getReason() { return reason; }
}
```

### 6.6 `CostCalculator.java`

```java
package com.lenovo.ai.token;

import com.lenovo.ai.token.model.TokenUsage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * 成本计算 (单位: ¥ / 千 token)
 * 价格表可配置化, 这里以硬编码为例
 */
@Slf4j
@Component
public class CostCalculator {

    /**
     * 各模型价格 (¥ / 千 token)
     * input  = 输入价格
     * output = 输出价格
     */
    private static final Map<String, ModelPrice> PRICES = Map.of(
            "kimi-k2.6", new ModelPrice(0.012, 0.012),
            "qwen-turbo", new ModelPrice(0.0003, 0.0006),
            "qwen-plus", new ModelPrice(0.0008, 0.002),
            "qwen-max", new ModelPrice(0.02, 0.06),
            "text-embedding-v3", new ModelPrice(0.0007, 0.0)
    );

    /**
     * 计算成本 (元)
     */
    public double calculate(String modelName, TokenUsage usage) {
        if (usage == null) return 0.0;
        ModelPrice price = PRICES.getOrDefault(modelName, ModelPrice.DEFAULT);

        // 缓存命中的部分按 10% 价格计费 (示例, 具体看服务商)
        int normalInput = usage.inputTokens() - usage.cachedTokens();
        int cachedInput = usage.cachedTokens();

        double inputCost = (normalInput / 1000.0) * price.input()
                + (cachedInput / 1000.0) * price.input() * 0.1;
        double outputCost = (usage.outputTokens() / 1000.0) * price.output();
        double total = inputCost + outputCost;

        log.debug("[cost] model={}, in={}, out={}, cached={}, cost={}",
                modelName, usage.inputTokens(), usage.outputTokens(),
                usage.cachedTokens(), total);
        return total;
    }

    public record ModelPrice(double input, double output) {
        public static final ModelPrice DEFAULT = new ModelPrice(0.02, 0.06);
    }
}
```

### 6.7 `TokenEstimator.java`（本地 token 估算）

```java
package com.lenovo.ai.token;

import org.springframework.stereotype.Component;

/**
 * Token 估算 (不需要调用 API)
 * 中文: 1 字 ≈ 1.5 token
 * 英文: 1 词 ≈ 1.3 token (按字符数 / 4 大致估算)
 */
@Component
public class TokenEstimator {

    public int estimate(String text) {
        if (text == null || text.isEmpty()) return 0;

        int chineseCount = 0;
        int otherCount = 0;
        for (char c : text.toCharArray()) {
            if (isChinese(c)) chineseCount++;
            else otherCount++;
        }
        // 中文 1 字 ≈ 1.5 token; 英文按 4 字符 ≈ 1 token
        return (int) Math.ceil(chineseCount * 1.5 + otherCount / 4.0);
    }

    private static boolean isChinese(char c) {
        return c >= 0x4E00 && c <= 0x9FFF;
    }
}
```

### 6.8 `TokenCounter.java`

```java
package com.lenovo.ai.token;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lenovo.ai.token.model.TokenUsage;
import com.lenovo.ai.token.model.TokenUsageRecord;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * Token 计数器
 * 职责: 把单次 LLM 调用的 usage 累计到 Redis
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TokenCounter {

    private static final DateTimeFormatter DAY_FMT = DateTimeFormatter.ofPattern("yyyyMMdd");
    private static final DateTimeFormatter MONTH_FMT = DateTimeFormatter.ofPattern("yyyyMM");
    private static final long DAY_TTL_DAYS = 90L;
    private static final long MONTH_TTL_DAYS = 400L;
    private static final int MAX_RECORDS_PER_DAY = 100;

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final StringRedisTemplate redis;
    private final CostCalculator costCalculator;

    /**
     * 记录一次 LLM 调用的 usage
     */
    public void record(String userId, String sessionId, String runId,
                       String modelName, TokenUsage usage) {
        if (usage == null || usage.totalTokens() <= 0) return;

        try {
            String dayKey = dayKey(userId);
            String monthKey = monthKey(userId);

            // ① 日用量
            redis.opsForHash().increment(dayKey, "inputTokens", usage.inputTokens());
            redis.opsForHash().increment(dayKey, "outputTokens", usage.outputTokens());
            redis.opsForHash().increment(dayKey, "totalTokens", usage.totalTokens());
            redis.opsForHash().increment(dayKey, "cachedTokens", usage.cachedTokens());
            redis.opsForHash().increment(dayKey, "requestCount", 1);
            redis.expire(dayKey, DAY_TTL_DAYS, TimeUnit.DAYS);

            // ② 月用量
            redis.opsForHash().increment(monthKey, "inputTokens", usage.inputTokens());
            redis.opsForHash().increment(monthKey, "outputTokens", usage.outputTokens());
            redis.opsForHash().increment(monthKey, "totalTokens", usage.totalTokens());
            redis.opsForHash().increment(monthKey, "cachedTokens", usage.cachedTokens());
            redis.opsForHash().increment(monthKey, "requestCount", 1);
            redis.expire(monthKey, MONTH_TTL_DAYS, TimeUnit.DAYS);

            // ③ 成本
            double cost = costCalculator.calculate(modelName, usage);
            String dayCostKey = costDayKey(userId);
            redis.opsForValue().increment(dayCostKey, cost);
            redis.expire(dayCostKey, DAY_TTL_DAYS, TimeUnit.DAYS);

            String monthCostKey = costMonthKey(userId);
            redis.opsForValue().increment(monthCostKey, cost);
            redis.expire(monthCostKey, MONTH_TTL_DAYS, TimeUnit.DAYS);

            // ④ 按模型统计
            String modelKey = "ai:token:model:" + userId + ":" + LocalDate.now().format(DAY_FMT);
            redis.opsForHash().increment(modelKey, modelName, usage.totalTokens());
            redis.expire(modelKey, DAY_TTL_DAYS, TimeUnit.DAYS);

            // ⑤ 详细记录 (前 100 条)
            TokenUsageRecord rec = TokenUsageRecord.builder()
                    .recordId(UUID.randomUUID().toString())
                    .userId(userId)
                    .sessionId(sessionId)
                    .runId(runId)
                    .modelName(modelName)
                    .inputTokens(usage.inputTokens())
                    .outputTokens(usage.outputTokens())
                    .totalTokens(usage.totalTokens())
                    .cachedTokens(usage.cachedTokens())
                    .costCny(cost)
                    .timestamp(System.currentTimeMillis())
                    .build();
            String recordKey = "ai:token:records:" + userId + ":" + LocalDate.now().format(DAY_FMT);
            redis.opsForList().leftPush(recordKey, MAPPER.writeValueAsString(rec));
            redis.opsForList().trim(recordKey, 0, MAX_RECORDS_PER_DAY - 1);
            redis.expire(recordKey, DAY_TTL_DAYS, TimeUnit.DAYS);

            log.info("[token-record] userId={}, model={}, in={}, out={}, cached={}, cost=¥{}",
                    userId, modelName, usage.inputTokens(), usage.outputTokens(),
                    usage.cachedTokens(), String.format("%.6f", cost));

        } catch (Exception e) {
            // 计数失败不影响主流程
            log.error("[token-record] 记录失败: userId={}, model={}", userId, modelName, e);
        }
    }

    // ================ key 构造 ================
    public static String dayKey(String userId) {
        return "ai:token:usage:" + userId + ":" + LocalDate.now().format(DAY_FMT);
    }

    public static String monthKey(String userId) {
        return "ai:token:usage:" + userId + ":" + LocalDate.now().format(MONTH_FMT);
    }

    public static String costDayKey(String userId) {
        return "ai:token:cost:" + userId + ":" + LocalDate.now().format(DAY_FMT);
    }

    public static String costMonthKey(String userId) {
        return "ai:token:cost:" + userId + ":" + LocalDate.now().format(MONTH_FMT);
    }
}
```

### 6.9 `TokenQuotaService.java`

```java
package com.lenovo.ai.token;

import com.lenovo.ai.token.exception.QuotaExceededException;
import com.lenovo.ai.token.model.QuotaStatus;
import com.lenovo.ai.token.model.TokenQuota;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Map;

/**
 * 配额管理
 * 职责: 检查配额、扣减、按用户等级控制
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TokenQuotaService {

    private static final DateTimeFormatter MONTH_FMT = DateTimeFormatter.ofPattern("yyyyMM");

    private final StringRedisTemplate redis;
    private final TokenCounter tokenCounter;

    /**
     * 查询配额状态
     */
    public QuotaStatus check(String userId) {
        TokenQuota quota = loadQuota(userId);

        if (!quota.enabled()) {
            return QuotaStatus.exceeded(userId, quota.tier(), "账户已禁用");
        }

        long dailyUsed = getLong(tokenCounter.getClass() == null ? null : null, null); // placeholder

        // 日用量
        String dayKey = TokenCounter.dayKey(userId);
        Map<Object, Object> dayUsage = redis.opsForHash().entries(dayKey);
        long dUsed = parseLong(dayUsage.get("totalTokens"));
        String dayCostKey = TokenCounter.costDayKey(userId);
        String dayCostStr = redis.opsForValue().get(dayCostKey);
        double dayCost = dayCostStr == null ? 0 : Double.parseDouble(dayCostStr);

        // 月用量
        String monthKey = "ai:token:usage:" + userId + ":" + LocalDate.now().format(MONTH_FMT);
        Map<Object, Object> monthUsage = redis.opsForHash().entries(monthKey);
        long mUsed = parseLong(monthUsage.get("totalTokens"));
        String monthCostKey = "ai:token:cost:" + userId + ":" + LocalDate.now().format(MONTH_FMT);
        String monthCostStr = redis.opsForValue().get(monthCostKey);
        double monthCost = monthCostStr == null ? 0 : Double.parseDouble(monthCostStr);

        boolean exceeded = false;
        String reason = null;

        if (dUsed >= quota.dailyTokenLimit()) {
            exceeded = true;
            reason = "每日 token 配额已用尽 (" + dUsed + " / " + quota.dailyTokenLimit() + ")";
        } else if (mUsed >= quota.monthlyTokenLimit()) {
            exceeded = true;
            reason = "每月 token 配额已用尽 (" + mUsed + " / " + quota.monthlyTokenLimit() + ")";
        }

        return new QuotaStatus(
                userId, quota.tier(),
                dUsed, quota.dailyTokenLimit(), quota.dailyTokenLimit() - dUsed,
                mUsed, quota.monthlyTokenLimit(), quota.monthlyTokenLimit() - mUsed,
                dayCost, monthCost,
                exceeded, reason
        );
    }

    /**
     * 检查配额, 超限抛异常
     */
    public void checkOrThrow(String userId) {
        QuotaStatus s = check(userId);
        if (s.exceeded()) {
            throw new QuotaExceededException(userId, s.reason());
        }
    }

    /**
     * 单次请求的预检查 (基于消息长度估算, 避免超大 prompt 打爆)
     */
    public void checkSingleRequest(String userId, int estimatedTokens) {
        TokenQuota quota = loadQuota(userId);
        if (estimatedTokens > quota.maxRequestTokens()) {
            throw new QuotaExceededException(userId,
                    "单次请求过大 (" + estimatedTokens + " > " + quota.maxRequestTokens() + ")");
        }
    }

    /**
     * 加载用户配额 (从 Redis, 没有则给默认)
     */
    public TokenQuota loadQuota(String userId) {
        String key = "ai:token:quota:" + userId;
        Map<Object, Object> m = redis.opsForHash().entries(key);
        if (m == null || m.isEmpty()) {
            return TokenQuota.defaultFree(userId);
        }
        try {
            String tier = str(m.get("tier"));
            long dailyLimit = parseLong(m.get("dailyLimit"));
            long monthlyLimit = parseLong(m.get("monthlyLimit"));
            int maxReq = (int) parseLong(m.get("maxRequest"));
            boolean enabled = !"0".equals(str(m.get("enabled")));
            return new TokenQuota(userId, tier, dailyLimit, monthlyLimit, maxReq, enabled);
        } catch (Exception e) {
            log.warn("[quota] 解析配额失败, 使用默认: userId={}", userId, e);
            return TokenQuota.defaultFree(userId);
        }
    }

    /**
     * 设置用户配额 (管理端调用)
     */
    public void setQuota(TokenQuota quota) {
        String key = "ai:token:quota:" + quota.userId();
        redis.opsForHash().put(key, "tier", quota.tier());
        redis.opsForHash().put(key, "dailyLimit", String.valueOf(quota.dailyTokenLimit()));
        redis.opsForHash().put(key, "monthlyLimit", String.valueOf(quota.monthlyTokenLimit()));
        redis.opsForHash().put(key, "maxRequest", String.valueOf(quota.maxRequestTokens()));
        redis.opsForHash().put(key, "enabled", quota.enabled() ? "1" : "0");
        log.info("[quota] 设置配额: userId={}, tier={}", quota.userId(), quota.tier());
    }

    // ================ helpers ================
    private static long parseLong(Object v) {
        if (v == null) return 0;
        try {
            return Long.parseLong(String.valueOf(v));
        } catch (Exception e) {
            return 0;
        }
    }

    private static String str(Object v) {
        return v == null ? "" : String.valueOf(v);
    }
}
```

> ⚠️ 上面 `check()` 方法里有一行 `long dailyUsed = getLong(...)` 是打错了，删除那行。完整版见下面**最终整理版**。

### 6.10 `TokenQuotaService.java`（最终整理版）

```java
package com.lenovo.ai.token;

import com.lenovo.ai.token.exception.QuotaExceededException;
import com.lenovo.ai.token.model.QuotaStatus;
import com.lenovo.ai.token.model.TokenQuota;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class TokenQuotaService {

    private static final DateTimeFormatter MONTH_FMT = DateTimeFormatter.ofPattern("yyyyMM");

    private final StringRedisTemplate redis;

    /** 查询配额状态 */
    public QuotaStatus check(String userId) {
        TokenQuota quota = loadQuota(userId);

        if (!quota.enabled()) {
            return QuotaStatus.exceeded(userId, quota.tier(), "账户已禁用");
        }

        // 日用量
        String dayKey = TokenCounter.dayKey(userId);
        Map<Object, Object> dayUsage = redis.opsForHash().entries(dayKey);
        long dUsed = parseLong(dayUsage.get("totalTokens"));
        String dayCostStr = redis.opsForValue().get(TokenCounter.costDayKey(userId));
        double dayCost = dayCostStr == null ? 0 : Double.parseDouble(dayCostStr);

        // 月用量
        String monthKey = "ai:token:usage:" + userId + ":" + LocalDate.now().format(MONTH_FMT);
        Map<Object, Object> monthUsage = redis.opsForHash().entries(monthKey);
        long mUsed = parseLong(monthUsage.get("totalTokens"));
        String monthCostStr = redis.opsForValue().get(
                "ai:token:cost:" + userId + ":" + LocalDate.now().format(MONTH_FMT));
        double monthCost = monthCostStr == null ? 0 : Double.parseDouble(monthCostStr);

        boolean exceeded = false;
        String reason = null;
        if (dUsed >= quota.dailyTokenLimit()) {
            exceeded = true;
            reason = "每日 token 配额已用尽 (" + dUsed + " / " + quota.dailyTokenLimit() + ")";
        } else if (mUsed >= quota.monthlyTokenLimit()) {
            exceeded = true;
            reason = "每月 token 配额已用尽 (" + mUsed + " / " + quota.monthlyTokenLimit() + ")";
        }

        return new QuotaStatus(
                userId, quota.tier(),
                dUsed, quota.dailyTokenLimit(), quota.dailyTokenLimit() - dUsed,
                mUsed, quota.monthlyTokenLimit(), quota.monthlyTokenLimit() - mUsed,
                dayCost, monthCost, exceeded, reason);
    }

    public void checkOrThrow(String userId) {
        QuotaStatus s = check(userId);
        if (s.exceeded()) throw new QuotaExceededException(userId, s.reason());
    }

    public void checkSingleRequest(String userId, int estimatedTokens) {
        TokenQuota quota = loadQuota(userId);
        if (estimatedTokens > quota.maxRequestTokens()) {
            throw new QuotaExceededException(userId,
                    "单次请求过大 (" + estimatedTokens + " > " + quota.maxRequestTokens() + ")");
        }
    }

    public TokenQuota loadQuota(String userId) {
        String key = "ai:token:quota:" + userId;
        Map<Object, Object> m = redis.opsForHash().entries(key);
        if (m == null || m.isEmpty()) return TokenQuota.defaultFree(userId);
        try {
            String tier = str(m.get("tier"));
            long dailyLimit = parseLong(m.get("dailyLimit"));
            long monthlyLimit = parseLong(m.get("monthlyLimit"));
            int maxReq = (int) parseLong(m.get("maxRequest"));
            boolean enabled = !"0".equals(str(m.get("enabled")));
            return new TokenQuota(userId, tier, dailyLimit, monthlyLimit, maxReq, enabled);
        } catch (Exception e) {
            log.warn("[quota] 解析失败, 用默认: userId={}", userId, e);
            return TokenQuota.defaultFree(userId);
        }
    }

    public void setQuota(TokenQuota quota) {
        String key = "ai:token:quota:" + quota.userId();
        redis.opsForHash().put(key, "tier", quota.tier());
        redis.opsForHash().put(key, "dailyLimit", String.valueOf(quota.dailyTokenLimit()));
        redis.opsForHash().put(key, "monthlyLimit", String.valueOf(quota.monthlyTokenLimit()));
        redis.opsForHash().put(key, "maxRequest", String.valueOf(quota.maxRequestTokens()));
        redis.opsForHash().put(key, "enabled", quota.enabled() ? "1" : "0");
        log.info("[quota] 设置: userId={}, tier={}", quota.userId(), quota.tier());
    }

    private static long parseLong(Object v) {
        if (v == null) return 0;
        try {
            return Long.parseLong(String.valueOf(v));
        } catch (Exception e) {
            return 0;
        }
    }

    private static String str(Object v) {
        return v == null ? "" : String.valueOf(v);
    }
}
```

### 6.11 `ContextTrimmer.java`

```java
package com.lenovo.ai.token;

import com.lenovo.ai.llm.Message;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 上下文裁剪器
 * 策略: 保留 system + 最近 N 轮, 丢弃过老的历史
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ContextTrimmer {

    /** 默认单次 prompt 上限 */
    public static final int DEFAULT_MAX_TOKENS = 6000;

    private final TokenEstimator estimator;

    /**
     * 裁剪历史对话
     * @param systemPrompts 必须保留的 system 消息 (可为空)
     * @param history       历史消息 (按时间正序)
     * @param currentUser   本轮 user message (必须保留)
     * @param maxTokens     最大 token 数
     * @return 裁剪后的完整 messages (system + history' + user)
     */
    public List<Message> trim(List<Message> systemPrompts,
                              List<Message> history,
                              Message currentUser,
                              int maxTokens) {
        List<Message> result = new ArrayList<>();

        // ① 累加 system 消耗
        int tokens = 0;
        if (systemPrompts != null) {
            for (Message sys : systemPrompts) {
                tokens += estimator.estimate(sys.content());
                result.add(sys);
            }
        }

        // ② 保留本轮 user
        tokens += estimator.estimate(currentUser.content());

        // ③ 预留 20% 给 AI 输出
        int historyBudget = (int) ((maxTokens - tokens) * 0.8);
        if (historyBudget < 0) historyBudget = 0;

        // ④ 从后往前选历史
        List<Message> kept = new ArrayList<>();
        int used = 0;
        if (history != null && !history.isEmpty()) {
            for (int i = history.size() - 1; i >= 0; i--) {
                Message m = history.get(i);
                int t = estimator.estimate(m.content());
                if (used + t > historyBudget) break;
                kept.add(0, m);
                used += t;
            }
        }

        result.addAll(kept);
        result.add(currentUser);

        log.info("[context-trim] 原始历史={} 条, 保留={} 条, 估算 token={}",
                history == null ? 0 : history.size(), kept.size(), tokens + used);

        return result;
    }
}
```

### 6.12 `ModelRouter.java`

```java
package com.lenovo.ai.token;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 模型路由器
 * 根据 Agent / 问题复杂度选择模型
 */
@Slf4j
@Component
public class ModelRouter {

    public static final String SMALL = "qwen-turbo";
    public static final String MEDIUM = "kimi-k2.6";
    public static final String LARGE = "qwen-max";

    /**
     * 选择模型
     * @param agentName     Agent 名 (planner / knowledge / ...)
     * @param message       用户消息
     * @param historySize   历史消息数
     */
    public String select(String agentName, String message, int historySize) {
        // 规则 1: planner 复杂推理, 用大模型
        if ("planner".equalsIgnoreCase(agentName)) {
            log.debug("[model-route] planner → LARGE");
            return LARGE;
        }

        // 规则 2: 极短消息 (打招呼、确认) → 小模型
        if (message != null && message.length() < 15 && historySize < 3) {
            log.debug("[model-route] 短消息 → SMALL");
            return SMALL;
        }

        // 规则 3: 长历史 → 中模型 (更稳)
        if (historySize > 10) {
            log.debug("[model-route] 长历史 → MEDIUM");
            return MEDIUM;
        }

        // 默认
        return MEDIUM;
    }

    /** 是否支持 function call (小模型可能不支持) */
    public boolean supportsFunctionCall(String model) {
        return switch (model) {
            case SMALL -> true;  // qwen-turbo 新版支持
            default -> true;
        };
    }
}
```

### 6.13 `TokenController.java`（对外接口）

```java
package com.lenovo.controller.ai;

import com.lenovo.ai.token.model.QuotaStatus;
import com.lenovo.ai.token.quota.TokenQuotaService;
import com.lenovo.ai.token.model.TokenUsageRecord;
import com.lenovo.response.ApiResponse;
import com.lenovo.security.utils.SecurityUtils;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "Token 管理", description = "配额 / 用量 / 成本查询")
@RestController
@RequestMapping("/ai/token")
@RequiredArgsConstructor
public class TokenController {

    private final TokenQuotaService quotaService;
    private final TokenQueryService queryService;

    /** 当前用户配额状态 */
    @Operation(summary = "查询当前用户配额状态")
    @GetMapping("/quota")
    public ApiResponse<QuotaStatus> quota() {
        String userId = SecurityUtils.getCurrentUserId();
        return ApiResponse.success(quotaService.check(userId));
    }

    /** 当前用户今日用量 */
    @Operation(summary = "查询今日 token 用量明细")
    @GetMapping("/usage/today")
    public ApiResponse<TokenUsageSummary> usageToday() {
        String userId = SecurityUtils.getCurrentUserId();
        return ApiResponse.success(queryService.todaySummary(userId));
    }

    /** 用户最近记录 */
    @Operation(summary = "查询最近 token 使用记录")
    @GetMapping("/records")
    public ApiResponse<List<TokenUsageRecord>> records(
            @RequestParam(defaultValue = "50") int limit) {
        String userId = SecurityUtils.getCurrentUserId();
        return ApiResponse.success(queryService.recentRecords(userId, limit));
    }
}
```

### 6.14 `TokenQueryService.java`（查询服务）

```java
package com.lenovo.ai.token;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lenovo.ai.token.model.TokenUsageRecord;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class TokenQueryService {

    private static final DateTimeFormatter DAY_FMT = DateTimeFormatter.ofPattern("yyyyMMdd");
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final StringRedisTemplate redis;

    /** 今日汇总 */
    public TokenUsageSummary todaySummary(String userId) {
        String key = TokenCounter.dayKey(userId);
        Map<Object, Object> m = redis.opsForHash().entries(key);
        long input = parseLong(m.get("inputTokens"));
        long output = parseLong(m.get("outputTokens"));
        long total = parseLong(m.get("totalTokens"));
        long cached = parseLong(m.get("cachedTokens"));
        long reqCount = parseLong(m.get("requestCount"));

        String costStr = redis.opsForValue().get(TokenCounter.costDayKey(userId));
        double cost = costStr == null ? 0 : Double.parseDouble(costStr);

        // 按模型统计
        String modelKey = "ai:token:model:" + userId + ":" + LocalDate.now().format(DAY_FMT);
        Map<Object, Object> modelMap = redis.opsForHash().entries(modelKey);
        java.util.Map<String, Long> byModel = new java.util.HashMap<>();
        modelMap.forEach((k, v) -> byModel.put(String.valueOf(k), parseLong(v)));

        return new TokenUsageSummary(input, output, total, cached, reqCount, cost, byModel);
    }

    /** 最近记录 */
    public List<TokenUsageRecord> recentRecords(String userId, int limit) {
        String key = "ai:token:records:" + userId + ":" + LocalDate.now().format(DAY_FMT);
        List<String> raw = redis.opsForList().range(key, 0, limit - 1);
        if (raw == null) return List.of();

        List<TokenUsageRecord> result = new ArrayList<>(raw.size());
        for (String s : raw) {
            try {
                result.add(MAPPER.readValue(s, TokenUsageRecord.class));
            } catch (Exception e) {
                log.warn("[token-query] 解析失败: {}", e.getMessage());
            }
        }
        return result;
    }

    private static long parseLong(Object v) {
        if (v == null) return 0;
        try {
            return Long.parseLong(String.valueOf(v));
        } catch (Exception e) {
            return 0;
        }
    }

    public record TokenUsageSummary(
            long inputTokens, long outputTokens, long totalTokens, long cachedTokens,
            long requestCount, double costCny, java.util.Map<String, Long> byModel) {
    }
}
```

---

## 7. 接入点与改动清单

### 7.1 改动清单

| 文件                      | 改动                                      | 作用      |
| ----------------------- | --------------------------------------- | ------- |
| `AiAgentService.chat()` | 入口调 `quotaService.checkOrThrow(userId)` | 拦截超配额请求 |
| `AiAgentRunner.run()`   | 步骤 3 后插入 `ContextTrimmer.trim`          | 上下文裁剪   |
| `AiAgentRunner.run()`   | 步骤 4 前插入 `ModelRouter.select`           | 模型路由    |
| `AgentLoop`             | LLM 响应后调 `tokenCounter.record(...)`     | 计数      |
| `AbstractAgent`         | 同上                                      | 计数      |
| `ChatRequest`           | 加 `estimatedTokens` 字段 (可选)             | 单次预检查   |
| `ContextBuilder`        | 支持传入 `maxTokens` 参数                     | 裁剪阈值可配  |

### 7.2 `AiAgentService.chat()` 改动示例

```java
public SseEmitter chat(ChatRequest request, String userId) {
    // ★ 新增: 入口检查配额
    try {
        quotaService.checkOrThrow(userId);
    } catch (QuotaExceededException e) {
        SseEmitter emitter = new SseEmitter();
        try {
            emitter.send(SseEmitter.event().name("error")
                    .data(Map.of("message", e.getReason(), "type", "quota")));
        } catch (Exception ignored) {}
        emitter.complete();
        return emitter;
    }

    // ... 原有逻辑
}
```

### 7.3 `AgentLoop` 改动示例

```java
// 每次 LLM 响应后
ChatResponse resp = model.chat(messages);

// ★ 新增: 提取 usage 并计数
TokenUsage usage = TokenUsage.from(resp.tokenUsage());
// 若有原始 JSON 响应, 从 prompt_tokens_details.cached_tokens 补 cachedTokens
tokenCounter.record(userId, sessionId, runId, modelName, usage);

// 可选: 扣减后再次校验
if (quotaService.check(userId).exceeded()) {
    throw new QuotaExceededException(userId, "配额在本次对话中超出");
}
```

### 7.4 `AiAgentRunner.run()` 改动示例

```java
// 步骤 3 前: 裁剪上下文
List<Message> messages = contextBuilder.build(sessionId, request.message(), memoryBlock);
List<Message> trimmed = contextTrimmer.trim(
        List.of(),                  // system prompts (由 Agent 内部注入)
        messages.subList(0, Math.max(0, messages.size() - 1)),
        messages.get(messages.size() - 1),
        ContextTrimmer.DEFAULT_MAX_TOKENS);

// 步骤 4 前: 模型路由
String modelName = modelRouter.select(
        agent.name(), request.message(), messages.size());

// 把 modelName 传给 Agent
ctx.setModelName(modelName);
```

---

## 8. 配置

### 8.1 `application.yml`

```yaml
token:
  enabled: true
  default-tier: FREE
  max-prompt-tokens: 6000
  context-reserve-ratio: 0.2   # 预留 20% 给输出
  cost:
    # ¥ / 千 token
    kimi-k2.6: { input: 0.012, output: 0.012 }
    qwen-turbo: { input: 0.0003, output: 0.0006 }
    qwen-max: { input: 0.02, output: 0.06 }
    text-embedding-v3: { input: 0.0007, output: 0.0 }
  quota:
    FREE:    { daily: 500000,   monthly: 5000000,   maxRequest: 8000 }
    BASIC:   { daily: 2000000,  monthly: 20000000,  maxRequest: 12000 }
    PREMIUM: { daily: 10000000, monthly: 100000000, maxRequest: 20000 }
    VIP:     { daily: -1,       monthly: -1,        maxRequest: 32000 }
```

### 8.2 加载配置到 Bean

```java
@Component
@ConfigurationProperties(prefix = "token")
@Data
public class TokenProperties {
    private boolean enabled = true;
    private String defaultTier = "FREE";
    private int maxPromptTokens = 6000;
    private double contextReserveRatio = 0.2;
    private Map<String, ModelPrice> cost = new HashMap<>();
    private Map<String, TierConfig> quota = new HashMap<>();

    @Data public static class ModelPrice { private double input; private double output; }
    @Data public static class TierConfig { private long daily; private long monthly; private int maxRequest; }
}
```

---

## 9. API 接口

| 方法    | 路径                           | 说明      | 权限  |
| ----- | ---------------------------- | ------- | --- |
| `GET` | `/ai/token/quota`            | 查当前用户配额 | 本人  |
| `GET` | `/ai/token/usage/today`      | 查今日用量   | 本人  |
| `GET` | `/ai/token/records?limit=50` | 查最近记录   | 本人  |
| `PUT` | `/ai/token/quota/{userId}`   | 设置配额    | 管理员 |
| `GET` | `/ai/token/admin/summary`    | 全局汇总    | 管理员 |

**`/ai/token/quota` 响应示例**：

```json
{
  "code": 0,
  "data": {
    "userId": "laijf2",
    "tier": "BASIC",
    "dailyUsed": 123456,
    "dailyLimit": 2000000,
    "dailyRemaining": 1876544,
    "monthlyUsed": 5000000,
    "monthlyLimit": 20000000,
    "monthlyRemaining": 15000000,
    "todayCostCny": 1.48,
    "monthCostCny": 60.32,
    "exceeded": false,
    "reason": null
  }
}
```

---

## 10. 监控指标

### 10.1 Prometheus Metrics

```java
@Component
@RequiredArgsConstructor
public class TokenMetrics {

    private final MeterRegistry registry;

    public void recordUsage(String userId, String model, TokenUsage usage, double cost) {
        registry.counter("ai_token_consumed_total",
                "model", model,
                "type", "input").increment(usage.inputTokens());
        registry.counter("ai_token_consumed_total",
                "model", model,
                "type", "output").increment(usage.outputTokens());
        registry.counter("ai_token_cost_total_cny",
                "model", model).increment(cost);
    }

    public void recordQuotaExceeded(String userId, String reason) {
        registry.counter("ai_token_quota_exceeded_total",
                "reason", reason).increment();
    }
}
```

### 10.2 关键指标

| 指标                                      | 类型      | 说明          |
| --------------------------------------- | ------- | ----------- |
| `ai_token_consumed_total{model,type}`   | Counter | 累计 token 消耗 |
| `ai_token_cost_total_cny{model}`        | Counter | 累计成本        |
| `ai_token_quota_exceeded_total{reason}` | Counter | 超配额次数       |
| `ai_token_quota_usage_ratio{userId}`    | Gauge   | 配额使用率       |
| `ai_context_trimmed_total`              | Counter | 上下文裁剪次数     |
| `ai_context_trimmed_saved_tokens`       | Counter | 裁剪节省的 token |

### 10.3 Grafana 看板建议

- 各模型 token 消耗趋势（line）
- 成本 Top 10 用户（table）
- 配额使用率分布（heatmap）
- 超配额次数（stat）

---

## 11. 测试方案

### 11.1 单元测试

```java
class TokenEstimatorTest {
    @Test
    void estimate_chinese() {
        int t = new TokenEstimator().estimate("你好世界");
        assertThat(t).isBetween(4, 8);
    }

    @Test
    void estimate_english() {
        int t = new TokenEstimator().estimate("hello world");
        assertThat(t).isBetween(2, 5);
    }
}

class CostCalculatorTest {
    @Test
    void calculate_kimi() {
        CostCalculator calc = new CostCalculator();
        double c = calc.calculate("kimi-k2.6",
                new TokenUsage(1000, 500, 1500, 0));
        // 1000/1000 * 0.012 + 500/1000 * 0.012 = 0.018
        assertThat(c).isCloseTo(0.018, offset(0.0001));
    }
}
```

### 11.2 集成测试

```java
@SpringBootTest
class TokenQuotaIntegrationTest {

    @Autowired TokenQuotaService quotaService;
    @Autowired TokenCounter counter;
    @Autowired StringRedisTemplate redis;

    private static final String UID = "test-token-user";

    @AfterEach
    void cleanup() {
        redis.delete(TokenCounter.dayKey(UID));
        redis.delete(TokenCounter.monthKey(UID));
        redis.delete("ai:token:quota:" + UID);
    }

    @Test
    void record_and_check() {
        // 记录 100 万 token
        counter.record(UID, "s1", "r1", "kimi-k2.6",
                new TokenUsage(800_000, 200_000, 1_000_000, 0));

        QuotaStatus s = quotaService.check(UID);
        assertThat(s.dailyUsed()).isEqualTo(1_000_000);
        assertThat(s.exceeded()).isTrue();  // 超过 FREE 日限额 50 万
    }

    @Test
    void setQuota_and_check() {
        quotaService.setQuota(TokenQuota.byTier(UID, TokenQuota.TIER_PREMIUM));
        counter.record(UID, "s1", "r1", "kimi-k2.6",
                new TokenUsage(800_000, 200_000, 1_000_000, 0));

        QuotaStatus s = quotaService.check(UID);
        assertThat(s.exceeded()).isFalse();  // PREMIUM 日限额 1000 万
    }
}
```

### 11.3 手工验收

1. **单用户测试**：
   
   - 用 FREE 账户问 10 次长问题（每次约 5000 token）
   - 累计 5 万 token 后仍正常
   - 累计超过 50 万后，日志应出现 `QuotaExceededException`

2. **查配额**：
   
   ```bash
   curl -H "ItCode: laijf2" http://localhost:8088/itsc/ai/token/quota
   ```

3. **看 Redis**：
   
   ```bash
   HGETALL ai:token:usage:laijf2:20261012
   GET ai:token:cost:laijf2:20261012
   ```

---

## 12. 上线检查清单

- [ ] **配置**
  
  - [ ] `token.enabled=true`
  - [ ] 价格表已填对（与阿里云账单核对）
  - [ ] 各 tier 配额合理

- [ ] **代码接入**
  
  - [ ] `AiAgentService.chat()` 已加配额检查
  - [ ] `AgentLoop` / `AbstractAgent` 已加计数
  - [ ] `ContextTrimmer` 已接入 `AiAgentRunner`
  - [ ] `ModelRouter` 已接入

- [ ] **数据**
  
  - [ ] Redis 容量已评估（1000 用户 90 天约 500 MB）
  - [ ] TTL 设置正确
  - [ ] 用户默认配额已同步

- [ ] **监控**
  
  - [ ] Prometheus 指标已暴露
  - [ ] Grafana 看板已建
  - [ ] 超配额告警已配

- [ ] **降级预案**
  
  - [ ] Redis 挂了 → 降级为不检查（还是拒绝？根据业务定）
  - [ ] 计数失败 → 不影响主流程（已用 try-catch 兜底）
  - [ ] 缓存对齐 → 每天凌晨对账

- [ ] **文档**
  
  - [ ] 用户侧：如何在管理端设置配额
  - [ ] 运维侧：如何查 Redis、如何重置配额

---

## 附录：与已有系统的接入点总结

| 已有类                  | 要改什么                                    |
| -------------------- | --------------------------------------- |
| `AiAgentService`     | 入口加 `quotaService.checkOrThrow(userId)` |
| `AiAgentRunner`      | 加 `ContextTrimmer` 和 `ModelRouter` 调用   |
| `AgentLoop`          | LLM 响应后调 `tokenCounter.record(...)`     |
| `AbstractAgent`      | 同上（如果用同一个 model 调用点）                    |
| `AiAgentController`  | 新增 `/ai/token/*` 接口                     |
| 新增 `TokenController` | 配额查询/管理接口                               |
| 新增 5 个类              | 见第 6 章                                  |

---

**文档结束。**

> 本文档给出了 Token 管理子系统的完整设计，从数据模型到代码到接入点全部覆盖。
> 落地顺序建议：
> 
> 1. 先做 **计数 + 查询**（保证能看清消耗）
> 2. 再做 **配额检查**（防止失控）
> 3. 最后做 **裁剪 + 模型路由**（优化成本和体验）
> 
> 有问题欢迎继续讨论。
