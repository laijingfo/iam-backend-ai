package com.lenovo.ai.token;

/**
 * @author laifo
 * @version 1.0
 * @date 2026-10-12 17:14
 * @project iam-backend-ai
 * @description
 */
import lombok.Builder;

@Builder
public record TokenUsageRecord(
        String recordId,
        String userId,
        String sessionId,
        String runId,
        String modelName,
        int inputTokens,
        int outputTokens,
        int totalTokens,
        int cachedTokens,
        double costCny,
        long timestamp
) {}
