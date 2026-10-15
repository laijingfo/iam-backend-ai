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

    /**
     * 精确去重闸的 TTL（天）。
     *
     * <p><b>修复</b>：原值 100 天大于记忆本体 TTL（RedisMemoryStore.TTL_SECONDS = 90 天）。
     * 记忆 90 天后过期消失，但 exactKey 仍存活约 10 天，
     * 此期间相同内容再也存不进去，且没有任何补偿机制 —— 表现为"记忆静默丢失"。</p>
     *
     * <p>必须与记忆本体 TTL 一致或更短。</p>
     */
    private static final long EXACT_TTL_DAYS = 90;

    /** 语义去重阈值：高于此分视为同一条 */
    private static final double SEMANTIC_DUP_SCORE = 0.9;
    /** 召回阈值 */
    private static final double RECALL_MIN_SCORE = 0.0;

    private final StringRedisTemplate redis;
    private final EmbeddingClient embeddingClient;
    private final MemoryStore memoryStore;

    // =========================================================
    // 保存
    // =========================================================
    public void save(String userId, String type, String content, double importance) {
        if (content == null || content.isBlank()) return;

        String id = DigestUtils.md5(userId + "|" + type + "|" + content.trim());
        String exactKey = PREFIX + "exact:" + id;

        // ★ ① 原子抢闸：setIfAbsent 成功才继续
        //    这样并发下只有一个请求能走到后面，避免重复入库
        Boolean first = redis.opsForValue()
                .setIfAbsent(exactKey, "1", EXACT_TTL_DAYS, TimeUnit.DAYS);
        if (Boolean.FALSE.equals(first)) {
            log.info("[memory-save] 精确重复，跳过: userId={}, type={}", userId, type);
            return;
        }

        // ★ 从这里开始，任何中途失败都要回滚 exactKey
        boolean shouldRollback = true;
        try {
            // ② 算 embedding
            List<Double> emb;
            try {
                emb = embeddingClient.embed(content);
            } catch (Exception e) {
                log.warn("[memory-save] embedding 失败，回滚 exactKey: content={}", content, e);
                return;   // finally 里会回滚
            }
            if (emb == null || emb.isEmpty()) {
                log.warn("[memory-save] embedding 为空，回滚 exactKey: content={}", content);
                return;   // finally 里会回滚
            }

            // ③ 语义去重
            List<MemoryHit> hits = memoryStore.searchHitsByUser(userId, emb, 1);
            if (!hits.isEmpty() && hits.get(0).score() >= SEMANTIC_DUP_SCORE) {
                MemoryHit hit = hits.get(0);
                memoryStore.save(hit.item().withHit());
                log.info("[memory-save] 语义重复(score={}), 累加命中, 保留 exactKey",
                        hit.score());
                shouldRollback = false;   // ★ 语义重复 = 已存在，保留 exactKey
                return;
            }

            // ④ 真正入库
            MemoryItem item = MemoryItem.of(userId, type, content.trim(), emb, importance);
            memoryStore.save(item);

            log.info("[memory-save] 保存成功: userId={}, type={}, content={}",
                    userId, type, content);
            shouldRollback = false;   // ★ 成功，保留 exactKey

        } finally {
            if (shouldRollback) {
                // ★ 中途失败：回滚 exactKey，允许下次重试
                try {
                    redis.delete(exactKey);
                    log.warn("[memory-save] 已回滚 exactKey: id={}", id);
                } catch (Exception e) {
                    log.error("[memory-save] 回滚 exactKey 失败: id={}", id, e);
                }
            }
        }
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