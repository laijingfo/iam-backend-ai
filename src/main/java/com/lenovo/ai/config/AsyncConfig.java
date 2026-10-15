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

    /**
     * 上下文装配线程池。
     *
     * <p>AiAgentRunner 在每轮开始时需要拉取三份远程数据：
     * 当前用户档案（Lenovo 接口）、提及用户档案（≤3 次 Lenovo 接口）、
     * 长期记忆召回（embedding + RediSearch）。
     * 原先三者串行执行，首字延迟是三次 RT 之和；
     * 这里提供专用线程池让它们并行，并为每一项加超时降级。</p>
     *
     * <p>注意：不要用 memoryExecutor —— 它是给 {@code @Async} 记忆抽取用的，
     * 队列被占满会导致抽取任务被拒。</p>
     */
    @Bean("contextExecutor")
    public Executor contextExecutor() {
        ThreadPoolTaskExecutor ex = new ThreadPoolTaskExecutor();
        ex.setCorePoolSize(2);
        ex.setMaxPoolSize(8);
        ex.setQueueCapacity(100);
        ex.setThreadNamePrefix("ctx-assemble-");
        // 上下文装配是"尽力而为"，满了就丢弃，由调用方降级
        ex.setRejectedExecutionHandler(new java.util.concurrent.ThreadPoolExecutor.AbortPolicy());
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
     * 表现为工具固定 30s 超时、返回"工具执行失败: null"，
     * 模型拿不到结果只能凭训练数据编造答案。</p>
     *
     * <p><b>为什么不能用 ForkJoinPool.commonPool</b>：</p>
     * <p>并行度仅 {@code cores-1}（小容器可能是 1），
     * 且被 LangChain4j 的流式回调占用，同样会饿死。</p>
     *
     * <p>本池 corePoolSize &gt; 0，保证提交即有线程可用；
     * 且永不与 ai-chat 池互相等待。</p>
     */
    @Bean("aiToolExecutor")
    public Executor aiToolExecutor() {
        ThreadPoolTaskExecutor ex = new ThreadPoolTaskExecutor();
        ex.setCorePoolSize(4);
        ex.setMaxPoolSize(16);
        ex.setQueueCapacity(200);
        ex.setThreadNamePrefix("ai-tool-");
        // 工具执行失败要显式报错，不静默丢弃
        ex.setRejectedExecutionHandler(new java.util.concurrent.ThreadPoolExecutor.AbortPolicy());
        ex.initialize();
        return ex;
    }
}
