package com.lenovo.ai.token.model;

import java.util.Map;

/**
 * @author laifo
 * @version 1.0
 * @date 2026-10-13
 * @project iam-backend-ai
 * @description 今日 token 用量汇总（从 TokenQueryService 内部 record 提升）
 */
public record TokenUsageSummary(
        long inputTokens,
        long outputTokens,
        long totalTokens,
        long cachedTokens,
        long requestCount,
        double costCny,
        Map<String, Long> byModel
) {}
