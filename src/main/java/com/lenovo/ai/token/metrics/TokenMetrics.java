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

    /**
     * 上报一次 token 消耗与成本。
     *
     * @param userId 仅用于日志 / 未来扩展，<b>不</b>作为 tag（避免高基数）
     * @param model  模型名（作为 tag）
     * @param usage  token 用量
     * @param cost   成本（元）
     */
    public void recordUsage(String userId, String model, TokenUsage usage, double cost) {
        if (usage == null) return;
        counter("ai_token_consumed_total", "model", model, "type", "input")
                .increment(usage.inputTokens());
        counter("ai_token_consumed_total", "model", model, "type", "output")
                .increment(usage.outputTokens());
        counter("ai_token_cost_total_cny", "model", model).increment(cost);
    }

    public void recordQuotaExceeded(String userId, String reason) {
        String safeReason = (reason == null || reason.isBlank()) ? "unknown" : reason;
        counter("ai_token_quota_exceeded_total", "reason", safeReason).increment();
    }

    private Counter counter(String name, String... tags) {
        // ★ P2 修复：原用 Arrays.hashCode(tags) 作 key，32 位 hash 有碰撞风险，
        //   不同 tags 组可能命中同一 Counter，导致指标串号。
        //   改用完整 tags 拼接，无碰撞。
        String key = name + "|" + String.join("\u0001", tags);
        return cache.computeIfAbsent(key, k -> {
            Counter.Builder b = Counter.builder(name);
            for (int i = 0; i < tags.length; i += 2) {
                b.tag(tags[i], tags[i + 1]);
            }
            return b.register(registry);
        });
    }
}