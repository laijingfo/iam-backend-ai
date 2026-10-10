package com.lenovo.ai.memory;

import java.util.List;
import java.util.Set;

/**
 * @author laifo
 * @version 1.0
 * @date 2026-09-30 10:13
 * @project iam-backend-ai
 * @description
 */
public interface MemoryStore {
    void save(MemoryItem item);
    List<MemoryItem> searchByUser(String userId, List<Double> queryEmbedding, int topK);
    List<MemoryItem> findByUserAndContentLike(String userId, String keyword);
    void invalidate(String memoryId);
    void decayAll(String userId, double factor);
    void deleteBelow(String userId, double threshold);
    
    /**
     * 获取所有活跃用户的 ID 列表（有记忆记录的用户）
     * @return 活跃用户 ID 集合，未实现时抛出 UnsupportedOperationException
     */
    default Set<String> getAllActiveUsers() {
        throw new UnsupportedOperationException("批量查询活跃用户暂未实现");
    }
}