package com.lenovo.ai.agent.loop;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lenovo.ai.llm.Message;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

@Slf4j
@Component
@RequiredArgsConstructor
public class PendingStateStore {

    private static final String KEY_PREFIX = "ai:pending:";

    private final StringRedisTemplate redisTemplate;
//    private final ObjectMapper objectMapper = new ObjectMapper();
    private final ObjectMapper objectMapper;
    public void save(AgentContext ctx) {
        try {
            PendingState state = new PendingState(
                    ctx.runId(),
                    ctx.memoryId(),
                    ctx.assistantMessageId(),
                    ctx.sessionId(),                          // ✅ 新增
                    new ArrayList<>(ctx.messages()),
                    ctx.confirmedCallsSnapshot(),
                    ctx.toolContext() == null ? null : ctx.toolContext().getUserId()
            );
            redisTemplate.opsForValue().set(
                    KEY_PREFIX + ctx.runId(),
                    objectMapper.writeValueAsString(state),
                    10, TimeUnit.MINUTES);
        } catch (Exception e) {
            log.error("保存挂起状态失败", e);
        }
    }

    public PendingState load(String runId) {
        String raw = redisTemplate.opsForValue().get(KEY_PREFIX + runId);
        if (raw == null) return null;
        try {
            return objectMapper.readValue(raw, new TypeReference<>() {});
        } catch (Exception e) {
            log.error("读取挂起状态失败", e);
            return null;
        }
    }

    public void remove(String runId) {
        redisTemplate.delete(KEY_PREFIX + runId);
    }

    public record PendingState(
            String runId,
            String memoryId,
            long assistantMessageId,
            String sessionId,                 // ✅ 新增
            List<Message> messages,
            List<String> confirmedCalls,
            String userId
    ) {}
}