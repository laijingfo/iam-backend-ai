package com.lenovo.ai.agent.router;

import com.lenovo.ai.agent.core.Agent;
/**
 * @author laifo
 * @version 1.0
 * @date 2026-10-12 13:53
 * @project iam-backend-ai
 * @description
 */
public interface AgentRouter {
    Agent route(RoutingRequest req);
}
