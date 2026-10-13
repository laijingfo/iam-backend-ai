package com.lenovo.ai.token.model;

/**
 * @author laifo
 * @version 1.0
 * @date 2026-10-12 17:14
 * @project iam-backend-ai
 * @description
 */
public record TokenQuota(
        String userId,
        String tier,
        long dailyTokenLimit,
        long monthlyTokenLimit,
        int maxRequestTokens,
        boolean enabled
) {
    public static final String TIER_FREE     = "FREE";
    public static final String TIER_BASIC    = "BASIC";
    public static final String TIER_PREMIUM  = "PREMIUM";
    public static final String TIER_VIP      = "VIP";
    public static final String TIER_DISABLED = "DISABLED";
    // defaultFree / byTier 已删除，改由 QuotaResolver 提供
}