package com.lenovo.ai.util;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

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
            StringBuilder sb = new StringBuilder(d.length * 2);
            for (byte b : d) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (Exception e) {
            throw new IllegalStateException(algorithm + " 计算失败", e);
        }
    }
}