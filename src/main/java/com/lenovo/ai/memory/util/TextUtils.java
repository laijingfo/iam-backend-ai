package com.lenovo.ai.memory.util;

/**
 * 文本处理工具。
 *
 * <p>★ P1-4 修复：原 {@code stripCodeFence} 会同时处理"代码围栏"和"方括号截取"，
 * 导致含 {@code [xxx]} 的摘要被误截。现拆成两个方法：</p>
 * <ul>
 *   <li>{@link #stripCodeFence(String)}：只剥离 ``` 代码围栏</li>
 *   <li>{@link #extractJsonArray(String)}：只提取第一个 [ 到最后一个 ] 的内容</li>
 * </ul>
 */
public final class TextUtils {

    private static final int LOG_PREVIEW_LEN = 300;

    private TextUtils() {}

    /**
     * 剥离 markdown 代码围栏（```json ... ```）。
     * <p><b>不会</b>处理方括号。适合处理摘要、普通文本。</p>
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
        return t;
    }

    /**
     * 从可能含 markdown 围栏的文本中提取 JSON 数组。
     * <p>先 {@link #stripCodeFence}，再取第一个 [ 到最后一个 ] 的内容。</p>
     * <p><b>只用于 JSON 数组解析场景</b>，不要用于普通摘要。</p>
     */
    public static String extractJsonArray(String s) {
        if (s == null) return "";
        String t = stripCodeFence(s).trim();
        if (t.isEmpty()) return t;

        int start = t.indexOf('[');
        int end = t.lastIndexOf(']');
        if (start >= 0 && end > start) {
            return t.substring(start, end + 1);
        }
        return t;
    }

    public static boolean looksLikeId(String s) {
        if (s == null) return false;
        String t = s.trim();
        if (t.length() == 32) {
            return t.matches("(?i)^[0-9a-f]{32}$");
        }
        if (t.length() == 36) {
            return t.matches("(?i)^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$");
        }
        return false;
    }

    public static String preview(String s) {
        if (s == null) return "null";
        return s.length() <= LOG_PREVIEW_LEN
                ? s
                : s.substring(0, LOG_PREVIEW_LEN) + "...(truncated)";
    }
}