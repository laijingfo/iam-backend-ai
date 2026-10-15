package com.lenovo.ai.llm;

import java.util.List;
import java.util.Map;

/**
 * LLM 一次调用的响应。
 *
 * @param error 非 null 表示本次调用失败（上游 5xx、连接中断、流中断、超时）。
 *              <b>调用方必须检查</b>，否则失败会被当成"正常返回空内容"，
 *              导致前端收到一条空白回复。
 */
public record LlmResponse(
        String content,
        List<ToolCall> toolCalls,
        int inputTokens,
        int outputTokens,
        int totalTokens,
        int cachedTokens,
        Throwable error
) {
    public boolean hasToolCalls() {
        return toolCalls != null && !toolCalls.isEmpty();
    }

    /** 本次调用是否失败 */
    public boolean isFailed() {
        return error != null;
    }

    /** 兼容旧调用：无 usage 时填 0 */
    public static LlmResponse of(String content, List<ToolCall> toolCalls) {
        return new LlmResponse(content, toolCalls, 0, 0, 0, 0, null);
    }

    /** 兼容旧调用：4 参数版本 */
    public static LlmResponse of(String content, List<ToolCall> toolCalls,
                                 int inputTokens, int outputTokens, int totalTokens) {
        return new LlmResponse(content, toolCalls, inputTokens, outputTokens, totalTokens, 0, null);
    }

    /** 完整版本 */
    public static LlmResponse of(String content, List<ToolCall> toolCalls,
                                 int inputTokens, int outputTokens, int totalTokens,
                                 int cachedTokens) {
        return new LlmResponse(content, toolCalls, inputTokens, outputTokens,
                totalTokens, cachedTokens, null);
    }

    /**
     * 失败响应（保留已产生的 usage 与 toolCalls，便于计费与排查）。
     *
     * @param partialContent 失败前已流式产出的内容（可能为 ""）
     * @param usage          已统计到的 usage，可为 null
     * @param error          失败原因，必填
     */
    public static LlmResponse failed(String partialContent, LlmResponse usage, Throwable error) {
        if (usage == null) {
            return new LlmResponse(partialContent == null ? "" : partialContent,
                    List.of(), 0, 0, 0, 0, error);
        }
        return new LlmResponse(partialContent == null ? "" : partialContent,
                // ★ R4 修复：保留已解析出的 toolCalls，便于排查"工具调用完整但调用失败"的场景。
                //   注意：AgentLoop 里 isFailed() 优先于 hasToolCalls()，
                //   所以即使保留了 toolCalls 也不会被执行，仅作诊断信息。
                usage.toolCalls() == null ? List.of() : usage.toolCalls(),
                usage.inputTokens(), usage.outputTokens(),
                usage.totalTokens(), usage.cachedTokens(), error);
    }

    /** 失败响应（无 usage） */
    public static LlmResponse failed(String partialContent, Throwable error) {
        return failed(partialContent, null, error);
    }

    public record ToolCall(String id, String name, Map<String, Object> args) {}
}