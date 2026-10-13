package com.lenovo.ai.token;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Map;

/**
 * @author laifo
 * @version 1.0
 * @date 2026-10-12 17:20
 * @project iam-backend-ai
 * @description
 * 配额管理
 * 职责: 检查配额、扣减、按用户等级控制
 */
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
        try { return Long.parseLong(String.valueOf(v)); } catch (Exception e) { return 0; }
    }
    private static String str(Object v) { return v == null ? "" : String.valueOf(v); }
}