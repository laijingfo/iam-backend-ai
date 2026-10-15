package com.lenovo.ai.infra.lock;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Collections;

@Slf4j
@Component
@RequiredArgsConstructor
public class RedisLockHelper {

    private static final String UNLOCK_LUA =
            "if redis.call('get', KEYS[1]) == ARGV[1] then " +
                    "return redis.call('del', KEYS[1]) else return 0 end";

    /** 续租 Lua：只有持有者才能延长 TTL */
    private static final String RENEW_LUA =
            "if redis.call('get', KEYS[1]) == ARGV[1] then " +
                    "return redis.call('pexpire', KEYS[1], ARGV[2]) else return 0 end";

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

    /**
     * 续租（延长锁的 TTL）。
     *
     * <p>供执行时间可能超过初始 TTL 的定时任务使用（如全量记忆衰减）。
     * 只有 token 匹配才会延长，避免误续别人的锁。</p>
     *
     * @param key       锁 key
     * @param token     持有者 token
     * @param ttlMillis 新的 TTL（毫秒）
     * @return true 表示续租成功
     */
    public boolean renew(String key, String token, long ttlMillis) {
        if (key == null || token == null) return false;
        try {
            Long r = redis.execute(new DefaultRedisScript<>(RENEW_LUA, Long.class),
                    Collections.singletonList(key), token, String.valueOf(ttlMillis));
            return r != null && r == 1L;
        } catch (Exception e) {
            log.warn("续租失败: key={}", key, e);
            return false;
        }
    }

    public void forceUnlock(String key) {
        redis.delete(key);
    }
}