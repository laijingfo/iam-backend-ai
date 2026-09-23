package com.lenovo.config;

import lombok.RequiredArgsConstructor;
import org.redisson.Redisson;
import org.redisson.api.RedissonClient;
import org.redisson.config.Config;
import org.springframework.boot.autoconfigure.data.redis.RedisProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.StringUtils;

import java.time.Duration;

/** Creates the lock client from the same standard Redis properties used by Lettuce. */
@Configuration(proxyBeanMethods = false)
@RequiredArgsConstructor
public class RedissonSentinelConfig {

    private final RedisProperties redisProperties;

    @Bean(destroyMethod = "shutdown")
    public RedissonClient redissonClient() {
        Config config = new Config();
        RedisProperties.Sentinel sentinel = redisProperties.getSentinel();
        String scheme = redisProperties.getSsl().isEnabled() ? "rediss://" : "redis://";

        if (sentinel != null && StringUtils.hasText(sentinel.getMaster())
                && sentinel.getNodes() != null && !sentinel.getNodes().isEmpty()) {
            var server = config.useSentinelServers()
                    .setMasterName(sentinel.getMaster())
                    .addSentinelAddress(sentinel.getNodes().stream()
                            .map(node -> scheme + node)
                            .toArray(String[]::new))
                    .setDatabase(redisProperties.getDatabase());
            if (StringUtils.hasText(redisProperties.getUsername())) {
                server.setUsername(redisProperties.getUsername());
            }
            if (StringUtils.hasText(redisProperties.getPassword())) {
                server.setPassword(redisProperties.getPassword());
            }
            if (StringUtils.hasText(sentinel.getUsername())) {
                server.setSentinelUsername(sentinel.getUsername());
            }
            if (StringUtils.hasText(sentinel.getPassword())) {
                server.setSentinelPassword(sentinel.getPassword());
            }
            applyTimeouts(server, redisProperties.getConnectTimeout(), redisProperties.getTimeout());
        } else {
            var server = config.useSingleServer()
                    .setAddress(scheme + redisProperties.getHost() + ":" + redisProperties.getPort())
                    .setDatabase(redisProperties.getDatabase());
            if (StringUtils.hasText(redisProperties.getUsername())) {
                server.setUsername(redisProperties.getUsername());
            }
            if (StringUtils.hasText(redisProperties.getPassword())) {
                server.setPassword(redisProperties.getPassword());
            }
            applyTimeouts(server, redisProperties.getConnectTimeout(), redisProperties.getTimeout());
        }
        return Redisson.create(config);
    }

    private static void applyTimeouts(org.redisson.config.BaseConfig<?> config,
                                      Duration connectTimeout, Duration timeout) {
        if (connectTimeout != null) {
            config.setConnectTimeout(Math.toIntExact(connectTimeout.toMillis()));
        }
        if (timeout != null) {
            config.setTimeout(Math.toIntExact(timeout.toMillis()));
        }
    }
}
