package com.lenovo.ai.lock;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Collections;

@Component
@RequiredArgsConstructor
public class RedisLockHelper {

    private static final String UNLOCK_LUA =
            "if redis.call('get', KEYS[1]) == ARGV[1] then " +
                    "return redis.call('del', KEYS[1]) else return 0 end";

    private final StringRedisTemplate redis;

    public boolean tryLock(String key, String token, long ttlMillis) {
        Boolean ok = redis.opsForValue()
                .setIfAbsent(key, token, Duration.ofMillis(ttlMillis));
        return Boolean.TRUE.equals(ok);
    }

    public void unlock(String key, String token) {
        redis.execute(new DefaultRedisScript<>(UNLOCK_LUA, Long.class),
                Collections.singletonList(key), token);
    }

    public void forceUnlock(String key) {
        redis.delete(key);
    }
}