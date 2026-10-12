package com.lenovo.ai.agent;

/**
 * @author laifo
 * @version 1.0
 * @date 2026-10-12 14:12
 * @project iam-backend-ai
 * @description
 */
public record ChatRequest(
        String sessionId,
        String message,
        String knowledgeId,
        String clientRequestId,
        String agentName          // 前端可选指定
) {}
