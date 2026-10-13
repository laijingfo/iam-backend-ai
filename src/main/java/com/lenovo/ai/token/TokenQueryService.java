package com.lenovo.ai.token;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;


/**
 * @author laifo
 * @version 1.0
 * @date 2026-10-12 17:35
 * @project iam-backend-ai
 * @description
 */
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
        try { return Long.parseLong(String.valueOf(v)); } catch (Exception e) { return 0; }
    }

    public record TokenUsageSummary(
            long inputTokens, long outputTokens, long totalTokens, long cachedTokens,
            long requestCount, double costCny, java.util.Map<String, Long> byModel) {}
}