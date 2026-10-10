package com.lenovo.ai.tools.impl;

import com.lenovo.ai.tools.AiTool;
import com.lenovo.ai.tools.ToolResult;
import com.lenovo.ai.tools.context.AiToolContext;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * @author laifo
 * @version 1.0
 * @date 2026-09-30 10:06
 * @project iam-backend-ai
 * @description
 */
@Component
@RequiredArgsConstructor
public class CreateTicketTool implements AiTool {

    @Override public String name() { return "create_ticket"; }

    @Override
    public String description() {
        return "创建一个工单。当用户明确要求报障、提交工单、反馈问题时调用。";
    }

    @Override
    public Map<String, Object> parameterSchema() {
        return Map.of(
                "type", "object",
                "properties", Map.of(
                        "title", Map.of("type", "string", "description", "工单标题"),
                        "content", Map.of("type", "string", "description", "工单详细描述"),
                        "priority", Map.of("type", "string", "enum", List.of("LOW", "NORMAL", "HIGH"))
                ),
                "required", List.of("title", "content")
        );
    }

    @Override
    public Set<String> allowedRoles() { return Set.of("USER", "ADMIN"); }

    @Override
    public boolean requiresConfirmation() { return true; }

    @Override
    public ToolResult execute(Map<String, Object> args, AiToolContext ctx) {
        // 替换为你的真实工单服务
        String id = "TICKET-" + UUID.randomUUID().toString().substring(0, 8);
        return ToolResult.ok("工单已创建，编号: " + id, Map.of("ticketId", id));
    }
}
