package com.lenovo.ai.agent.blackboard;

import java.util.Map;
/**
 * @author laifo
 * @version 1.0
 * @date 2026-10-12 13:55
 * @project iam-backend-ai
 * @description
 */
public record AgentTask(
        String taskId,
        String agentName,     // 指派给哪个 Agent
        String instruction,   // 任务描述
        Map<String, Object> input
) {}
