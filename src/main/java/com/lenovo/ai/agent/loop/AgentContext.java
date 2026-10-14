package com.lenovo.ai.agent.loop;

import com.lenovo.ai.agent.blackboard.Blackboard;
import com.lenovo.ai.infra.sse.AiChatSseEmitter;
import com.lenovo.ai.llm.Message;
import com.lenovo.ai.llm.enums.MessageRole;
import com.lenovo.ai.tools.context.AiToolContext;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class AgentContext {

    private final List<Message> messages;
    private final AiToolContext toolContext;
    private final AiChatSseEmitter emitter;
    private final String sessionId;
    private final long assistantMessageId;
    private final String runId;
    private final String memoryId;
    private final Set<String> confirmedCalls = ConcurrentHashMap.newKeySet();

    // ===== 原有字段 =====
    private String agentName;
    private List<String> allowedToolNames;
    private int maxSteps = 8;
    private Blackboard blackboard;
    private boolean streamToUser = true;

    // ★ 新增：模型名（由 AiAgentRunner 路由后写入）
    private String modelName;

    public AgentContext(List<Message> messages, AiToolContext toolContext,
                        AiChatSseEmitter emitter, String sessionId,
                        long assistantMessageId, String runId, String memoryId) {
        this.messages = messages == null ? new ArrayList<>() : messages;
        this.toolContext = toolContext;
        this.emitter = emitter;
        this.sessionId = sessionId;
        this.assistantMessageId = assistantMessageId;
        this.runId = runId;
        this.memoryId = memoryId;
    }

    // ===== 原有 getter =====
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

    public String agentName() { return agentName; }
    public void setAgentName(String agentName) { this.agentName = agentName; }

    public List<String> allowedToolNames() { return allowedToolNames; }
    public void setAllowedToolNames(List<String> allowedToolNames) {
        this.allowedToolNames = allowedToolNames;
    }

    public int maxSteps() { return maxSteps; }
    public void setMaxSteps(int maxSteps) { this.maxSteps = maxSteps; }

    public Blackboard blackboard() { return blackboard; }
    public void setBlackboard(Blackboard blackboard) { this.blackboard = blackboard; }

    public boolean streamToUser() { return streamToUser; }
    public void setStreamToUser(boolean streamToUser) { this.streamToUser = streamToUser; }

    // ★ 新增
    public String getModelName() { return modelName; }
    public void setModelName(String modelName) { this.modelName = modelName; }

    public AgentContext fork(String instruction) {
        List<Message> sub = new ArrayList<>();

        // ★ 继承父 ctx 的 system 消息（保留全局约束）
        for (Message m : this.messages) {
            if (m.role() == MessageRole.SYSTEM) {
                sub.add(m);
            }
        }

        sub.add(Message.user(instruction));

        AgentContext child = new AgentContext(sub, this.toolContext, this.emitter,
                this.sessionId, this.assistantMessageId, this.runId, this.memoryId);
        child.setBlackboard(this.blackboard);
        child.setStreamToUser(this.streamToUser);
        child.setModelName(this.modelName);
        return child;
    }

    public String lastAssistantContent() {
        for (int i = messages.size() - 1; i >= 0; i--) {
            Message m = messages.get(i);
            if (m.role() == MessageRole.ASSISTANT
                    && m.content() != null && !m.content().isBlank()) {
                return m.content();
            }
        }
        return "";
    }
}