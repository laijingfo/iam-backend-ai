package com.lenovo.ai.memory;

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
 * 修复：改用 SummaryUtil#extractLongTermMemoriesAsList，
 * 不再对字符串做 JSON 解析；单条失败不影响整批。
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

    @Async("memoryExecutor")
    public void extract(String userId, List<Message> turn) {
        try {
            if (turn == null || turn.isEmpty()) {
                return;
            }
            String dialog = turn.stream()
                    .map(m -> m.role() + ": " + m.content())
                    .collect(Collectors.joining("\n"));

            // 直接拿 List，SummaryUtil 已保证是合法结构
            List<Map<String, Object>> items = summaryUtil.extractLongTermMemoriesAsList(dialog);
            if (items == null || items.isEmpty()) {
                log.debug("[MemoryExtractor] 未检测到值得记住的信息");
                return;
            }

            for (Map<String, Object> it : items) {
                saveOne(userId, it);
            }
        } catch (Exception e) {
            log.warn("长期记忆抽取失败：{}", e.getMessage(), e);
        }
    }

    private void saveOne(String userId, Map<String, Object> it) {
        try {
            Object typeObj = it.get("type");
            Object contentObj = it.get("content");
            if (typeObj == null || contentObj == null) {
                log.warn(">>> 记忆字段缺失，跳过：item={}", it);
                return;
            }
            String type = String.valueOf(typeObj);
            String content = String.valueOf(contentObj);
            if (content.isBlank()) {
                return;
            }
            double importance = 0.5;
            Object imp = it.get("importance");
            if (imp instanceof Number) {
                importance = ((Number) imp).doubleValue();
            }

            List<MemoryItem> existing = memoryStore.findByUserAndContentLike(userId, content);
            if (existing != null && !existing.isEmpty()) {
                memoryStore.invalidate(existing.get(0).id());
            }

            // ===== 降级：embedding 失败则跳过这条记忆的保存 =====
            List<Double> embedding;
            try {
                embedding = embeddingClient.embed(content);
            } catch (Exception e) {
                log.warn(">>> 记忆 embedding 失败，跳过保存：content={}, err={}",
                        content, e.toString());
                return;
            }
            if (embedding == null || embedding.isEmpty()) {
                log.warn(">>> 记忆 embedding 为空，跳过保存：content={}", content);
                return;
            }

            memoryStore.save(MemoryItem.of(userId, type, content, embedding, importance));
            log.info(">>> 成功保存记忆：type={}, content={}",
                    type, content.substring(0, Math.min(50, content.length())));
        } catch (Exception e) {
            // 单条失败不影响其它条目
            log.warn(">>> 保存单条记忆失败：item={}, err={}", it, e.toString());
        }
    }
}