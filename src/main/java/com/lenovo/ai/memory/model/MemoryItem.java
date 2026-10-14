package com.lenovo.ai.memory.model;

import java.util.List;
import java.util.UUID;

/**
 * 长期记忆项。
 * <p>
 * id 由 UUID 生成，保证全局唯一；
 * 精确去重由 UserMemoryStore 的 setIfAbsent 保证（md5(userId|type|content)）；
 * 语义去重由向量检索相似度保证。
 */
public record MemoryItem(
        String id,
        String userId,
        String type,
        String content,
        List<Double> embedding,
        double importance,
        long createdAt,
        long updatedAt,
        boolean invalid
) {
    public static MemoryItem of(String userId, String type, String content,
                                List<Double> embedding, double importance) {
        if (userId == null || userId.isBlank()) {
            throw new IllegalArgumentException("userId 不能为空");
        }
        if (content == null || content.isBlank()) {
            throw new IllegalArgumentException("content 不能为空");
        }
        double imp = Double.isNaN(importance) || importance < 0
                ? 0.5 : Math.min(importance, 1.0);
        long now = System.currentTimeMillis();
        return new MemoryItem(UUID.randomUUID().toString(), userId, type, content,
                embedding, imp, now, now, false);
    }

    /** 命中累加（importance 微增，updatedAt 刷新） */
    public MemoryItem withHit() {
        return new MemoryItem(
                id, userId, type, content, embedding,
                Math.min(1.0, importance + 0.02),
                createdAt, System.currentTimeMillis(), invalid);
    }
}