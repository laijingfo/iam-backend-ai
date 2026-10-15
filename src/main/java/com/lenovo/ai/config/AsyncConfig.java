package com.lenovo.ai.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;

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
        // ★ 记忆抽取是"尽力而为"，队列满时降级为调用线程执行，不丢任务
        ex.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        ex.initialize();
        return ex;
    }

    /**
     * 上下文装配线程池。
     */
    @Bean("contextExecutor")
    public Executor contextExecutor() {
        ThreadPoolTaskExecutor ex = new ThreadPoolTaskExecutor();
        ex.setCorePoolSize(2);
        ex.setMaxPoolSize(8);
        ex.setQueueCapacity(100);
        ex.setThreadNamePrefix("ctx-assemble-");
        // ★ P0-6 修复：AbortPolicy 会抛 RejectedExecutionException，与"满了就降级"的
        //   语义不符。改用 CallerRunsPolicy，队列满时由调用线程执行，起到背压作用；
        //   调用线程本来就是 ai-chat 线程，最坏情况是首字延迟增加，不会丢任务也不会挂。
        ex.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        ex.initialize();
        return ex;
    }

    /**
     * 工具执行专用线程池 —— 必须独立，且 corePoolSize > 0。
     *
     * <p><b>为什么不能用 AiChatExecutor</b>（血泪教训）：</p>
     * <p>AiChatExecutor 是 {@code corePoolSize=0 + LinkedBlockingQueue}。
     * AgentLoop 跑在 ai-chat-N 线程上，执行工具时会 <b>阻塞等待</b> 工具结果。
     * 若工具任务也提交到该池，{@code ThreadPoolExecutor.execute()} 的分支是：</p>
     * <pre>
     *   workerCount(1) &lt; corePoolSize(0) ? 否
     *   → workQueue.offer() 入队成功
     *   → workerCount == 0 ? 否（ai-chat-N 正阻塞在 join 里）
     *   → 不创建线程
     * </pre>
     * <p>于是"执行者"阻塞等待"被排队的任务"，没人来跑 —— 典型自死锁，
     * 表现为工具固定 30s 超时、返回"工具执行失败: null"。</p>
     *
     * <p><b>为什么不能用 ForkJoinPool.commonPool</b>：</p>
     * <p>并行度仅 {@code cores-1}（小容器可能是 1），
     * 且被 LangChain4j 的流式回调占用，同样会饿死。</p>
     */
    @Bean("aiToolExecutor")
    public Executor aiToolExecutor() {
        ThreadPoolTaskExecutor ex = new ThreadPoolTaskExecutor();
        ex.setCorePoolSize(4);
        ex.setMaxPoolSize(16);
        ex.setQueueCapacity(200);
        ex.setThreadNamePrefix("ai-tool-");
        // 工具执行失败要显式报错，不静默丢弃
        ex.setRejectedExecutionHandler(new ThreadPoolExecutor.AbortPolicy());
        ex.initialize();
        return ex;
    }
}