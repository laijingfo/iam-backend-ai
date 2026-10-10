package com.lenovo.ai.memory;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lenovo.ai.llm.Message;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 长期记忆抽取工具。
 * <p>
 * 改动：embedding 调用用 try-catch 降级，失败则跳过该条记忆的保存。
 *
 * @author laifo
 * @date 2026-09-30
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MemoryExtractor {

    private final MemoryStore memoryStore;
    private final EmbeddingClient embeddingClient;
    private final SummaryUtil summaryUtil;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Async("memoryExecutor")
    public void extract(String userId, List<Message> turn) {
        try {
            String dialog = turn.stream()
                    .map(m -> m.role() + ": " + m.content())
                    .collect(Collectors.joining("\n"));

            // 使用 SummaryUtil 提取长期记忆
            String json = summaryUtil.extractLongTermMemories(dialog);
            
            if ("[]".equals(json.trim())) {
                log.debug("[MemoryExtractor] 未检测到值得记住的信息");
                return;
            }

            List<Map<String, Object>> items = objectMapper.readValue(
                    json, new TypeReference<>() {});

            for (Map<String, Object> it : items) {
                String type = String.valueOf(it.get("type"));
                String content = String.valueOf(it.get("content"));
                double importance = ((Number) it.getOrDefault("importance", 0.5)).doubleValue();

                List<MemoryItem> existing = memoryStore.findByUserAndContentLike(userId, content);
                if (!existing.isEmpty()) {
                    memoryStore.invalidate(existing.get(0).id());
                }

                // ===== 降级：embedding 失败则跳过这条记忆的保存 =====
                List<Double> embedding;
                try {
                    embedding = embeddingClient.embed(content);
                } catch (Exception e) {
                    log.warn(">>> 记忆 embedding 失败，跳过保存：content={}, err={}",
                            content, e.toString());
                    continue;
                }
                if (embedding == null || embedding.isEmpty()) {
                    log.warn(">>> 记忆 embedding 为空，跳过保存：content={}", content);
                    continue;
                }

                memoryStore.save(MemoryItem.of(userId, type, content, embedding, importance));
                log.debug(">>> 成功保存记忆：type={}, content={}", type, content.substring(0, Math.min(50, content.length())));
            }
        } catch (Exception e) {
            log.warn("长期记忆抽取失败：{}", e.getMessage());
        }
    }
}
