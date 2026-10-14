package com.lenovo.ai.memory.store;

import com.lenovo.ai.memory.model.MemoryHit;
import com.lenovo.ai.memory.model.MemoryItem;

import java.util.List;

/**
 * 向量记忆存储：保存 + 向量检索 + 内容模糊查询。
 *
 * @author laifo
 * @date 2026-09-30
 */
public interface MemoryStore {

    /** 保存一条记忆 */
    void save(MemoryItem item);

    /** 向量 KNN 检索（仅返回 item） */
    List<MemoryItem> searchByUser(String userId, List<Double> queryEmbedding, int topK);

    /**
     * 向量 KNN 检索（带相似度分数）。
     * <p>默认实现退化为无分数版本，实现类应覆盖以提供真实 score。</p>
     */
    default List<MemoryHit> searchHitsByUser(String userId, List<Double> queryEmbedding, int topK) {
        return searchByUser(userId, queryEmbedding, topK).stream()
                .map(item -> new MemoryHit(item, 1.0))
                .toList();
    }

    /** 按内容子串模糊查询（用于去重） */
    List<MemoryItem> findByUserAndContentLike(String userId, String keyword);
}