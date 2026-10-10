package com.lenovo.ai.tools;

import com.lenovo.ai.tools.context.AiToolContext;

import java.util.Map;
import java.util.Set;

/**
 * @author laifo
 * @version 1.0
 * @date 2026-09-30 10:04
 * @project iam-backend-ai
 * @description
 */
public interface AiTool {
    String name();
    String description();
    Map<String, Object> parameterSchema();
    ToolResult execute(Map<String, Object> args, AiToolContext ctx);
    default Set<String> allowedRoles() { return Set.of("*"); }
    default boolean requiresConfirmation() { return false; }
}
