package com.lenovo.ai.token.counter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lenovo.ai.token.config.TokenProperties;
import com.lenovo.ai.token.constant.TokenRedisKeys;
import com.lenovo.ai.token.cost.CostCalculator;
import com.lenovo.ai.token.model.TokenUsage;
import com.lenovo.ai.token.model.TokenUsageRecord;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Slf4j
@Component
@RequiredArgsConstructor
public class RedisTokenCounter implements TokenCounter {

    private static final long DAY_TTL_DAYS = 90L;
    private static final long MONTH_TTL_DAYS = 400L;
    private static final int MAX_RECORDS_PER_DAY = 100;
//    private static final ObjectMapper MAPPER = new ObjectMapper();
    private final ObjectMapper objectMapper;
    private static final byte[] FIELD_COST_B =
            TokenRedisKeys.FIELD_COST.getBytes(StandardCharsets.UTF_8);

    private final StringRedisTemplate redis;
    private final CostCalculator costCalculator;
    private final TokenProperties properties;

    @Override
    public void record(String userId, String sessionId, String runId,
                       String modelName, TokenUsage usage) {
        if (!properties.isEnabled() || usage == null || usage.totalTokens() <= 0) return;

        try {
            double cost = costCalculator.calculate(modelName, usage);
            // 元 -> 微元，四舍五入（原 (long) 强转是截断，会累积偏差）
            long costMicro = Math.round(cost * TokenRedisKeys.MICRO_CNY);

            TokenUsageRecord rec = TokenUsageRecord.builder()
                    .recordId(UUID.randomUUID().toString())
                    .userId(userId).sessionId(sessionId).runId(runId)
                    .modelName(modelName)
                    .inputTokens(usage.inputTokens())
                    .outputTokens(usage.outputTokens())
                    .totalTokens(usage.totalTokens())
                    .cachedTokens(usage.cachedTokens())
                    .costCny(cost)
                    .timestamp(System.currentTimeMillis())
                    .build();
            String recJson = objectMapper.writeValueAsString(rec);

            String dayKey   = TokenRedisKeys.usageDay(userId);
            String monthKey = TokenRedisKeys.usageMonth(userId);
            String modelKey = TokenRedisKeys.modelDay(userId);
            String costDay  = TokenRedisKeys.costDay(userId);
            String costMon  = TokenRedisKeys.costMonth(userId);
            String recKey   = TokenRedisKeys.recordsDay(userId);

            redis.executePipelined((RedisCallback<Object>) connection -> {
                byte[] dayKeyB   = dayKey.getBytes(StandardCharsets.UTF_8);
                byte[] monthKeyB = monthKey.getBytes(StandardCharsets.UTF_8);
                byte[] modelKeyB = modelKey.getBytes(StandardCharsets.UTF_8);
                byte[] costDayB  = costDay.getBytes(StandardCharsets.UTF_8);
                byte[] costMonB  = costMon.getBytes(StandardCharsets.UTF_8);
                byte[] recKeyB   = recKey.getBytes(StandardCharsets.UTF_8);

                // 日 / 月 用量
                incrHash(connection, dayKeyB, usage);
                incrHash(connection, monthKeyB, usage);

                // 日 / 月 成本（hash field = cny，单位微元）
                connection.hIncrBy(costDayB, FIELD_COST_B, costMicro);
                connection.hIncrBy(costMonB, FIELD_COST_B, costMicro);

                // 模型维度
                connection.hIncrBy(modelKeyB, modelName.getBytes(StandardCharsets.UTF_8), usage.totalTokens());

                // 明细
                connection.lPush(recKeyB, recJson.getBytes(StandardCharsets.UTF_8));
                connection.lTrim(recKeyB, 0, MAX_RECORDS_PER_DAY - 1);

                // TTL
                connection.expire(dayKeyB, TimeUnit.DAYS.toSeconds(DAY_TTL_DAYS));
                connection.expire(monthKeyB, TimeUnit.DAYS.toSeconds(MONTH_TTL_DAYS));
                connection.expire(modelKeyB, TimeUnit.DAYS.toSeconds(DAY_TTL_DAYS));
                connection.expire(costDayB, TimeUnit.DAYS.toSeconds(DAY_TTL_DAYS));
                connection.expire(costMonB, TimeUnit.DAYS.toSeconds(MONTH_TTL_DAYS));
                connection.expire(recKeyB, TimeUnit.DAYS.toSeconds(DAY_TTL_DAYS));
                return null;
            });

            log.info("[token-record] userId={}, model={}, in={}, out={}, cached={}, cost=¥{}",
                    userId, modelName, usage.inputTokens(), usage.outputTokens(),
                    usage.cachedTokens(), String.format("%.6f", cost));
        } catch (Exception e) {
            log.error("[token-record] 记录失败: userId={}, model={}", userId, modelName, e);
        }
    }

    private static void incrHash(RedisConnection connection, byte[] key, TokenUsage u) {
        connection.hIncrBy(key, "inputTokens".getBytes(StandardCharsets.UTF_8),  u.inputTokens());
        connection.hIncrBy(key, "outputTokens".getBytes(StandardCharsets.UTF_8), u.outputTokens());
        connection.hIncrBy(key, "totalTokens".getBytes(StandardCharsets.UTF_8),  u.totalTokens());
        connection.hIncrBy(key, "cachedTokens".getBytes(StandardCharsets.UTF_8), u.cachedTokens());
        connection.hIncrBy(key, "requestCount".getBytes(StandardCharsets.UTF_8), 1L);
    }
}