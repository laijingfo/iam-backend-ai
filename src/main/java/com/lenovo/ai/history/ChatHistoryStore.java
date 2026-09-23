package com.lenovo.ai.history;

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
}