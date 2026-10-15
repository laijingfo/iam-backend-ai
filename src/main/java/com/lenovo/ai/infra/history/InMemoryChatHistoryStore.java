package com.lenovo.ai.infra.history;

import com.lenovo.ai.llm.Message;
import com.lenovo.ai.llm.enums.MessageRole;
import com.lenovo.dto.AiChatPolicyResponse;
import com.lenovo.dto.AiChatSessionResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * ChatHistoryStore 的内存实现（进程内保存，重启丢失）。
 * <p>
 * TODO 待 PG 的 AI 聊天相关表就绪后替换为数据库实现。
 */
@Slf4j
@Component
//@ConditionalOnMissingBean(ChatHistoryStore.class)
public class InMemoryChatHistoryStore implements ChatHistoryStore {

    private record StoredMessage(long id, String sessionId, String userId,
                                 String role, String content, String extra) {}

    private record SessionMeta(String owner, String title,
                               LocalDateTime createdAt, LocalDateTime updatedAt) {}

    private final Map<String, SessionMeta> sessions = new ConcurrentHashMap<>();

    // ★ R8 修复：按 sessionId 分组存储，避免 load(sessionId) 全表扫描。
    //   旧结构：Map<Long, StoredMessage>，每次 load 要遍历所有会话的所有消息。
    //   新结构：Map<String sessionId, Map<Long msgId, StoredMessage>>，load 只遍历单个会话。
    private final Map<String, Map<Long, StoredMessage>> sessionMessages = new ConcurrentHashMap<>();

    private final AtomicLong idGen = new AtomicLong(1);

    @Override
    public boolean exists(String sessionId) {
        return sessions.containsKey(sessionId);
    }

    @Override
    public void assertOwner(String sessionId, String userId) {
        SessionMeta meta = sessions.get(sessionId);
        if (meta != null && !meta.owner().equals(userId)) {
            throw new IllegalStateException("会话不属于当前用户");
        }
    }

    @Override
    public long nextMessageId() {
        return idGen.getAndIncrement();
    }

    @Override
    public void append(String sessionId, String userId, long messageId,
                       String role, String content, String extra) {
        LocalDateTime now = LocalDateTime.now();
        sessions.compute(sessionId, (k, old) -> {
            String owner;
            if (old != null && old.owner() != null && !old.owner().isBlank()) {
                owner = old.owner();
            } else {
                owner = (userId == null || userId.isBlank()) ? "" : userId;
            }
            String title = old != null && old.title() != null && !old.title().isBlank()
                    ? old.title() : firstLine(content);
            LocalDateTime created = old != null ? old.createdAt() : now;
            return new SessionMeta(owner, title, created, now);
        });
        sessionMessages.computeIfAbsent(sessionId, k -> new ConcurrentHashMap<>())
                .put(messageId, new StoredMessage(messageId, sessionId, userId, role, content, extra));
    }

    @Override
    public void appendAssistant(long assistantMessageId, String sessionId, String content) {
        SessionMeta meta = sessions.get(sessionId);
        if (meta == null) {
            log.warn("appendAssistant 时 session 不存在: sessionId={}, assistantMessageId={}",
                    sessionId, assistantMessageId);
            return;   // ★ 不写，避免创建空 owner session
        }
        append(sessionId, meta.owner(), assistantMessageId, "ASSISTANT", content, null);
    }

    @Override
    public AiChatPolicyResponse policyStatus(String currentUserId) {
        // TODO 待政策表就绪后完善真实查询，当前固定返回需要同意政策的状态。
        AiChatPolicyResponse response = new AiChatPolicyResponse();
        response.setFirst(true);
        response.setOver30(true);
        return response;
    }

    @Override
    public void acceptPolicy(String currentUserId) {
        // TODO 待政策表就绪后完善：mapper.acceptPolicy(owner)
    }

    @Override
    public List<AiChatSessionResponse> sessions(String currentUserId) {
        List<AiChatSessionResponse> result = new ArrayList<>();
        sessions.forEach((sessionId, meta) -> {
            if (!meta.owner().equals(currentUserId)) return;
            AiChatSessionResponse item = new AiChatSessionResponse();
            item.setSessionId(sessionId);
            item.setTitle(meta.title());
            item.setCreatedAt(meta.createdAt());
            item.setUpdatedAt(meta.updatedAt());
            result.add(item);
        });
        result.sort(Comparator.comparing(AiChatSessionResponse::getUpdatedAt,
                Comparator.nullsLast(Comparator.reverseOrder())));
        return result;
    }

    @Override
    public String loadAssistant(long assistantMessageId) {
        // ★ R8 修复：辅助消息 ID 是全局唯一，需要遍历所有会话。
        //   但 loadAssistant 调用频率低（每轮结束 1 次），可接受。
        //   若未来成为瓶颈，可再加 Map<Long, String sessionId> 的反向索引。
        for (Map<Long, StoredMessage> perSession : sessionMessages.values()) {
            StoredMessage message = perSession.get(assistantMessageId);
            if (message != null && "ASSISTANT".equalsIgnoreCase(message.role())) {
                return message.content() == null ? "" : message.content();
            }
        }
        return "";
    }

    @Override
    public List<Message> load(String sessionId) {
        // ★ R8 修复：只遍历单个会话的消息
        Map<Long, StoredMessage> perSession = sessionMessages.get(sessionId);
        if (perSession == null || perSession.isEmpty()) return List.of();
        return perSession.values().stream()
                .sorted(Comparator.comparingLong(StoredMessage::id))
                .map(this::toMessage)
                .toList();
    }

    @Override
    public void appendUser(long userMessageId, String sessionId, String userId, String message) {
        append(sessionId, userId, userMessageId, "USER", message, null);
    }

    private Message toMessage(StoredMessage m) {
        MessageRole role;
        try {
            role = MessageRole.valueOf(m.role().toUpperCase());
        } catch (Exception e) {
            role = MessageRole.USER;
        }
        return new Message(role, m.content(), null, null);
    }

    private String firstLine(String content) {
        if (content == null) return "";
        String line = content.lines().findFirst().orElse("");
        return line.length() > 50 ? line.substring(0, 50) : line;
    }
}