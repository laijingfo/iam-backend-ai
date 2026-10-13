package com.lenovo.ai.token.counter;

import com.lenovo.ai.token.model.TokenUsage;

/**
 * @author laifo
 * @version 1.0
 * @date 2026-10-13 11:24
 * @project iam-backend-ai
 * @description
 */
public interface TokenCounter {
    void record(String userId, String sessionId, String runId, String modelName, TokenUsage usage);
}
