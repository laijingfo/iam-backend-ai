package com.lenovo.ai.llm;

import java.util.List;
import java.util.Map;

/**
 * @author laifo
 * @version 1.0
 * @date 2026-09-30 10:09
 * @project iam-backend-ai
 * @description
 */
public record LlmResponse(
        String content,
        List<ToolCall> toolCalls
) {
    public boolean hasToolCalls() { return toolCalls != null && !toolCalls.isEmpty(); }

    public record ToolCall(String id, String name, Map<String, Object> args) {}
}
