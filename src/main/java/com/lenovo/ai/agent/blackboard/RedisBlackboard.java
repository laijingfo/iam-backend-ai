package com.lenovo.ai.agent.blackboard;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

@Slf4j
public class RedisBlackboard implements Blackboard {

    private static final String PREFIX = "ai:blackboard:";
    private static final long TTL_MINUTES = 30;

    private final StringRedisTemplate redis;
    private final String boardId;
    private final ObjectMapper objectMapper;

    public RedisBlackboard(StringRedisTemplate redis, String boardId, ObjectMapper objectMapper) {
        this.redis = redis;
        this.boardId = boardId;
        this.objectMapper = objectMapper;
    }

    private String key(String suffix) {
        return PREFIX + boardId + ":" + suffix;
    }

    @Override
    public String boardId() {
        return boardId;
    }

    // ===== 任务区 =====

    /**
     * 投递子任务。
     *
     * <p>★ P0-5 修复：pending 用 Hash（field=taskId），删除用 HDEL，
     * 天然幂等、O(1)，避免 List + LREM 因 JSON 序列化差异（字段顺序/空格）
     * 匹配失败导致任务永远删不掉、后续 `pendingTasks()` 反复读到已完成任务。</p>
     */
    @Override
    public void postTask(AgentTask task) {
        String k = key("tasks:pending");
        redis.opsForHash().put(k, task.taskId(), toJson(task));
        redis.expire(k, TTL_MINUTES, TimeUnit.MINUTES);
    }

    @Override
    public List<AgentTask> pendingTasks() {
        return redis.opsForHash().values(key("tasks:pending")).stream()
                .map(v -> fromJson(String.valueOf(v), AgentTask.class))
                .filter(Objects::nonNull)
                .toList();
    }

    @Override
    public void markTaskDone(String taskId, AgentResult result) {
        // ★ P0-5 修复：HDEL 按 field 直接删，O(1)、幂等、不依赖 JSON 一致性
        redis.opsForHash().delete(key("tasks:pending"), taskId);
        redis.opsForHash().put(key("tasks:done"), taskId, toJson(result));
        redis.expire(key("tasks:done"), TTL_MINUTES, TimeUnit.MINUTES);
    }

    @Override
    public List<AgentResult> allResults() {
        return redis.opsForHash().values(key("tasks:done")).stream()
                .map(v -> fromJson(String.valueOf(v), AgentResult.class))
                .filter(Objects::nonNull)
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
        try {
            return objectMapper.writeValueAsString(o);
        } catch (Exception e) {
            log.warn("blackboard toJson 失败", e);
            return "{}";
        }
    }

    private <T> T fromJson(String s, Class<T> type) {
        try {
            return objectMapper.readValue(s, type);
        } catch (Exception e) {
            log.warn("blackboard fromJson 失败: {}", s, e);
            return null;
        }
    }
}