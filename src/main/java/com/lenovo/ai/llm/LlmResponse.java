package com.lenovo.ai.llm;

import java.util.List;
import java.util.Map;

/**
 * LLM 响应
 * - content      : 文本内容
 * - toolCalls    : 工具调用列表（可能为空）
 * - inputTokens  : 输入 token 数（LangChain4j tokenUsage）
 * - outputTokens : 输出 token 数
 * - totalTokens  : 总 token 数
 */
public record LlmResponse(
        String content,
        List<ToolCall> toolCalls,
        int inputTokens,
        int outputTokens,
        int totalTokens
) {
    public boolean hasToolCalls() {
        return toolCalls != null && !toolCalls.isEmpty();
    }

    /** 兼容旧调用：无 usage 时填 0 */
    public static LlmResponse of(String content, List<ToolCall> toolCalls) {
        return new LlmResponse(content, toolCalls, 0, 0, 0);
    }

    public static LlmResponse of(String content, List<ToolCall> toolCalls,
                                 int inputTokens, int outputTokens, int totalTokens) {
        return new LlmResponse(content, toolCalls, inputTokens, outputTokens, totalTokens);
    }

    /**
     * ★ 唯一结构性改动：args 从 String → Map
     * 这样 AgentLoop 里的 tool.execute(call.args(), ...) 和 Message.ToolCallRef(...) 都能编译通过
     */
    public record ToolCall(String id, String name, Map<String, Object> args) {}
}