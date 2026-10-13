package com.lenovo.ai.token.quota;

import com.lenovo.ai.token.config.TokenProperties;
import com.lenovo.ai.token.constant.TokenRedisKeys;
import com.lenovo.ai.token.exception.QuotaExceededException;
import com.lenovo.ai.token.model.QuotaStatus;
import com.lenovo.ai.token.model.TokenQuota;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class TokenQuotaService {

    private final StringRedisTemplate redis;
    private final QuotaResolver quotaResolver;
    private final TokenProperties properties;

    /** 查询配额状态 */
    public QuotaStatus check(String userId) {
        TokenQuota quota = loadQuota(userId);

        if (!quota.enabled()) {
            return QuotaStatus.exceeded(userId, quota.tier(), "账户已禁用");
        }

        // 日用量
        String dayKey = TokenRedisKeys.usageDay(userId);
        Map<Object, Object> dayUsage = redis.opsForHash().entries(dayKey);
        long dUsed = parseLong(dayUsage.get("totalTokens"));
        double dayCost = readCostMicro(TokenRedisKeys.costDay(userId));

        // 月用量
        String monthKey = TokenRedisKeys.usageMonth(userId);
        Map<Object, Object> monthUsage = redis.opsForHash().entries(monthKey);
        long mUsed = parseLong(monthUsage.get("totalTokens"));
        double monthCost = readCostMicro(TokenRedisKeys.costMonth(userId));

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
        String key = TokenRedisKeys.quota(userId);
        Map<Object, Object> m = redis.opsForHash().entries(key);

        // ★ 关键：key 不存在 OR 字段缺失时，都走默认
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

    /** 从 hash 读成本（微元 -> 元） */
    private double readCostMicro(String key) {
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
    private static String str(Object v) { return v == null ? "" : String.valueOf(v); }
}