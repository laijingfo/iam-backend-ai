package com.lenovo.ai.token;

/**
 * @author laifo
 * @version 1.0
 * @date 2026-10-12 17:15
 * @project iam-backend-ai
 * @description
 */
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
