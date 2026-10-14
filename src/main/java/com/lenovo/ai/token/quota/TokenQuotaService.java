package com.lenovo.ai.token.quota;

import com.lenovo.ai.token.config.TokenProperties;
import com.lenovo.ai.token.constant.TokenRedisKeys;
import com.lenovo.ai.token.exception.QuotaExceededException;
import com.lenovo.ai.token.metrics.TokenMetrics;
import com.lenovo.ai.token.model.QuotaStatus;
import com.lenovo.ai.token.model.TokenQuota;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class TokenQuotaService {

    private static final byte[] FIELD_COST_B =
            TokenRedisKeys.FIELD_COST.getBytes(StandardCharsets.UTF_8);

    private final StringRedisTemplate redis;
    private final QuotaResolver quotaResolver;
    private final TokenProperties properties;
    private final TokenMetrics tokenMetrics;   // ★ 新增

    public QuotaStatus check(String userId) {
        TokenQuota quota = loadQuota(userId);

        if (!quota.enabled()) {
            return QuotaStatus.exceeded(userId, quota.tier(), "账户已禁用");
        }

        String dayKey = TokenRedisKeys.usageDay(userId);
        String monthKey = TokenRedisKeys.usageMonth(userId);
        String costDayKey = TokenRedisKeys.costDay(userId);
        String costMonthKey = TokenRedisKeys.costMonth(userId);

        // ★ pipeline：一次往返取 4 个值
        List<Object> results = redis.executePipelined((RedisCallback<Object>) connection -> {
            connection.hGetAll(dayKey.getBytes(StandardCharsets.UTF_8));
            connection.hGetAll(monthKey.getBytes(StandardCharsets.UTF_8));
            connection.hGet(costDayKey.getBytes(StandardCharsets.UTF_8), FIELD_COST_B);
            connection.hGet(costMonthKey.getBytes(StandardCharsets.UTF_8), FIELD_COST_B);
            return null;
        });

        long dUsed = 0, mUsed = 0;
        double dayCost = 0.0, monthCost = 0.0;

        if (results != null && results.size() >= 4) {
            dUsed = parseTotalTokens(results.get(0));
            mUsed = parseTotalTokens(results.get(1));
            dayCost = parseCostMicro(results.get(2));
            monthCost = parseCostMicro(results.get(3));
        }

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
        if (s.exceeded()) {
            try {
                tokenMetrics.recordQuotaExceeded(userId, s.reason());
            } catch (Exception me) {
                log.warn("[quota] 指标上报失败", me);
            }
            throw new QuotaExceededException(userId, s.reason());
        }
    }

    public void checkSingleRequest(String userId, int estimatedTokens) {
        TokenQuota quota = loadQuota(userId);
        if (estimatedTokens > quota.maxRequestTokens()) {
            throw new QuotaExceededException(userId,
                    "单次请求过大 (" + estimatedTokens + " > " + quota.maxRequestTokens() + ")");
        }
    }

    public TokenQuota loadQuota(String userId) {
        String key = TokenRedisKeys.quota(userId);
        Map<Object, Object> m = redis.opsForHash().entries(key);

        if (m == null || m.isEmpty() || !m.containsKey("monthlyLimit")) {
            log.info("[quota] 用户 {} 无有效配额，走默认 tier={}", userId, properties.getDefaultTier());
            return quotaResolver.byTier(userId, properties.getDefaultTier());
        }

        try {
            String tier = str(m.get("tier"));
            long dailyLimit = parseLong(m.get("dailyLimit"));
            long monthlyLimit = parseLong(m.get("monthlyLimit"));
            int maxReq = (int) parseLong(m.get("maxRequest"));
            boolean enabled = !"0".equals(str(m.get("enabled")));
            return new TokenQuota(userId, tier, dailyLimit, monthlyLimit, maxReq, enabled);
        } catch (Exception e) {
            log.warn("[quota] 解析失败, 用默认: userId={}", userId, e);
            return quotaResolver.byTier(userId, properties.getDefaultTier());
        }
    }

    public void setQuota(TokenQuota quota) {
        String key = TokenRedisKeys.quota(quota.userId());
        redis.opsForHash().put(key, "tier", quota.tier());
        redis.opsForHash().put(key, "dailyLimit", String.valueOf(quota.dailyTokenLimit()));
        redis.opsForHash().put(key, "monthlyLimit", String.valueOf(quota.monthlyTokenLimit()));
        redis.opsForHash().put(key, "maxRequest", String.valueOf(quota.maxRequestTokens()));
        redis.opsForHash().put(key, "enabled", quota.enabled() ? "1" : "0");
        log.info("[quota] 设置: userId={}, tier={}", quota.userId(), quota.tier());
    }

    // ===== 工具 =====

    private static long parseTotalTokens(Object raw) {
        if (!(raw instanceof Map<?, ?> m)) return 0;
        Object v = m.get("totalTokens");
        if (v == null) {
            // byte[] key 的情况
            for (Map.Entry<?, ?> e : m.entrySet()) {
                String k = e.getKey() instanceof byte[] b
                        ? new String(b, StandardCharsets.UTF_8)
                        : String.valueOf(e.getKey());
                if ("totalTokens".equals(k)) return parseLong(e.getValue());
            }
            return 0;
        }
        return parseLong(v);
    }

    private static double parseCostMicro(Object raw) {
        if (raw == null) return 0.0;
        try {
            String s = raw instanceof byte[] b
                    ? new String(b, StandardCharsets.UTF_8)
                    : String.valueOf(raw);
            return Long.parseLong(s) / (double) TokenRedisKeys.MICRO_CNY;
        } catch (NumberFormatException e) {
            return 0.0;
        }
    }

    private static long parseLong(Object v) {
        if (v == null) return 0;
        try {
            String s = v instanceof byte[] b
                    ? new String(b, StandardCharsets.UTF_8)
                    : String.valueOf(v);
            return Long.parseLong(s);
        } catch (Exception e) { return 0; }
    }

    private static String str(Object v) { return v == null ? "" : String.valueOf(v); }
}