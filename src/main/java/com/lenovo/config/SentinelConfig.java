package com.lenovo.config;

import io.lettuce.core.ClientOptions;
import io.lettuce.core.ReadFrom;
import io.lettuce.core.SocketOptions;
import org.springframework.boot.autoconfigure.data.redis.LettuceClientConfigurationBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import java.time.Duration;

@Configuration
public class SentinelConfig {

    @Bean
    public LettuceClientConfigurationBuilderCustomizer lettuceCustomizer() {
        return clientConfigurationBuilder -> {
            // 优先从主节点读取，主节点不可用时从副本读取
            clientConfigurationBuilder.readFrom(ReadFrom.MASTER_PREFERRED);

            // 启用拓扑刷新
            clientConfigurationBuilder.clientOptions(
                    ClientOptions.builder()
                            .autoReconnect(true)
                            .disconnectedBehavior(ClientOptions.DisconnectedBehavior.REJECT_COMMANDS)
                            .socketOptions(SocketOptions.builder()
                                    .connectTimeout(Duration.ofSeconds(5))
                                    .build())
                            .build()  // 添加这个 build()
            );  // 修复括号匹配
        };
    }
}