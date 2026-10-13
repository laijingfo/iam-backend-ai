package com.lenovo.ai.token.metrics;

import com.lenovo.ai.token.model.TokenUsage;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
@RequiredArgsConstructor
public class TokenMetrics {

    private final MeterRegistry registry;
    private final Map<String, Counter> cache = new ConcurrentHashMap<>();

    public void recordUsage(String userId, String model, TokenUsage usage, double cost) {
        if (usage == null) return;
        counter("ai_token_consumed_total", "model", model, "type", "input")
                .increment(usage.inputTokens());
        counter("ai_token_consumed_total", "model", model, "type", "output")
                .increment(usage.outputTokens());
        counter("ai_token_cost_total_cny", "model", model).increment(cost);
    }

    public void recordQuotaExceeded(String userId, String reason) {
        counter("ai_token_quota_exceeded_total", "reason", reason).increment();
    }

    private Counter counter(String name, String... tags) {
        StringBuilder sb = new StringBuilder(name);
        for (int i = 0; i < tags.length; i += 2) sb.append('|').append(tags[i]).append('=').append(tags[i + 1]);
        return cache.computeIfAbsent(sb.toString(), k -> {
            Counter.Builder b = Counter.builder(name);
            for (int i = 0; i < tags.length; i += 2) b.tag(tags[i], tags[i + 1]);
            return b.register(registry);
        });
    }
}