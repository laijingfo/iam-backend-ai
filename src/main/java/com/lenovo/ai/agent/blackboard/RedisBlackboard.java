package com.lenovo.ai.agent.blackboard;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.util.List;
import java.util.concurrent.TimeUnit;

@Slf4j
public class RedisBlackboard implements Blackboard {

    private static final String PREFIX = "ai:blackboard:";
    private static final long TTL_MINUTES = 30;
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final StringRedisTemplate redis;
    private final String boardId;

    public RedisBlackboard(StringRedisTemplate redis, String boardId) {
        this.redis = redis;
        this.boardId = boardId;
    }

    private String key(String suffix) { return PREFIX + boardId + ":" + suffix; }

    @Override public String boardId() { return boardId; }

    // ===== 任务区 =====

    @Override
    public void postTask(AgentTask task) {
        redis.opsForList().rightPush(key("tasks:pending"), toJson(task));
        redis.expire(key("tasks:pending"), TTL_MINUTES, TimeUnit.MINUTES);
    }

    @Override
    public List<AgentTask> pendingTasks() {
        List<String> raw = redis.opsForList().range(key("tasks:pending"), 0, -1);
        if (raw == null) return List.of();
        return raw.stream().map(s -> fromJson(s, AgentTask.class)).toList();
    }

    @Override
    public void markTaskDone(String taskId, AgentResult result) {
        List<AgentTask> pending = pendingTasks();
        for (AgentTask t : pending) {
            if (t.taskId().equals(taskId)) {
                redis.opsForList().remove(key("tasks:pending"), 1, toJson(t));
                break;
            }
        }
        redis.opsForHash().put(key("tasks:done"), taskId, toJson(result));
        redis.expire(key("tasks:done"), TTL_MINUTES, TimeUnit.MINUTES);
    }

    @Override
    public List<AgentResult> allResults() {
        return redis.opsForHash().values(key("tasks:done")).stream()
                .map(v -> fromJson(String.valueOf(v), AgentResult.class))
                .filter(java.util.Objects::nonNull)
                .toList();
    }

    // ===== 结果区 =====

    @Override
    public void putResult(String k, Object value) {
        redis.opsForHash().put(key("results"), k, toJson(value));
        redis.expire(key("results"), TTL_MINUTES, TimeUnit.MINUTES);
    }

    @Override
    public <T> T getResult(String k, Class<T> type) {
        Object raw = redis.opsForHash().get(key("results"), k);
        return raw == null ? null : fromJson(String.valueOf(raw), type);
    }

    // ===== 事实区 =====

    @Override
    public void appendFact(String fact) {
        redis.opsForList().rightPush(key("facts"), fact);
        redis.expire(key("facts"), TTL_MINUTES, TimeUnit.MINUTES);
    }

    @Override
    public List<String> facts() {
        List<String> raw = redis.opsForList().range(key("facts"), 0, -1);
        return raw == null ? List.of() : raw;
    }

    // ===== 最终答案 =====

    @Override
    public void setFinalAnswer(String answer) {
        redis.opsForValue().set(key("final"), answer, TTL_MINUTES, TimeUnit.MINUTES);
    }

    @Override
    public String finalAnswer() {
        return redis.opsForValue().get(key("final"));
    }

    // ===== 工具 =====

    private String toJson(Object o) {
        try { return MAPPER.writeValueAsString(o); }
        catch (Exception e) { log.warn("blackboard toJson 失败", e); return "{}"; }
    }

    private <T> T fromJson(String s, Class<T> type) {
        try { return MAPPER.readValue(s, type); }
        catch (Exception e) { log.warn("blackboard fromJson 失败: {}", s, e); return null; }
    }
}