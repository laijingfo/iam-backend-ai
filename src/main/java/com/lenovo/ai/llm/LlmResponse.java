package com.lenovo.ai.llm;

import java.util.List;
import java.util.Map;

public record LlmResponse(
        String content,
        List<ToolCall> toolCalls,
        int inputTokens,
        int outputTokens,
        int totalTokens,
        int cachedTokens      // ★ 新增
) {
    public boolean hasToolCalls() {
        return toolCalls != null && !toolCalls.isEmpty();
    }

    /** 兼容旧调用：无 usage 时填 0 */
    public static LlmResponse of(String content, List<ToolCall> toolCalls) {
        return new LlmResponse(content, toolCalls, 0, 0, 0, 0);
    }

    /** 兼容旧调用：4 参数版本 */
    public static LlmResponse of(String content, List<ToolCall> toolCalls,
                                 int inputTokens, int outputTokens, int totalTokens) {
        return new LlmResponse(content, toolCalls, inputTokens, outputTokens, totalTokens, 0);
    }

    /** 完整版本 */
    public static LlmResponse of(String content, List<ToolCall> toolCalls,
                                 int inputTokens, int outputTokens, int totalTokens,
                                 int cachedTokens) {
        return new LlmResponse(content, toolCalls, inputTokens, outputTokens,
                totalTokens, cachedTokens);
    }

    public record ToolCall(String id, String name, Map<String, Object> args) {}
}