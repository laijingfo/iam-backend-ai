package com.lenovo.ai.memory;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.List;
import java.util.UUID;

/**
 * 长期记忆项。
 * id 采用 userId|type|content 的 md5，保证同一条记忆 id 稳定（便于去重/更新）。
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

    /** ★ 新增：命中累加（importance 微增，updatedAt 刷新） */
    public MemoryItem withHit() {
        return new MemoryItem(
                id, userId, type, content, embedding,
                Math.min(1.0, importance + 0.02),
                createdAt, System.currentTimeMillis(), invalid);
    }
}