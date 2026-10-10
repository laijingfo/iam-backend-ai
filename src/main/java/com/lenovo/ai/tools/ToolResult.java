package com.lenovo.ai.tools;
import java.util.Map;

/**
 * @author laifo
 * @version 1.0
 * @date 2026-09-30 10:05
 * @project iam-backend-ai
 * @description
 */
public record ToolResult(boolean success, String content, Map<String, Object> meta) {
    public static ToolResult ok(String content) { return new ToolResult(true, content, Map.of()); }
    public static ToolResult ok(String content, Map<String, Object> meta) { return new ToolResult(true, content, meta); }
    public static ToolResult fail(String msg) { return new ToolResult(false, msg, Map.of()); }
}
