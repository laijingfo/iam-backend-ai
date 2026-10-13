package com.lenovo.ai.token;

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
