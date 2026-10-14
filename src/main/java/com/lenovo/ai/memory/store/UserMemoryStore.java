package com.lenovo.ai.memory.store;

import com.lenovo.ai.memory.embedding.EmbeddingClient;
import com.lenovo.ai.memory.model.MemoryHit;
import com.lenovo.ai.memory.model.MemoryItem;
import com.lenovo.ai.util.DigestUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

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
    // 将 EXACT_TTL_DAYS 从 30 改为 90+，与 RedisMemoryStore 的 TTL 对齐
    private static final long EXACT_TTL_DAYS = 90L;

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

//        String id = md5(userId + "|" + type + "|" + content.trim());
        String id = DigestUtils.md5(userId + "|" + type + "|" + content.trim());
        String exactKey = PREFIX + "exact:" + id;

        // ① 先检查是否已存在
        Boolean exists = redis.hasKey(exactKey);
        if (Boolean.TRUE.equals(exists)) {
            log.info("[memory-save] 精确重复，跳过: userId={}, type={}", userId, type);
            return;
        }

        // ② 计算 embedding（失败则直接返回，不写 exactKey）
        List<Double> emb;
        try {
            emb = embeddingClient.embed(content);
        } catch (Exception e) {
            log.warn("[memory-save] embedding 失败，跳过: content={}", content, e);
            return;
        }
        if (emb == null || emb.isEmpty()) {
            log.warn("[memory-save] embedding 为空，跳过: content={}", content);
            return;
        }

        // ③ 语义去重
        List<MemoryHit> hits = memoryStore.searchHitsByUser(userId, emb, 1);
        if (!hits.isEmpty() && hits.get(0).score() >= SEMANTIC_DUP_SCORE) {
            MemoryHit hit = hits.get(0);
            memoryStore.save(hit.item().withHit());
            log.info("[memory-save] 语义重复(score={}), 累加命中", hit.score());
            return;
        }

        // ④ 真正入库
        MemoryItem item = MemoryItem.of(userId, type, content.trim(), emb, importance);
        memoryStore.save(item);

        // ⑤ 入库成功后才写 exactKey（setIfAbsent 兜底并发）
        Boolean first = redis.opsForValue()
                .setIfAbsent(exactKey, "1", EXACT_TTL_DAYS, TimeUnit.DAYS);
        if (Boolean.FALSE.equals(first)) {
            log.debug("[memory-save] 并发重复，已忽略 exactKey: id={}", id);
        }

        log.info("[memory-save] 保存成功: userId={}, type={}, content={}", userId, type, content);
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

}