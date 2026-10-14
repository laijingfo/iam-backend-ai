package com.lenovo.ai.memory.extractor;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lenovo.ai.llm.LlmClient;
import com.lenovo.ai.memory.util.TextUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 记忆抽取解析器：
 * <ol>
 *   <li>调用 LLM（prompt 由 {@link MemoryExtractionPrompt} 提供）</li>
 *   <li>清洗 code fence</li>
 *   <li>解析 JSON 数组</li>
 *   <li>字段校验 + 归一化</li>
 * </ol>
 *
 * @author laifo
 * @date 2026-10-13
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MemoryParser {

    private final LlmClient llmClient;
    private final ObjectMapper objectMapper;

    /** 抽取并返回结构化列表 */
    public List<Map<String, Object>> extractAsList(String dialog) {
        if (dialog == null || dialog.isBlank()) {
            log.warn(">>> [记忆抽取] dialog 为空，跳过");
            return Collections.emptyList();
        }

        log.info(">>> [记忆抽取] 开始, dialogLen={}, dialog={}",
                dialog.length(), TextUtils.preview(dialog));

        String raw;
        try {
            raw = llmClient.chatSimple(MemoryExtractionPrompt.build(dialog));
        } catch (Exception e) {
            log.warn(">>> [记忆抽取] LLM 调用失败：{}", e.getMessage(), e);
            return Collections.emptyList();
        }

        log.info(">>> [记忆抽取] LLM 原始返回, rawLen={}, raw={}",
                raw == null ? 0 : raw.length(), TextUtils.preview(raw));

        String cleaned = TextUtils.stripCodeFence(raw);
        if (cleaned == null || cleaned.isBlank()) {
            log.warn(">>> [记忆抽取] stripCodeFence 后为空, raw={}", TextUtils.preview(raw));
            return Collections.emptyList();
        }

        log.info(">>> [记忆抽取] 清洗后, cleanedLen={}, cleaned={}",
                cleaned.length(), TextUtils.preview(cleaned));

        if (!cleaned.startsWith("[")) {
            log.warn(">>> [记忆抽取] 返回非 JSON 数组，丢弃。cleaned={}", TextUtils.preview(cleaned));
            return Collections.emptyList();
        }

        List<Map<String, Object>> items;
        try {
            items = objectMapper.readValue(cleaned,
                    new TypeReference<List<Map<String, Object>>>() {});
        } catch (Exception e) {
            log.warn(">>> [记忆抽取] JSON 解析失败：{} | cleaned={}",
                    e.getMessage(), TextUtils.preview(cleaned));
            return Collections.emptyList();
        }

        if (items == null || items.isEmpty()) {
            log.info(">>> [记忆抽取] LLM 判定没有值得长期记住的信息（返回 []），dialog={}",
                    TextUtils.preview(dialog));
            return Collections.emptyList();
        }

        List<Map<String, Object>> valid = items.stream()
                .filter(this::isValidItem)
                .peek(this::normalizeItem)
                .collect(Collectors.toList());

        log.info(">>> [记忆抽取] 完成, 原始条数={}, 有效条数={}, items={}",
                items.size(), valid.size(), valid);

        return valid;
    }

    /** 兼容旧 API：返回 JSON 字符串 */
    public String extractAsJson(String dialog) {
        List<Map<String, Object>> items = extractAsList(dialog);
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

    private boolean isValidItem(Map<String, Object> it) {
        if (it == null) return false;
        Object type = it.get("type");
        Object content = it.get("content");
        if (type == null || content == null) {
            log.warn(">>> 记忆项缺少 type/content，丢弃：{}", it);
            return false;
        }
        String typeStr = String.valueOf(type).trim().toUpperCase();
        if (!MemoryExtractionPrompt.ALLOWED_TYPES.contains(typeStr)) {
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
}