package com.lenovo.ai.token;


import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
/**
 * @author laifo
 * @version 1.0
 * @date 2026-10-12 17:19
 * @project iam-backend-ai
 * @description
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