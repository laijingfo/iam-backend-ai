package com.lenovo.ai.memory.util;

/**
 * @author laifo
 * @version 1.0
 * @date 2026-10-13
 * @project iam-backend-ai
 * @description
 * 文本处理工具
 */
public final class TextUtils {

    private static final int LOG_PREVIEW_LEN = 300;

    private TextUtils() {}

    /**
     * 移除 markdown 代码块标记，并截取第一个 [ 到最后一个 ] 之间的内容。
     */
    public static String stripCodeFence(String s) {
        if (s == null) return "";
        String t = s.trim();
        if (t.isEmpty()) return t;

        if (t.startsWith("```")) {
            t = t.replaceFirst("^```[a-zA-Z]*\\s*", "");
            int idx = t.lastIndexOf("```");
            if (idx >= 0) t = t.substring(0, idx);
            t = t.trim();
        }
        int start = t.indexOf('[');
        int end = t.lastIndexOf(']');
        if (start >= 0 && end > start) {
            t = t.substring(start, end + 1);
        }
        return t;
    }

    /**
     * 判断字符串是否像 UUID / hex id。
     */
    public static boolean looksLikeId(String s) {
        if (s == null) return false;
        String t = s.trim();
        // 只认标准的 32 位 hex（无连字符）或 36 位 UUID（带连字符）
        if (t.length() == 32) {
            return t.matches("(?i)^[0-9a-f]{32}$");
        }
        if (t.length() == 36) {
            return t.matches("(?i)^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$");
        }
        return false;
    }

    /**
     * 日志预览，超长截断。
     */
    public static String preview(String s) {
        if (s == null) return "null";
        return s.length() <= LOG_PREVIEW_LEN
                ? s
                : s.substring(0, LOG_PREVIEW_LEN) + "...(truncated)";
    }
}