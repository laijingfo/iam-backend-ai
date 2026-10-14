package com.lenovo.ai.memory.util;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

/**
 * Redis 返回值统一解码工具。
 */
public final class RedisBytes {

    private RedisBytes() {}

    public static String asString(Object o) {
        if (o == null) return null;
        if (o instanceof byte[] b) return new String(b, StandardCharsets.UTF_8);
        return String.valueOf(o);
    }

    public static long asLong(Object o, long def) {
        String s = asString(o);
        if (s == null) return def;
        try { return Long.parseLong(s); } catch (Exception e) { return def; }
    }

    public static double asDouble(Object o, double def) {
        String s = asString(o);
        if (s == null) return def;
        try { return Double.parseDouble(s); } catch (Exception e) { return def; }
    }

    public static int asInt(Object o, int def) {
        String s = asString(o);
        if (s == null) return def;
        try { return Integer.parseInt(s); } catch (Exception e) { return def; }
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