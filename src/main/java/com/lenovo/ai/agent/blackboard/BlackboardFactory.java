package com.lenovo.ai.agent.blackboard;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class BlackboardFactory {

    private final StringRedisTemplate redis;
    private final ObjectMapper objectMapper;   // ★ 注入全局 Bean

    /** 一次 run 一块黑板，boardId = runId */
    public Blackboard forRun(String runId) {
        return new RedisBlackboard(redis, runId, objectMapper);
    }
}