package com.lenovo.ai.memory.facade;

import com.lenovo.ai.llm.Message;
import com.lenovo.ai.memory.context.ContextBuilder;
import com.lenovo.ai.memory.extractor.MemoryExtractor;
import com.lenovo.ai.memory.model.MemoryItem;
import com.lenovo.ai.memory.store.UserMemoryStore;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.stream.Collectors;

/**
 * 记忆模块统一门面。
 * <p>对外只暴露本类，调用方不再直接依赖 ContextBuilder / UserMemoryStore / MemoryExtractor。</p>
 *
 * @author laifo
 * @date 2026-10-13
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MemoryFacade {

    /** 默认召回条数 */
    private static final int DEFAULT_RECALL_TOP_K = 5;

    private final ContextBuilder contextBuilder;
    private final UserMemoryStore userMemoryStore;
    private final MemoryExtractor memoryExtractor;

    // =========================================================
    // 上下文
    // =========================================================

    /**
     * 构建对话上下文（含长期记忆召回 + 历史摘要 + 本轮用户消息）。
     *
     * @param userId     用户 ID；为 null/blank 时跳过长期记忆召回
     * @param sessionId  会话 ID
     * @param newUserMsg 本轮用户消息；null/blank 时不追加
     */
    public List<Message> buildContext(String userId, String sessionId, String newUserMsg) {
        String memoryBlock = recallAsBlock(userId, newUserMsg);
        return contextBuilder.build(sessionId, newUserMsg, memoryBlock);
    }

    // =========================================================
    // 抽取
    // =========================================================

    /**
     * 异步抽取本轮对话的长期记忆。
     *
     * @param userId 用户 ID；为 null/blank 时跳过
     * @param turn   本轮对话（通常为 user + assistant 两条）
     */
    public void extractAsync(String userId, List<Message> turn) {
        if (userId == null || userId.isBlank()) {
            log.debug(">>> extractAsync 跳过：userId 为空");
            return;
        }
        if (turn == null || turn.isEmpty()) {
            log.debug(">>> extractAsync 跳过：turn 为空");
            return;
        }
        memoryExtractor.extract(userId, turn);
    }

    // =========================================================
    // 召回
    // =========================================================

    /**
     * 召回长期记忆并格式化为文本块（供上下文注入）。
     *
     * @return 命中时返回 "- [TYPE] content" 的多行文本；未命中返回空串
     */
    public String recallAsBlock(String userId, String query) {
        List<MemoryItem> items = recall(userId, query);
        if (items.isEmpty()) return "";
        return items.stream()
                .map(i -> "- [" + i.type() + "] " + i.content())
                .collect(Collectors.joining("\n"));
    }

    /** 召回长期记忆（默认 topK=5） */
    public List<MemoryItem> recall(String userId, String query) {
        return recall(userId, query, DEFAULT_RECALL_TOP_K);
    }

    /** 召回长期记忆（指定 topK） */
    public List<MemoryItem> recall(String userId, String query, int topK) {
        if (userId == null || userId.isBlank() || query == null || query.isBlank()) {
            return List.of();
        }
        return userMemoryStore.recall(userId, query, topK);
    }

    // =========================================================
    // 写入（管理端 / 测试）
    // =========================================================

    /** 手动写入一条记忆 */
    public void save(String userId, String type, String content, double importance) {
        if (userId == null || userId.isBlank()) {
            throw new IllegalArgumentException("userId 不能为空");
        }
        userMemoryStore.save(userId, type, content, importance);
    }
}