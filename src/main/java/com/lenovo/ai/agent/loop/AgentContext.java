package com.lenovo.ai.agent.loop;

import com.lenovo.ai.infra.sse.AiChatSseEmitter;
import com.lenovo.ai.llm.Message;
import com.lenovo.ai.tools.context.AiToolContext;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 一次 Agent 运行（run）的上下文：消息列表、工具上下文、SSE emitter、
 * 历史落库所需的 sessionId / assistantMessageId，以及已确认的工具调用集合。
 */
public class AgentContext {

    private final List<Message> messages;
    private final AiToolContext toolContext;
    private final AiChatSseEmitter emitter;
    private final String sessionId;
    private final long assistantMessageId;
    private final String runId;
    private final String memoryId;
    private final Set<String> confirmedCalls = ConcurrentHashMap.newKeySet();

    public AgentContext(List<Message> messages, AiToolContext toolContext,
                        AiChatSseEmitter emitter, String sessionId,
                        long assistantMessageId,
                        String runId, String memoryId) {
        this.messages = messages == null ? new ArrayList<>() : messages;
        this.toolContext = toolContext;
        this.emitter = emitter;
        this.sessionId = sessionId;
        this.assistantMessageId = assistantMessageId;
        this.runId = runId;
        this.memoryId = memoryId;
    }

    public List<Message> messages() { return messages; }
    public AiToolContext toolContext() { return toolContext; }
    public AiChatSseEmitter emitter() { return emitter; }
    public String sessionId() { return sessionId; }
    public long assistantMessageId() { return assistantMessageId; }
    public String runId() { return runId; }
    public String memoryId() { return memoryId; }
    public boolean confirmed(String callId) { return confirmedCalls.contains(callId); }
    public void markConfirmed(String callId) { confirmedCalls.add(callId); }
    public List<String> confirmedCallsSnapshot() { return new ArrayList<>(confirmedCalls); }
}
