package com.lenovo.ai.token;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
/**
 * Token 相关的 Prometheus 指标
 * 注意：需要在 pom.xml 引入 micrometer-registry-prometheus
 */
@Component
@RequiredArgsConstructor
public class TokenMetrics {

    private final MeterRegistry registry;

    /** 按 model + type 记录 token 消耗（用 Counter.builder 缓存） */
    public void recordUsage(String userId, String model, TokenUsage usage, double cost) {
        if (usage == null) return;

        Counter.builder("ai_token_consumed_total")
                .tag("model", model)
                .tag("type", "input")
                .register(registry)
                .increment(usage.inputTokens());

        Counter.builder("ai_token_consumed_total")
                .tag("model", model)
                .tag("type", "output")
                .register(registry)
                .increment(usage.outputTokens());

        Counter.builder("ai_token_cost_total_cny")
                .tag("model", model)
                .register(registry)
                .increment(cost);
    }

    public void recordQuotaExceeded(String userId, String reason) {
        Counter.builder("ai_token_quota_exceeded_total")
                .tag("reason", reason)
                .register(registry)
                .increment();
    }
}