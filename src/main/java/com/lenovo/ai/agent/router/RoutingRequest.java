package com.lenovo.ai.agent.router;

import java.util.Set;

public record RoutingRequest(
        String question,
        String userId,
        String sessionId,
        String explicitAgent,   // 前端可指定，优先级最高
        Set<String> userRoles   // 用于权限过滤 Agent
) {}