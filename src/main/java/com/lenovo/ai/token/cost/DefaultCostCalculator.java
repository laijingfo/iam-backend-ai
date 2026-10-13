package com.lenovo.ai.token.cost;


import com.lenovo.ai.token.config.TokenProperties;
import com.lenovo.ai.token.model.TokenUsage;
import com.lenovo.ai.token.model.ModelPrice;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
/**
 * @author laifo
 * @version 1.0
 * @date 2026-10-13 11:00
 * @project iam-backend-ai
 * @description
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DefaultCostCalculator implements CostCalculator {

    private static final double CACHED_DISCOUNT = 0.1;
    private final TokenProperties properties;

    /** 缓存解析后的价格，避免每次查 Map */
    private final Map<String, ModelPrice> cache = new ConcurrentHashMap<>();

    @Override
    public double calculate(String modelName, TokenUsage usage) {
        if (usage == null || usage.totalTokens() <= 0) return 0.0;

        ModelPrice price = resolve(modelName);
        int normalInput = usage.inputTokens() - usage.cachedTokens();
        int cachedInput = Math.max(usage.cachedTokens(), 0);

        double inputCost = (normalInput / 1000.0) * price.input()
                + (cachedInput / 1000.0) * price.input() * CACHED_DISCOUNT;
        double outputCost = (usage.outputTokens() / 1000.0) * price.output();
        double total = inputCost + outputCost;

        log.debug("[cost] model={}, in={}, out={}, cached={}, cost=¥{}",
                modelName, usage.inputTokens(), usage.outputTokens(),
                usage.cachedTokens(), String.format("%.6f", total));
        return total;
    }

    private ModelPrice resolve(String modelName) {
        return cache.computeIfAbsent(modelName, name -> {
            TokenProperties.ModelPrice p = properties.getCost().get(name);
            if (p != null) return new ModelPrice(p.getInput(), p.getOutput());
            log.warn("[cost] 模型 {} 未配置价格，使用默认值", name);
            return ModelPrice.DEFAULT;
        });
    }
}
