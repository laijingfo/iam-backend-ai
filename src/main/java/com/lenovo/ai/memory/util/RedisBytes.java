package com.lenovo.ai.memory.util;

import java.nio.charset.StandardCharsets;

/**
 * Redis 返回值统一解码工具。
 * <p>
 * 解决 ByteArrayCodec 下 {@code String.valueOf(byte[])} 得到 {@code [B@hash} 的问题。
 * 所有从 Lettuce / RediSearch 拿到的字段名、字段值，统一走本类解码。
 *
 * @author laifo
 * @date 2026-10-14
 */
public final class RedisBytes {

    private RedisBytes() {}

    /** 任意对象转 String：byte[] 走 UTF-8，其他走 String.valueOf */
    public static String asString(Object o) {
        if (o == null) return null;
        if (o instanceof byte[] b) return new String(b, StandardCharsets.UTF_8);
        return String.valueOf(o);
    }

    /** 任意对象转 long，失败返回 def */
    public static long asLong(Object o, long def) {
        String s = asString(o);
        if (s == null) return def;
        try { return Long.parseLong(s); } catch (Exception e) { return def; }
    }

    /** 任意对象转 double，失败返回 def */
    public static double asDouble(Object o, double def) {
        String s = asString(o);
        if (s == null) return def;
        try { return Double.parseDouble(s); } catch (Exception e) { return def; }
    }

    /** 任意对象转 int，失败返回 def */
    public static int asInt(Object o, int def) {
        String s = asString(o);
        if (s == null) return def;
        try { return Integer.parseInt(s); } catch (Exception e) { return def; }
    }
}