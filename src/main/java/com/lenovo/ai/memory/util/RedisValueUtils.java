package com.lenovo.ai.memory.util;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

/**
 * Redis 值解析工具，统一 null / 类型不匹配时的兜底。
 * <p>底层委托给 {@link RedisBytes}，确保 byte[] 走 UTF-8 解码。</p>
 *
 * @author laifo
 * @date 2026-10-13
 */
public final class RedisValueUtils {

    private RedisValueUtils() {}

    public static double parseDouble(Object o, double def) {
        return RedisBytes.asDouble(o, def);
    }

    public static long parseLong(Object o, long def) {
        return RedisBytes.asLong(o, def);
    }

    public static int parseInt(Object o, int def) {
        return RedisBytes.asInt(o, def);
    }

    /** byte[] Map 转 String Map（UTF-8） */
    public static Map<String, String> toStringMap(Map<byte[], byte[]> bytesMap) {
        if (bytesMap == null || bytesMap.isEmpty()) return Map.of();
        Map<String, String> map = new HashMap<>(bytesMap.size());
        for (Map.Entry<byte[], byte[]> e : bytesMap.entrySet()) {
            map.put(new String(e.getKey(), StandardCharsets.UTF_8),
                    new String(e.getValue(), StandardCharsets.UTF_8));
        }
        return map;
    }
}