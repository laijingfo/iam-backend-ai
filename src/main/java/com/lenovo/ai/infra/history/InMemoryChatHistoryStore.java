package com.lenovo.ai.infra.history;

import com.lenovo.dto.AiChatPolicyResponse;
import com.lenovo.dto.AiChatSessionResponse;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

@Component
public class InMemoryChatHistoryStore implements ChatHistoryStore {

    private final Map<String, String> owners = new ConcurrentHashMap<>();
    private final AtomicLong idGen = new AtomicLong(1);

    @Override
    public boolean exists(String sessionId) {
        return owners.containsKey(sessionId);
    }

    @Override
    public void assertOwner(String sessionId, String userId) {
        String owner = owners.get(sessionId);
        if (owner != null && !owner.equals(userId)) {
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
        owners.putIfAbsent(sessionId, userId);
        // TODO 写入 PG
    }

    public AiChatPolicyResponse policyStatus(String currentUserId) {
        //TODO 待完善
        AiChatPolicyResponse aiChatPolicyResponse = new AiChatPolicyResponse();
        aiChatPolicyResponse.setFirst(true);
        aiChatPolicyResponse.setOver30(true);
        return aiChatPolicyResponse;
    }

    @Transactional
    public void acceptPolicy(String owner) {
        //TODO 待完善
//        mapper.acceptPolicy(owner);
    }

    @Override
    public List<AiChatSessionResponse> sessions(String currentUserId) {
        //TODO 待完善
        return List.of();
    }

    // 在现有 ChatHistoryStore 里补上以下方法：

    /** 加载某个 session 的完整历史，转成 List<Message> */
    public List<com.lenovo.ai.llm.Message> load(String sessionId) {
        // 从你现有的存储结构读，转成 Message
        // 例如：List<ChatMessage> list = ...;
        // return list.stream().map(m -> new Message(
        //     MessageRole.valueOf(m.getRole().toUpperCase()),
        //     m.getContent(), null, null)).toList();
        return java.util.List.of();
    }

    public void appendUser(long id, String sessionId, String userId, String content) {
        // 落库 user 消息
    }

    public void appendAssistant(long id, String content) {
        // 落库 assistant 消息
    }

    public String loadAssistant(long assistantMessageId) {
        // 读 assistant 消息内容
        return "";
    }
}