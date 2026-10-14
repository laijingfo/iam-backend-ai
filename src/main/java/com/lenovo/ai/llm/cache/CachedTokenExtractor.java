package com.lenovo.ai.llm.cache;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;

/**
 * 从 OpenAI 兼容协议的原始响应中提取 cachedTokens。
 * <p>兼容字段路径：</p>
 * <ul>
 *   <li>{@code usage.prompt_tokens_details.cached_tokens}（OpenAI 标准）</li>
 *   <li>{@code usage.cached_tokens}（部分国内厂商）</li>
 *   <li>{@code usage.prompt_cache_hit_tokens}（DeepSeek）</li>
 *   <li>{@code usage.cache_read_input_tokens}（Anthropic 风格）</li>
 * </ul>
 */
@Slf4j
public final class CachedTokenExtractor {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private CachedTokenExtractor() {}

    public static int extract(String rawJson) {
        if (rawJson == null || rawJson.isBlank()) return 0;
        try {
            JsonNode root = MAPPER.readTree(rawJson);
            return extractFromNode(root);
        } catch (Exception e) {
            log.debug("解析 cachedTokens 失败: {}", e.getMessage());
            return 0;
        }
    }

    public static int extractFromNode(JsonNode root) {
        if (root == null) return 0;

        JsonNode usage = root.path("usage");
        if (usage.isMissingNode()) {
            usage = root;   // 可能 root 本身就是 usage
        }

        // 路径 1：OpenAI 标准 usage.prompt_tokens_details.cached_tokens
        JsonNode details = usage.path("prompt_tokens_details");
        if (!details.isMissingNode()) {
            int v = details.path("cached_tokens").asInt(0);
            if (v > 0) return v;
        }

        // 路径 2：usage.cached_tokens
        int v = usage.path("cached_tokens").asInt(0);
        if (v > 0) return v;

        // 路径 3：DeepSeek usage.prompt_cache_hit_tokens
        v = usage.path("prompt_cache_hit_tokens").asInt(0);
        if (v > 0) return v;

        // 路径 4：Anthropic usage.cache_read_input_tokens
        v = usage.path("cache_read_input_tokens").asInt(0);
        if (v > 0) return v;

        return 0;
    }
}