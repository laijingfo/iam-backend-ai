package com.lenovo.ai.token;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

/**
 * @author laifo
 * @version 1.0
 * @date 2026-10-12 17:38
 * @project iam-backend-ai
 * @description
 */
@Component
@ConfigurationProperties(prefix = "token")
@Data
public class TokenProperties {
    private boolean enabled = true;
    private String defaultTier = "FREE";
    private int maxPromptTokens = 6000;
    private double contextReserveRatio = 0.2;
    private Map<String, ModelPrice> cost = new HashMap<>();
    private Map<String, TierConfig> quota = new HashMap<>();

    @Data public static class ModelPrice { private double input; private double output; }
    @Data public static class TierConfig { private long daily; private long monthly; private int maxRequest; }
}
