package com.lenovo.ai.agent.blackboard;

import java.util.List;

/**
 * 黑板：一次 run 一块，用于 Agent 间共享状态。
 * blackboardId = runId。
 */
public interface Blackboard {

    String boardId();

    // ===== 任务区 =====
    void postTask(AgentTask task);
    List<AgentTask> pendingTasks();
    void markTaskDone(String taskId, AgentResult result);
    List<AgentResult> allResults();

    // ===== 结果区 =====
    void putResult(String key, Object value);
    <T> T getResult(String key, Class<T> type);

    // ===== 事实区 =====
    void appendFact(String fact);
    List<String> facts();

    // ===== 最终答案 =====
    void setFinalAnswer(String answer);
    String finalAnswer();
}