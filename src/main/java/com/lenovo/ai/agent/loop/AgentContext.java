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

/**
 * 一次 Agent 运行（run）的上下文。
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

    // ===== 新增字段 =====
    private String agentName;
    private List<String> allowedToolNames;
    private int maxSteps = 8;
    private Blackboard blackboard;
    private boolean streamToUser = true;

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

    // ===== 新增 getter/setter =====
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

    /**
     * 派生一个子上下文，用于子任务：
     * - 复制 runId/sessionId/assistantMessageId/blackboard/streamToUser
     * - messages 重置为只含一条 user 指令
     * - 继承工具上下文和 SSE（同一个 emitter）
     */
    public AgentContext fork(String instruction) {
        List<Message> sub = new ArrayList<>();
        sub.add(Message.user(instruction));
        AgentContext child = new AgentContext(sub, this.toolContext, this.emitter,
                this.sessionId, this.assistantMessageId, this.runId, this.memoryId);
        child.setBlackboard(this.blackboard);
        child.setStreamToUser(this.streamToUser);
        return child;
    }

    /** 取最后一条 assistant 文本，用于 Orchestrator 汇总 */
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