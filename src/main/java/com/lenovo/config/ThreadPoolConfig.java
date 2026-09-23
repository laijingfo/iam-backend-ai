package com.lenovo.config;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.AsyncTaskExecutor;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import java.util.concurrent.ThreadPoolExecutor;

/**
 * @Description TODO 发送lm,bpo邮件/发送user邮件的线程池
 * @ClassName ThreadPoolConfig
 * @Author wangfenglong
 * @Date 2026/3/13 14:56
 **/
@EnableAsync
@Configuration
@RequiredArgsConstructor
public class ThreadPoolConfig
{
    private final AsyncEmailProperties asyncEmailProperties;

    @Bean(name = {"emailSendExecutor", "taskExecutor"})
    public AsyncTaskExecutor emailSendExecutor()
    {
        AsyncEmailProperties.EmailProperties prop = asyncEmailProperties.getEmail();
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(prop.getCorePoolSize());
        executor.setMaxPoolSize(prop.getMaxPoolSize());
        executor.setQueueCapacity(prop.getQueueCapacity());
        executor.setThreadNamePrefix(prop.getThreadNamePrefix());
        executor.setKeepAliveSeconds(prop.getKeepAliveSeconds());
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(prop.getAwaitTerminationSeconds());
        executor.initialize();
        return new ContextAwareTaskExecutor(executor);
    }

    @Bean("sendUserEmailExecutor")
    public ThreadPoolTaskExecutor sendUserEmailExecutor()
    {
        AsyncEmailProperties.EmailProperties prop = asyncEmailProperties.getSendUser();
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(prop.getCorePoolSize());
        executor.setMaxPoolSize(prop.getMaxPoolSize());
        executor.setQueueCapacity(prop.getQueueCapacity());
        executor.setKeepAliveSeconds(prop.getKeepAliveSeconds());
        executor.setThreadNamePrefix(prop.getThreadNamePrefix());
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(prop.getAwaitTerminationSeconds());
        executor.initialize();
        return executor;
    }

    @Bean("sendLineManagerMailExecutor")
    public ThreadPoolTaskExecutor sendLineManagerMailExecutor()
    {
        AsyncEmailProperties.EmailProperties prop = asyncEmailProperties.getEmail();
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(prop.getCorePoolSize());
        executor.setMaxPoolSize(prop.getMaxPoolSize());
        executor.setQueueCapacity(prop.getQueueCapacity());
        executor.setKeepAliveSeconds(prop.getKeepAliveSeconds());
        executor.setThreadNamePrefix("LineManagerMail-");
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(prop.getAwaitTerminationSeconds());
        executor.initialize();
        return executor;
    }

    @Bean("sendBpoMailExecutor")
    public ThreadPoolTaskExecutor sendBpoMailExecutor()
    {
        AsyncEmailProperties.EmailProperties prop = asyncEmailProperties.getEmail();
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(prop.getCorePoolSize());
        executor.setMaxPoolSize(prop.getMaxPoolSize());
        executor.setQueueCapacity(prop.getQueueCapacity());
        executor.setKeepAliveSeconds(prop.getKeepAliveSeconds());
        executor.setThreadNamePrefix("BpoMail-");
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(prop.getAwaitTerminationSeconds());
        executor.initialize();
        return executor;
    }

}
