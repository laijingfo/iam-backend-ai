package com.lenovo.ai.agent.blackboard;

import java.util.Map;
/**
 * @author laifo
 * @version 1.0
 * @date 2026-10-12 13:55
 * @project iam-backend-ai
 * @description
 */
public record AgentResult(
        String taskId,
        String agentName,
        boolean success,
        String output,
        Map<String, Object> meta
) {}
