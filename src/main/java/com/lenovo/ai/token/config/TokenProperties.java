package com.lenovo.ai.token.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

@Component
@ConfigurationProperties(prefix = "token")
@Data
public class TokenProperties {

    private boolean enabled = true;
    private String defaultTier = "FREE";
    private int maxPromptTokens = 6000;
    private double contextReserveRatio = 0.2;

    /** 模型价格表：modelName -> {input, output} */
    private Map<String, ModelPrice> cost = new HashMap<>();

    /** 用户等级配额表：tier -> {daily, monthly, maxRequest} */
    private Map<String, TierConfig> quota = new HashMap<>();

    /**
     * 配置用价格（与 token.model.ModelPrice 解耦，
     * 避免配置类直接依赖领域模型）
     */
    @Data
    public static class ModelPrice {
        private double input;
        private double output;
    }

    @Data
    public static class TierConfig {
        private long daily;
        private long monthly;
        private int maxRequest;
    }
}