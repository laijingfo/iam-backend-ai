package com.lenovo.ai.token.model;

import lombok.Builder;

/**
 * @author laifo
 * @version 1.0
 * @date 2026-10-12 17:14
 * @project iam-backend-ai
 * @description
 * 单次 LLM 调用的 token 用量明细记录
 */
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