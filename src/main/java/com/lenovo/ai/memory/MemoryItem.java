package com.lenovo.ai.memory;

import java.util.List;
import java.util.UUID;

/**
 * @author laifo
 * @version 1.0
 * @date 2026-09-30 10:12
 * @project iam-backend-ai
 * @description
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
        double imp = Double.isNaN(importance) || importance < 0 ? 0.5 : Math.min(importance, 1.0);
        long now = System.currentTimeMillis();
        return new MemoryItem(UUID.randomUUID().toString(), userId, type, content,
                embedding, imp, now, now, false);
    }
}