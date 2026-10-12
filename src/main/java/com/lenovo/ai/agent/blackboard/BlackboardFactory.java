package com.lenovo.ai.agent.blackboard;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

/**
 * @author laifo
 * @version 1.0
 * @date 2026-10-12 13:57
 * @project iam-backend-ai
 * @description
 */
@Component
@RequiredArgsConstructor
public class BlackboardFactory {

    private final StringRedisTemplate redis;

    /** 一次 run 一块黑板，boardId = runId */
    public Blackboard forRun(String runId) {
        return new RedisBlackboard(redis, runId);
    }
}