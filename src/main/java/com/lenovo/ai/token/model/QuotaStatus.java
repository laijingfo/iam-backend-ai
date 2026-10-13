package com.lenovo.ai.token.model;

/**
 * @author laifo
 * @version 1.0
 * @date 2026-10-12 17:15
 * @project iam-backend-ai
 * @description
 * 用户 token 配额状态快照
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

    /**
     * 构造一个"已超限"的状态，用于账户禁用 / 无法读取配额等场景
     */
    public static QuotaStatus exceeded(String userId, String tier, String reason) {
        return new QuotaStatus(
                userId, tier,
                0, 0, 0,
                0, 0, 0,
                0, 0,
                true, reason);
    }
}