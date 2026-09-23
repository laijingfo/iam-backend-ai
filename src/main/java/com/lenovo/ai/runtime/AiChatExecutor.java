package com.lenovo.ai.runtime;

import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * AI 对话专用线程池；不向其他业务暴露通用线程池 Bean。
 *
 * 设计要点：
 * - 核心线程 0，最大并发由 ai.chat.max-concurrent 控制，空闲 60s 回收。
 * - 队列容量有限（ai.chat.queue-capacity），避免无界堆积导致内存问题。
 * - 拒绝策略由调用方决定：execute 返回 false 表示被拒绝，不直接抛异常。
 * - 关闭时先 shutdown 等待任务结束，超时再 shutdownNow。
 */
@Slf4j
@Component
@ConditionalOnProperty(prefix = "ai.chat", name = "enabled", havingValue = "true", matchIfMissing = true)
public final class AiChatExecutor {

    private final ThreadPoolExecutor executor;

    public AiChatExecutor(
            @Value("${ai.chat.max-concurrent:16}") int maxConcurrent,
            @Value("${ai.chat.queue-capacity:100}") int queueCapacity,
            @Value("${ai.chat.keep-alive-seconds:60}") long keepAliveSeconds) {
        if (maxConcurrent < 1) {
            throw new IllegalArgumentException("AI 聊天最大并发数必须大于 0");
        }
        if (queueCapacity < 1) {
            throw new IllegalArgumentException("AI 聊天队列容量必须大于 0");
        }

        AtomicInteger sequence = new AtomicInteger();
        ThreadFactory defaultFactory = Executors.defaultThreadFactory();
        ThreadFactory namedFactory = task -> {
            Thread thread = defaultFactory.newThread(task);
            thread.setName("ai-chat-" + sequence.incrementAndGet());
            thread.setDaemon(false);
            return thread;
        };

        this.executor = new ThreadPoolExecutor(
                0,
                maxConcurrent,
                keepAliveSeconds,
                TimeUnit.SECONDS,
                new LinkedBlockingQueue<>(queueCapacity),
                namedFactory,
                new ThreadPoolExecutor.AbortPolicy()   // 拒绝时抛 RejectedExecutionException
        );
        this.executor.allowCoreThreadTimeOut(true);
    }

    /**
     * 提交任务。被拒绝时返回 false，不抛异常，由调用方决定如何提示。
     */
    public boolean submit(Runnable task) {
        if (task == null) {
            return false;
        }
        try {
            executor.execute(task);
            return true;
        } catch (RejectedExecutionException e) {
            log.warn("AiChatExecutor 已满，任务被拒绝。activeCount={}, queueSize={}",
                    executor.getActiveCount(), executor.getQueue().size());
            return false;
        }
    }

    /** 当前活跃线程数 */
    public int getActiveCount() {
        return executor.getActiveCount();
    }

    /** 当前排队任务数 */
    public int getQueueSize() {
        return executor.getQueue().size();
    }

    /** 已完成任务数 */
    public long getCompletedTaskCount() {
        return executor.getCompletedTaskCount();
    }

    @PreDestroy
    public void shutdown() {
        executor.shutdown();
        try {
            if (!executor.awaitTermination(30, TimeUnit.SECONDS)) {
                log.warn("AiChatExecutor 30s 内未关闭，强制中断");
                executor.shutdownNow();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            executor.shutdownNow();
        }
    }

    public void execute(Runnable task) {
        executor.execute(task);
    }

}