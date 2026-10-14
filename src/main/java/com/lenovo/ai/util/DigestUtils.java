package com.lenovo.ai.util;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;

/**
 * 摘要工具。
 */
public final class DigestUtils {

    private DigestUtils() {}

    public static String md5(String s) {
        return hex("MD5", s);
    }

    public static String sha256(String s) {
        return hex("SHA-256", s);
    }

    private static String hex(String algorithm, String s) {
        if (s == null) s = "";
        try {
            MessageDigest md = MessageDigest.getInstance(algorithm);
            byte[] d = md.digest(s.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(d);
        } catch (Exception e) {
            throw new IllegalStateException(algorithm + " 计算失败", e);
        }
    }
}