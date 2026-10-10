package com.lenovo.ai.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;

/**
 * AI 模块线程池配置。
 * <p>
 * {@code @EnableAsync} 已在 ThreadPoolConfig 中声明，{@code @EnableScheduling}
 * 已在 ItscServerApplication 主类中声明，这里不再重复添加以避免冲突。
 */
@Configuration
public class AsyncConfig {

    @Bean("memoryExecutor")
    public Executor memoryExecutor() {
        ThreadPoolTaskExecutor ex = new ThreadPoolTaskExecutor();
        ex.setCorePoolSize(2);
        ex.setMaxPoolSize(4);
        ex.setQueueCapacity(200);
        ex.setThreadNamePrefix("memory-");
        ex.initialize();
        return ex;
    }
}