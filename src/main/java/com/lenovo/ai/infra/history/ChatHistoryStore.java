package com.lenovo.ai.infra.history;

import com.lenovo.ai.llm.Message;
import com.lenovo.dto.AiChatPolicyResponse;
import com.lenovo.dto.AiChatSessionResponse;

import java.util.List;

public interface ChatHistoryStore {

    boolean exists(String sessionId);

    void assertOwner(String sessionId, String userId);

    long nextMessageId();

    void append(String sessionId, String userId, long messageId,
                String role, String content, String extra);

    AiChatPolicyResponse policyStatus(String currentUserId);

    void acceptPolicy(String currentUserId);

    List<AiChatSessionResponse> sessions(String currentUserId);

    String loadAssistant(long assistantMessageId);

    List<Message> load(String sessionId);

    void appendUser(long userMessageId, String sessionId, String userId, String message);

    void appendAssistant(long l, String content);
}