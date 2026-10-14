package com.lenovo.ai.memory.store;

import com.lenovo.ai.memory.embedding.EmbeddingClient;
import com.lenovo.ai.memory.model.MemoryHit;
import com.lenovo.ai.memory.model.MemoryItem;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * 用户记忆业务门面：编排 embedding、精确/语义去重、召回。
 *
 * @apiNote 仅限 memory 模块内部使用，外部调用方请通过
 *          {@link com.lenovo.ai.memory.facade.MemoryFacade}。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class UserMemoryStore {

    private static final String PREFIX = "ai:memory:";
    private static final long   EXACT_TTL_DAYS = 30;

    /** 语义去重阈值：高于此分视为同一条 */
    private static final double SEMANTIC_DUP_SCORE = 0.95;
    /** 召回阈值 */
    private static final double RECALL_MIN_SCORE   = 0.0;

    private final StringRedisTemplate redis;
    private final EmbeddingClient embeddingClient;
    private final MemoryStore memoryStore;          // ★ 改为 MemoryStore

    // =========================================================
    // 保存
    // =========================================================
    public void save(String userId, String type, String content, double importance) {
        if (content == null || content.isBlank()) return;

        String id = md5(userId + "|" + type + "|" + content.trim());

        // ① 精确去重
        String exactKey = PREFIX + "exact:" + id;
        Boolean first = redis.opsForValue()
                .setIfAbsent(exactKey, "1", EXACT_TTL_DAYS, TimeUnit.DAYS);
        if (Boolean.FALSE.equals(first)) {
            log.info("[memory-save] 精确重复，跳过: userId={}, type={}, content={}",
                    userId, type, content);
            return;
        }

        List<Double> emb = embeddingClient.embed(content);
        if (emb == null || emb.isEmpty()) {
            log.warn("[memory-save] embedding 为空，跳过: content={}", content);
            return;
        }

        // ② 语义去重
        List<MemoryHit> hits = memoryStore.searchHitsByUser(userId, emb, 1);
        if (!hits.isEmpty() && hits.get(0).score() >= SEMANTIC_DUP_SCORE) {
            MemoryHit hit = hits.get(0);
            memoryStore.save(hit.item().withHit());   // ★ 命中累加直接 save
            log.info("[memory-save] 语义重复(score={})，累加命中: userId={}, content={}",
                    hit.score(), userId, content);
            return;
        }

        // ③ 真入库
        MemoryItem item = MemoryItem.of(userId, type, content.trim(), emb, importance);
        memoryStore.save(item);
        log.info("[memory-save] userId={}, type={}, content={}", userId, type, content);
    }

    // =========================================================
    // 召回
    // =========================================================
    public List<MemoryItem> recall(String userId, String query, int topK) {
        if (query == null || query.isBlank()) return List.of();

        List<Double> emb = embeddingClient.embed(query);
        if (emb == null || emb.isEmpty()) {
            log.warn("[memory-recall] embedding 为空: userId={}, query={}", userId, query);
            return List.of();
        }

        List<MemoryHit> hits = memoryStore.searchHitsByUser(userId, emb, topK);

        List<MemoryItem> result = hits.stream()
                .filter(h -> h.score() >= RECALL_MIN_SCORE)
                .map(MemoryHit::item)
                .toList();

        log.info("[memory-recall] userId={}, query={}, topK={}, 命中={}, scores={}",
                userId, query, topK, result.size(),
                hits.stream().map(h -> String.format("%.3f", h.score())).toList());
        return result;
    }

    // =========================================================
    private static String md5(String s) {
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] d = md.digest(s.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : d) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (Exception e) {
            throw new IllegalStateException("md5 failed", e);
        }
    }
}