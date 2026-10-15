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
 * 一次 Agent 执行的上下文。
 *
 * <p><b>assistantMessageId 哨兵约定</b>：
 * 值为 {@link #NO_PERSIST_ID}（0）时表示"本 ctx 的结果不落库"。
 * 多 Agent 编排的子任务必须使用该哨兵——它们的结果会被 Synthesizer 汇总后
 * 由 Orchestrator 统一落到主 ctx 的消息 ID 上，子任务自行落库只会产生
 * 中间态脏数据（例如配额超时的提示文本被当成最终答案）。</p>
 */
public class AgentContext {

    /** 哨兵：不落库的消息 ID */
    public static final long NO_PERSIST_ID = 0L;

    private final List<Message> messages;
    private final AiToolContext toolContext;
    private AiChatSseEmitter emitter;
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

    /** 本 ctx 是否需要将结果落库 */
    public boolean shouldPersist() { return assistantMessageId > NO_PERSIST_ID; }

    /** ★ 新增：替换 emitter（用于给子任务挂 childView） */
    public void setEmitter(AiChatSseEmitter emitter) {
        if (emitter != null) this.emitter = emitter;
    }

    // ★ 新增
    public String getModelName() { return modelName; }
    public void setModelName(String modelName) { this.modelName = modelName; }

    /**
     * 派生子上下文（不继承历史对话）。
     * <p>子 ctx 使用哨兵 messageId，结果不落库。</p>
     */
    public AgentContext fork(String instruction) {
        return fork(instruction, false);
    }

    /**
     * 派生子上下文。
     *
     * @param instruction    子任务指令（作为 user 消息）
     * @param inheritHistory 是否继承父 ctx 的非 system 历史消息。
     *                       子任务 worker 需要看上文做指代消解时传 true
     */
    public AgentContext fork(String instruction, boolean inheritHistory) {
        List<Message> sub = new ArrayList<>();

        // ★ P1 修复：inheritHistory 时跳过"当前轮最后一条 user"，
        //   避免子任务出现两条连续 user（原始问题 + instruction）。
        int lastUserIdx = -1;
        if (inheritHistory) {
            for (int i = this.messages.size() - 1; i >= 0; i--) {
                if (this.messages.get(i).role() == MessageRole.USER) {
                    lastUserIdx = i;
                    break;
                }
            }
        }

        for (int i = 0; i < this.messages.size(); i++) {
            Message m = this.messages.get(i);
            if (m.role() == MessageRole.SYSTEM) {
                sub.add(m);
            } else if (inheritHistory) {
                // 跳过当前轮最后一条 user
                if (i == lastUserIdx) continue;
                sub.add(m);
            }
        }

        sub.add(Message.user(instruction));

        AgentContext child = new AgentContext(sub, this.toolContext, this.emitter,
                this.sessionId, NO_PERSIST_ID, this.runId, this.memoryId);
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
