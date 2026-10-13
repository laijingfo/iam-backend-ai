package com.lenovo.ai.token;


import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Map;
/**
 * @author laifo
 * @version 1.0
 * @date 2026-10-12 17:18
 * @project iam-backend-ai
 * @description
 * 成本计算 (单位: ¥ / 千 token)
 * 价格表可配置化, 这里以硬编码为例
 */
@Slf4j
@Component
public class CostCalculator {

    /**
     * 各模型价格 (¥ / 千 token)
     * input  = 输入价格
     * output = 输出价格
     */
    private static final Map<String, ModelPrice> PRICES = Map.of(
            "kimi-k2.6",         new ModelPrice(0.012, 0.012),
            "qwen-turbo",        new ModelPrice(0.0003, 0.0006),
            "qwen-plus",         new ModelPrice(0.0008, 0.002),
            "qwen-max",          new ModelPrice(0.02, 0.06),
            "text-embedding-v3", new ModelPrice(0.0007, 0.0)
    );

    /**
     * 计算成本 (元)
     */
    public double calculate(String modelName, TokenUsage usage) {
        if (usage == null) return 0.0;
        ModelPrice price = PRICES.getOrDefault(modelName, ModelPrice.DEFAULT);

        // 缓存命中的部分按 10% 价格计费 (示例, 具体看服务商)
        int normalInput = usage.inputTokens() - usage.cachedTokens();
        int cachedInput = usage.cachedTokens();

        double inputCost = (normalInput / 1000.0) * price.input()
                + (cachedInput / 1000.0) * price.input() * 0.1;
        double outputCost = (usage.outputTokens() / 1000.0) * price.output();
        double total = inputCost + outputCost;

        log.debug("[cost] model={}, in={}, out={}, cached={}, cost={}",
                modelName, usage.inputTokens(), usage.outputTokens(),
                usage.cachedTokens(), total);
        return total;
    }

    public record ModelPrice(double input, double output) {
        public static final ModelPrice DEFAULT = new ModelPrice(0.02, 0.06);
    }
}