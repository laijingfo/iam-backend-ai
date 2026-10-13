package com.lenovo.ai.token.query;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lenovo.ai.token.constant.TokenRedisKeys;
import com.lenovo.ai.token.model.TokenUsageRecord;
import com.lenovo.ai.token.model.TokenUsageSummary;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class TokenQueryService {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final StringRedisTemplate redis;

    /** 今日汇总 */
    public TokenUsageSummary todaySummary(String userId) {
        String key = TokenRedisKeys.usageDay(userId);
        Map<Object, Object> m = redis.opsForHash().entries(key);
        long input = parseLong(m.get("inputTokens"));
        long output = parseLong(m.get("outputTokens"));
        long total = parseLong(m.get("totalTokens"));
        long cached = parseLong(m.get("cachedTokens"));
        long reqCount = parseLong(m.get("requestCount"));

        // ✅ 成本改为读 hash field = cny，单位微元 -> 元
        double cost = readCostMicro(redis, TokenRedisKeys.costDay(userId));

        // 按模型统计
        String modelKey = TokenRedisKeys.modelDay(userId);
        Map<Object, Object> modelMap = redis.opsForHash().entries(modelKey);
        Map<String, Long> byModel = new HashMap<>();
        modelMap.forEach((k, v) -> byModel.put(String.valueOf(k), parseLong(v)));

        return new TokenUsageSummary(input, output, total, cached, reqCount, cost, byModel);
    }

    /** 最近记录 */
    public List<TokenUsageRecord> recentRecords(String userId, int limit) {
        String key = TokenRedisKeys.recordsDay(userId);
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

    /** 从 hash 读成本（微元 -> 元） */
    private static double readCostMicro(StringRedisTemplate redis, String key) {
        Object v = redis.opsForHash().get(key, TokenRedisKeys.FIELD_COST);
        if (v == null) return 0.0;
        try {
            return Long.parseLong(String.valueOf(v)) / (double) TokenRedisKeys.MICRO_CNY;
        } catch (NumberFormatException e) {
            return 0.0;
        }
    }

    private static long parseLong(Object v) {
        if (v == null) return 0;
        try { return Long.parseLong(String.valueOf(v)); } catch (Exception e) { return 0; }
    }
}