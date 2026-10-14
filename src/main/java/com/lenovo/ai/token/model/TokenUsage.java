package com.lenovo.ai.token.model;

import com.lenovo.ai.llm.LlmResponse;

/**
 * @author laifo
 * @version 1.0
 * @date 2026-10-12 17:14
 * @project iam-backend-ai
 * @description
 * Token 用量快照 (对 LangChain4j TokenUsage 的封装)
 */
public record TokenUsage(
        int inputTokens,
        int outputTokens,
        int totalTokens,
        int cachedTokens
) {

    public static TokenUsage zero() {
        return new TokenUsage(0, 0, 0, 0);
    }

    /**
     * 从 LangChain4j 的 TokenUsage 转换
     * 兼容 dev.langchain4j.model.output.TokenUsage
     */
    public static TokenUsage from(dev.langchain4j.model.output.TokenUsage lc) {
        if (lc == null) return zero();
        Integer in = lc.inputTokenCount();
        Integer out = lc.outputTokenCount();
        Integer total = lc.totalTokenCount();
        return new TokenUsage(
                in == null ? 0 : in,
                out == null ? 0 : out,
                total == null ? 0 : total,
                0    // LangChain4j 不直接暴露 cachedTokens, 由调用方从原始响应解析
        );
    }

    public TokenUsage withCached(int cached) {
        return new TokenUsage(inputTokens, outputTokens, totalTokens, cached);
    }

    public static TokenUsage fromLlmResponse(LlmResponse resp) {
        if (resp == null) return zero();
        return new TokenUsage(
                resp.inputTokens(),
                resp.outputTokens(),
                resp.totalTokens(),
                resp.cachedTokens()    // ★ 使用真实值
        );
    }
}