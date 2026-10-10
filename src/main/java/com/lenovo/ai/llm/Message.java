package com.lenovo.ai.llm;

import com.lenovo.ai.llm.enums.MessageRole;

import java.util.List;
import java.util.Map;


/**
 * @author laifo
 * @version 1.0
 * @date 2026-09-30 10:08
 * @project iam-backend-ai
 * @description
 */
public record Message(
        MessageRole role,
        String content,
        String toolCallId,
        List<ToolCallRef> toolCalls
) {
    public static Message system(String c) { return new Message(MessageRole.SYSTEM, c, null, null); }
    public static Message user(String c)   { return new Message(MessageRole.USER, c, null, null); }
    public static Message assistant(String c) { return new Message(MessageRole.ASSISTANT, c, null, null); }
    public static Message assistantWithTools(String c, List<ToolCallRef> calls) {
        return new Message(MessageRole.ASSISTANT, c, null, calls);
    }
    public static Message toolResult(String callId, String c) {
        return new Message(MessageRole.TOOL, c, callId, null);
    }
    public boolean isSystem() { return role == MessageRole.SYSTEM; }

    public record ToolCallRef(String id, String name, Map<String, Object> args) {}
}
