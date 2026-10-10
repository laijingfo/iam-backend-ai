package com.lenovo.ai.memory;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lenovo.ai.llm.LlmClient;
import com.lenovo.ai.llm.Message;
import com.lenovo.util.RedisUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 对话摘要生成工具类
 *
 * @author laifo
 * @date 2026-10-10
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SummaryUtil {

    private static final Set<String> ALLOWED_TYPES = Set.of("PREFERENCE", "FACT", "ENTITY");
    private static final String CACHE_PREFIX = "ai:summary:";
    private static final int LOG_PREVIEW_LEN = 300;

    private final LlmClient llmClient;
    private final RedisUtils redisUtils;
    private final ObjectMapper objectMapper;

    // ==================== 摘要 ====================

    public String summarize(List<Message> messages) {
        return summarize(messages, 200);
    }

    public String summarize(List<Message> messages, int maxWordSize) {
        if (messages == null || messages.isEmpty()) {
            return "";
        }

        String raw = messages.stream()
                .map(m -> m.role() + ": " + m.content())
                .collect(Collectors.joining("\n"));

        // 加命名空间前缀，避免和别的业务撞 key
        String cacheKey = CACHE_PREFIX + Integer.toHexString(raw.hashCode());

        try {
            Object cached = redisUtils.get(cacheKey);
            if (cached != null) {
                String cachedStr = String.valueOf(cached);
                // 只在内容可信时才用缓存
                if (!cachedStr.isBlank() && !looksLikeId(cachedStr)) {
                    log.debug(">>> 使用缓存的摘要：cacheKey={}", cacheKey);
                    return cachedStr;
                }
                log.warn(">>> 缓存内容可疑，忽略：cacheKey={}, value={}", cacheKey, preview(cachedStr));
            }
        } catch (Exception e) {
            log.warn(">>> 读取 Redis 缓存失败", e);
        }

        String prompt = """
            请把以下对话压缩成简洁摘要，保留：
            1. 用户的关键诉求和已确认的事实
            2. 已经执行过的操作及其结果
            3. 尚未解决的问题
            忽略寒暄和重复内容。控制在 %d 字以内，直接输出摘要内容。
            
            对话：
            %s
            """.formatted(maxWordSize, raw);

        String summary = stripCodeFence(llmClient.chatSimple(prompt));
        if (summary == null || summary.isBlank()) {
            log.warn(">>> LLM 摘要返回为空");
            return "";
        }
        // 防御：LLM 网关失败时可能返回 requestId 之类的字符串，别缓存也别返回
        if (looksLikeId(summary)) {
            log.warn(">>> LLM 摘要疑似返回了 ID，放弃：{}", preview(summary));
            return "";
        }

        try {
            redisUtils.set(cacheKey, summary, Duration.ofHours(1).toSeconds());
            log.debug(">>> 摘要已生成并缓存：cacheKey={}, summaryLen={}", cacheKey, summary.length());
        } catch (Exception e) {
            log.warn(">>> 保存摘要到 Redis 缓存失败", e);
        }

        return summary;
    }

    // ==================== 长期记忆抽取 ====================

    /**
     * 从对话中提取值得长期记住的信息项。
     * <p>
     * 契约：<b>永远返回合法 JSON 数组字符串</b>（无内容时返回 "[]"）。
     * 任何异常、非数组、字段非法的情况都归零为 "[]"。
     */
    public String extractLongTermMemories(String dialog) {
        List<Map<String, Object>> items = extractLongTermMemoriesAsList(dialog);
        if (items.isEmpty()) {
            return "[]";
        }
        try {
            return objectMapper.writeValueAsString(items);
        } catch (Exception e) {
            log.warn(">>> 序列化记忆列表失败，返回空数组", e);
            return "[]";
        }
    }

    /**
     * 推荐调用方式：直接拿 List，避免调用方再对字符串做 JSON 解析。
     */
    public List<Map<String, Object>> extractLongTermMemoriesAsList(String dialog) {
        if (dialog == null || dialog.isBlank()) {
            return Collections.emptyList();
        }

        String prompt = """
            从以下对话中抽取值得长期记住的用户信息。
            只输出 JSON 数组，不要任何解释。格式：
            [{"type":"PREFERENCE|FACT|ENTITY","content":"...","importance":0.0~1.0}]
            没有值得记住的信息则输出 []。
            
            判断标准：
            - PREFERENCE: 用户偏好（语言、风格、习惯）
            - FACT: 关于用户的稳定事实（部门、职位、常用系统）
            - ENTITY: 用户关注的具体对象（项目名、系统名）
            临时性问题不要抽取。
            
            对话：
            %s
            """.formatted(dialog);

        String raw;
        try {
            raw = llmClient.chatSimple(prompt);
        } catch (Exception e) {
            log.warn(">>> LLM 抽取记忆调用失败：{}", e.getMessage(), e);
            return Collections.emptyList();
        }

        String cleaned = stripCodeFence(raw);
        if (cleaned == null || cleaned.isBlank()) {
            log.debug(">>> LLM 抽取记忆返回空");
            return Collections.emptyList();
        }
        // 必须是以 [ 开头的数组，否则视为脏数据（例如网关返回了 requestId）
        if (!cleaned.startsWith("[")) {
            log.warn(">>> 抽取记忆返回非 JSON 数组，丢弃。raw={}", preview(raw));
            return Collections.emptyList();
        }

        List<Map<String, Object>> items;
        try {
            items = objectMapper.readValue(cleaned,
                    new TypeReference<List<Map<String, Object>>>() {});
        } catch (Exception e) {
            log.warn(">>> 抽取记忆 JSON 解析失败：{} | raw={}", e.getMessage(), preview(raw));
            return Collections.emptyList();
        }
        if (items == null || items.isEmpty()) {
            return Collections.emptyList();
        }

        // 逐条校验并规范化
        return items.stream()
                .filter(this::isValidItem)
                .peek(this::normalizeItem)
                .collect(Collectors.toList());
    }

    private boolean isValidItem(Map<String, Object> it) {
        if (it == null) {
            return false;
        }
        Object type = it.get("type");
        Object content = it.get("content");
        if (type == null || content == null) {
            log.warn(">>> 记忆项缺少 type/content，丢弃：{}", it);
            return false;
        }
        String typeStr = String.valueOf(type).trim().toUpperCase();
        if (!ALLOWED_TYPES.contains(typeStr)) {
            log.warn(">>> 记忆项 type 非法，丢弃：{}", it);
            return false;
        }
        if (String.valueOf(content).isBlank()) {
            log.warn(">>> 记忆项 content 为空，丢弃：{}", it);
            return false;
        }
        return true;
    }

    private void normalizeItem(Map<String, Object> it) {
        it.put("type", String.valueOf(it.get("type")).trim().toUpperCase());
        Object imp = it.get("importance");
        if (!(imp instanceof Number)) {
            it.put("importance", 0.5);
        } else {
            double v = ((Number) imp).doubleValue();
            if (Double.isNaN(v) || v < 0) v = 0;
            if (v > 1) v = 1;
            it.put("importance", v);
        }
    }

    // ==================== 工具 ====================

    /**
     * 移除 markdown 代码块标记，并截取第一个 [ 到最后一个 ] 之间的内容。
     */
    public String stripCodeFence(String s) {
        if (s == null) {
            return "";
        }
        String t = s.trim();
        if (t.isEmpty()) {
            return t;
        }
        // 去掉 ```json ... ``` 或 ``` ... ```
        if (t.startsWith("```")) {
            t = t.replaceFirst("^```[a-zA-Z]*\\s*", "");
            int idx = t.lastIndexOf("```");
            if (idx >= 0) {
                t = t.substring(0, idx);
            }
            t = t.trim();
        }
        // 兼容 LLM 前后多嘴
        int start = t.indexOf('[');
        int end = t.lastIndexOf(']');
        if (start >= 0 && end > start) {
            t = t.substring(start, end + 1);
        }
        return t;
    }

    /**
     * 判断字符串是否像 UUID / hex id（如 df7ac871-xxxx... 或 8~32 位 hex），
     * 用于防止把网关错误里的 requestId 当成正常内容。
     */
    private boolean looksLikeId(String s) {
        if (s == null) {
            return false;
        }
        String t = s.trim();
        if (t.isEmpty() || t.length() > 64) {
            return false;
        }
        // 纯 hex（允许连字符），长度 8~36
        return t.matches("(?i)^[0-9a-f]{8}(-[0-9a-f]{4}){0,4}([0-9a-f]{0,12})?$")
                && t.replace("-", "").length() >= 8;
    }

    private String preview(String s) {
        if (s == null) {
            return "null";
        }
        return s.length() <= LOG_PREVIEW_LEN ? s : s.substring(0, LOG_PREVIEW_LEN) + "...(truncated)";
    }
}