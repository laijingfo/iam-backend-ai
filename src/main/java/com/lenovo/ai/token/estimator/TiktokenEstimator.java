package com.lenovo.ai.token.estimator;

import com.knuddels.jtokkit.Encodings;
import com.knuddels.jtokkit.api.Encoding;
import com.knuddels.jtokkit.api.EncodingRegistry;
import com.knuddels.jtokkit.api.EncodingType;
import lombok.extern.slf4j.Slf4j;

/**
 * @author laifo
 * @version 1.0
 * @date 2026-10-13
 * @project iam-backend-ai
 * @description
 * 精确估算：基于 jtokkit（OpenAI cl100k_base / o200k_base 编码）
 *
 * 注意：
 *   - 编码器初始化有开销（加载 BPE 词表，约 100ms），用静态单例
 *   - 不同模型对应不同编码，这里按"最接近"的编码近似
 *   - 中文 token 数 jtokkit 比启发式更准，但 qwen / kimi 不一定与 OpenAI 编码一致
 *
 * 启用方式：
 *   在 TokenEstimatorConfig 里注册 @Bean，
 *   或在本类加 @Component（此时 HeuristicTokenEstimator 自动让位）
 */
@Slf4j
public class TiktokenEstimator implements TokenEstimator {

    private static final EncodingRegistry REGISTRY = Encodings.newDefaultEncodingRegistry();

    /** 默认用 cl100k_base（GPT-4 / GPT-3.5-turbo 使用） */
    private final Encoding encoding;

    public TiktokenEstimator() {
        this(EncodingType.CL100K_BASE);
    }

    public TiktokenEstimator(EncodingType type) {
        this.encoding = REGISTRY.getEncoding(type);
        log.info("[token-estimator] TiktokenEstimator 初始化，编码={}", type.getName());
    }

    @Override
    public int estimate(String text) {
        if (text == null || text.isEmpty()) return 0;
        try {
            return encoding.countTokens(text);
        } catch (Exception e) {
            log.warn("[token-estimator] tiktoken 估算失败，回退字符数/2: {}", e.getMessage());
            return text.length() / 2;
        }
    }
}
