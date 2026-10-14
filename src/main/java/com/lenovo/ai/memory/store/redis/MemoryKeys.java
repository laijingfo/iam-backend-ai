package com.lenovo.ai.memory.store.redis;

/**
 * 记忆相关 Redis key 构造入口
 *
 * @author laifo
 * @date 2026-10-13
 */
public final class MemoryKeys {

    private static final String BASE = "ai:memory:";

    private MemoryKeys() {}

    /** 主数据：ai:memory:{userId}:{memoryId} */
    public static String item(String userId, String memoryId) {
        return BASE + userId + ":" + memoryId;
    }

    /** 用户记忆 ID 集合：ai:memory:user:{userId} */
    public static String userSet(String userId) {
        return BASE + "user:" + userId;
    }

    /** 反向索引：ai:memory:reverse:{memoryId} */
    public static String reverse(String memoryId) {
        return BASE + "reverse:" + memoryId;
    }

    /** 向量存储：ai:memory:vec:{userId} */
    public static String vector(String userId) {
        return BASE + "vec:" + userId;
    }

    /** 扫描模式：所有用户的 user set */
    public static String userPattern() {
        return BASE + "user:*";
    }

    /** 扫描模式：某用户的所有记忆主键 */
    public static String itemPattern(String userId) {
        return BASE + userId + ":*";
    }
}