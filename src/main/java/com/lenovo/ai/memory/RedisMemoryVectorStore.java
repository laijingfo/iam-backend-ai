package com.lenovo.ai.memory;

/**
 * @author laifo
 * @version 1.0
 * @date 2026-10-12 15:23
 * @project iam-backend-ai
 * @description
 */
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/**
 * 基于 Redis Hash 的简易向量存储实现。
 * key:  ai:memory:vec:{userId}     field: memoryId     value: MemoryItem JSON
 *
 * 检索：取出该用户全部记忆，算余弦相似度，取 topK。
 * 适合记忆量 < 2000 条；再大建议换真正的向量索引（如 RedisSearch / Milvus）。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RedisMemoryVectorStore implements MemoryVectorStore {

    private static final String PREFIX = "ai:memory:vec:";
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final StringRedisTemplate redis;

    @Override
    public void add(String userId, MemoryItem item, List<Double> embedding) {
        try {
            redis.opsForHash().put(PREFIX + userId, item.id(), MAPPER.writeValueAsString(item));
            log.debug("[memory-vec] add: userId={}, id={}", userId, item.id());
        } catch (Exception e) {
            log.error("[memory-vec] add 失败: userId={}, id={}", userId, item.id(), e);
            throw new RuntimeException("memory add 失败", e);
        }
    }

    @Override
    public List<MemoryHit> search(String userId, List<Double> queryEmbedding, int topK) {
        if (queryEmbedding == null || queryEmbedding.isEmpty()) return List.of();

        Map<Object, Object> all = redis.opsForHash().entries(PREFIX + userId);
        if (all == null || all.isEmpty()) {
            log.info("[memory-vec] search: userId={}, 库内 0 条", userId);
            return List.of();
        }

        List<MemoryHit> hits = new ArrayList<>(all.size());
        for (Object v : all.values()) {
            try {
                MemoryItem item = MAPPER.readValue(String.valueOf(v), MemoryItem.class);
                if (item.invalid()) continue;
                double score = cosine(queryEmbedding, item.embedding());
                hits.add(new MemoryHit(item, score));
            } catch (Exception e) {
                log.warn("[memory-vec] 反序列化失败，跳过一条: {}", e.getMessage());
            }
        }
        hits.sort(Comparator.comparingDouble(MemoryHit::score).reversed());

        log.info("[memory-vec] search: userId={}, 库内 {} 条, 返回 top{}",
                userId, hits.size(), topK);
        return hits.stream().limit(topK).toList();
    }

    @Override
    public void update(MemoryItem item) {
        try {
            redis.opsForHash().put(PREFIX + item.userId(), item.id(),
                    MAPPER.writeValueAsString(item));
            log.debug("[memory-vec] update: userId={}, id={}", item.userId(), item.id());
        } catch (Exception e) {
            log.error("[memory-vec] update 失败: id={}", item.id(), e);
            throw new RuntimeException("memory update 失败", e);
        }
    }

    private static double cosine(List<Double> a, List<Double> b) {
        if (a == null || b == null || a.size() != b.size() || a.isEmpty()) return 0;
        double dot = 0, na = 0, nb = 0;
        for (int i = 0; i < a.size(); i++) {
            double x = a.get(i) == null ? 0 : a.get(i);
            double y = b.get(i) == null ? 0 : b.get(i);
            dot += x * y;
            na += x * x;
            nb += y * y;
        }
        if (na == 0 || nb == 0) return 0;
        return dot / (Math.sqrt(na) * Math.sqrt(nb));
    }
}