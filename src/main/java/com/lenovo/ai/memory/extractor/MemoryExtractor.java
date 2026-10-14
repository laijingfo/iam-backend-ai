package com.lenovo.ai.memory.extractor;

import com.lenovo.ai.llm.Message;
import com.lenovo.ai.memory.store.UserMemoryStore;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 记忆抽取调度器。
 *
 * @apiNote 仅限 memory 模块内部使用，外部调用方请通过
 *          {@link com.lenovo.ai.memory.facade.MemoryFacade#extractAsync(String, java.util.List)}。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MemoryExtractor {

    private final UserMemoryStore userMemoryStore;
    private final MemoryParser memoryParser;

    @Async("memoryExecutor")
    public void extract(String userId, List<Message> turn) {
        try {
            if (turn == null || turn.isEmpty()) {
                log.warn(">>> 记忆抽取跳过：对话为空，userId={}", userId);
                return;
            }
            String dialog = turn.stream()
                    .map(m -> m.role() + ": " + m.content())
                    .collect(Collectors.joining("\n"));

            log.info(">>> 开始抽取记忆：userId={}, 对话长度={} 字符", userId, dialog.length());

            List<Map<String, Object>> items = memoryParser.extractAsList(dialog);
            log.info(">>> 记忆抽取完成：userId={}, 抽取到 {} 条记忆", userId,
                    items == null ? 0 : items.size());

            if (items == null || items.isEmpty()) {
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
            String type = String.valueOf(typeObj).trim();
            String content = String.valueOf(contentObj).trim();
            if (content.isBlank()) return;

            double importance = 0.5;
            Object imp = it.get("importance");
            if (imp instanceof Number) {
                importance = ((Number) imp).doubleValue();
            }

            userMemoryStore.save(userId, type, content, importance);

        } catch (Exception e) {
            log.warn(">>> 保存单条记忆失败：item={}, err={}", it, e.toString());
        }
    }
}