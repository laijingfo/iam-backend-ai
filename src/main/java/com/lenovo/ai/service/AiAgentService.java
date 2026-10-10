package com.lenovo.ai.service;                                                  // 包声明

import com.lenovo.ai.agent.AiAgentRunner;                                       // Agent 运行器
import com.lenovo.ai.agent.runtime.AiChatExecutor;                              // AI 专用线程池
import com.lenovo.ai.infra.history.ChatHistoryStore;                            // 历史存储
import com.lenovo.ai.infra.lock.RedisLockHelper;                                // Redis 锁
import com.lenovo.ai.infra.sse.AiChatSseEmitter;                                // SSE 发射器
import com.lenovo.ai.tools.context.AiToolContext;                               // 工具上下文
import com.lenovo.controller.ai.AiAgentController.ChatRequest;                  // 请求体
import com.lenovo.util.RedisUtils;                                              // Redis 工具
import lombok.extern.slf4j.Slf4j;                                               // 日志
import org.springframework.context.i18n.LocaleContextHolder;                    // 国际化上下文
import org.springframework.stereotype.Service;                                  // Spring 服务
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;        // SSE 基类

import java.util.Locale;                                                        // 语言
import java.util.Map;                                                           // Map
import java.util.UUID;                                                          // UUID
import java.util.concurrent.ConcurrentHashMap;                                  // 并发 Map

@Slf4j                                                                          // 日志
@Service                                                                        // 注册服务
public class AiAgentService {                                                   // 对话服务入口

    private final ChatHistoryStore history;                                     // 历史存储
    private final RedisLockHelper redisLockHelper;                              // 锁工具
    private final RedisUtils redisUtils;                                        // Redis 工具
    private final AiChatExecutor chatExecutor;                                  // AI 线程池
    private final AiAgentRunner runner;                                         // Agent 运行器

    private final Map<String, AiChatSseEmitter> runningEmitters = new ConcurrentHashMap<>(); // memoryId → 当前 emitter
    private final Map<String, String> runningRunIds = new ConcurrentHashMap<>();             // memoryId → runId
    private final Map<String, String> runningTokens = new ConcurrentHashMap<>();             // memoryId → 锁 token
    private final Map<String, String> runningRequestIds = new ConcurrentHashMap<>();         // memoryId → 客户端请求 ID

    /**                                                                         // Javadoc
     * 构造注入。                                                                 // 说明
     */                                                                         // 结束
    public AiAgentService(ChatHistoryStore history,                             // 构造器
                          RedisLockHelper redisLockHelper,
                          RedisUtils redisUtils,
                          AiChatExecutor chatExecutor,
                          AiAgentRunner runner) {
        this.history = history;                                                 // 赋值
        this.redisLockHelper = redisLockHelper;                                 // 赋值
        this.redisUtils = redisUtils;                                           // 赋值
        this.chatExecutor = chatExecutor;                                       // 赋值
        this.runner = runner;                                                   // 赋值
    }

    /**                                                                         // Javadoc
     * 对话入口：创建 SSE，注册状态，加锁，提交线程池。                             // 说明
     */                                                                         // 结束
    public SseEmitter chat(ChatRequest request, String userId) {                // 方法
        Locale locale = LocaleContextHolder.getLocale();                        // 1. 取当前语言
        String sessionId = request.sessionId();                                 // 2. 取 sessionId
        String memoryId = userId + ":" + sessionId;                             // 3. 构造 memoryId（用户+会话唯一键）
        String lockKey = "ai:chat:lock:" + memoryId;                            // 4. 构造锁 key
        String token = UUID.randomUUID().toString();                            // 5. 生成本次锁 token
        String newRunId = UUID.randomUUID().toString();                         // 6. 生成本次 runId
        String requestId = (request.clientRequestId() != null && !request.clientRequestId().isBlank()) // 7. 客户端请求 ID
                ? request.clientRequestId()                                     //    有则用
                : UUID.randomUUID().toString();                                 //    无则生成

        AiChatSseEmitter emitter = new AiChatSseEmitter();                      // 8. 创建 SSE 发射器

        try {                                                                   // 9. 主流程
            // 1. 清理旧状态（先取 token、forceUnlock，再关闭旧 emitter，避免 onCompletion 提前清 token） // 注释
            String oldToken = runningTokens.remove(memoryId);                   // 9.1 取旧 token 并移除
            if (oldToken != null) {                                             //    旧 token 存在
                redisLockHelper.forceUnlock(lockKey);                           //    强制释放旧锁
                log.info(">>> 强制释放旧 Redis 锁: lockKey={}, oldToken={}", lockKey, oldToken); // 日志
            }

            AiChatSseEmitter oldEmitter = runningEmitters.remove(memoryId);     // 9.2 取旧 emitter 并移除
            if (oldEmitter != null && !oldEmitter.isClosed()) {                 //    旧 emitter 存在且未关闭
                try {                                                           //    try
                    oldEmitter.send("error", Map.of("message", "会话已被新请求替换")); //    发送替换通知
                    oldEmitter.complete();                                      //    关闭
                    log.info(">>> 已关闭旧 emitter: memoryId={}", memoryId);      //    日志
                } catch (Exception e) {                                         //    异常
                    log.warn("关闭旧 emitter 异常: memoryId={}", memoryId, e);    //    日志
                }
            }

            // 2. 注册当前请求状态                                             // 注释
            runningEmitters.put(memoryId, emitter);                             // 9.3 注册 emitter
            runningTokens.put(memoryId, token);                                 //    注册 token
            runningRunIds.put(memoryId, newRunId);                              //    注册 runId
            runningRequestIds.put(memoryId, requestId);                         //    注册 requestId

            // 3. 获取锁                                                       // 注释
            boolean locked = redisLockHelper.tryLock(lockKey, token, 5_000L);   // 9.4 尝试加锁（5s TTL）
            if (!locked) {                                                      //    加锁失败
                log.error(">>> 锁冲突，关闭新请求: memoryId={}, oldToken={}", memoryId, oldToken); // 日志
                runningEmitters.remove(memoryId, emitter);                      //    清理状态
                runningTokens.remove(memoryId, token);                          //    清理
                runningRunIds.remove(memoryId, newRunId);                       //    清理
                runningRequestIds.remove(memoryId, requestId);                  //    清理
                try {                                                           //    try
                    emitter.send(SseEmitter.event().name("error")               //    发送 error
                            .data(Map.of("message", "上一次请求尚未完成，请稍后再试"))); //    消息
                } catch (Exception ignored) {}                                  //    忽略
                emitter.complete();                                             //    关闭
                return emitter;                                                 //    返回
            }
            log.info(">>> 成功获取锁: memoryId={}, requestId={}", memoryId, requestId); // 9.5 日志

            // 4. 会话初始化                                                   // 注释
            boolean newSession = !history.exists(sessionId);                    // 9.6 判断是否新会话
            if (!newSession) history.assertOwner(sessionId, userId);            //    非新会话校验 owner

            // ✅ 注意：AiToolContext.capture 需要 3 个参数（userId, roles, redisUtils） // 注释
            // 如果你没有 RoleUtils 字段，请检查 AiToolContext 的签名                  // 注释
            AiToolContext context = AiToolContext.capture(userId, redisUtils);  // 9.7 构造工具上下文
            long userMessageId = history.nextMessageId();                       // 9.8 分配 user 消息 ID
            long assistantMessageId = history.nextMessageId();                  //    分配 assistant 消息 ID

            // 5. 注册生命周期回调                                              // 注释
            emitter.onCompletion(() -> {                                        // 9.9 完成回调
                runningEmitters.remove(memoryId, emitter);                      //    清理 emitter
                runningRunIds.remove(memoryId, newRunId);                       //    清理 runId
                runningTokens.remove(memoryId, token);                          //    清理 token
                runningRequestIds.remove(memoryId, requestId);                  //    清理 requestId
            });
            emitter.onTimeout(() -> {                                           // 9.10 超时回调
                try { emitter.complete(); } catch (Exception ignored) {}        //    关闭
                runningRequestIds.remove(memoryId);                             //    清理 requestId
                redisLockHelper.unlock(lockKey, token);                         //    释放锁
            });

            // 6. 提交任务                                                     // 注释
            chatExecutor.execute(() -> runner.run(                              // 9.11 提交线程池
                    request, context, userId, newSession, sessionId,            //    参数
                    userMessageId, assistantMessageId, locale,                  //    参数
                    memoryId, lockKey, token, emitter, newRunId));              //    参数

            return emitter;                                                     // 9.12 返回 emitter

        } catch (Exception e) {                                                 // 10. 入口异常
            log.error("chat() 入口异常", e);                                     //    日志
            try {                                                               //    try
                emitter.send(SseEmitter.event().name("error")                   //    发送 error
                        .data(Map.of("message", "系统繁忙，请稍后再试")));         //    消息
            } catch (Exception ignored) {}                                      //    忽略
            emitter.complete();                                                 //    关闭
            return emitter;                                                     //    返回
        }
    }

    /**                                                                         // Javadoc
     * 停止指定会话。                                                             // 说明
     */                                                                         // 结束
    public void stop(String sessionId, String userId, String clientRequestId) { // 方法
        if (sessionId == null || sessionId.isBlank() || userId == null) return; // 1. 参数校验
        String memoryId = userId + ":" + sessionId;                             // 2. 构造 memoryId
        log.info(">>> 停止会话: memoryId={}, clientRequestId={}", memoryId, clientRequestId); // 3. 日志

        String storedRequestId = runningRequestIds.get(memoryId);               // 4. 取存储的 requestId
        if (clientRequestId != null && !clientRequestId.isBlank()) {            // 5. 若客户端传了 requestId
            boolean requestIdMatches = clientRequestId.equals(storedRequestId); //    比对
            if (!requestIdMatches) {                                            //    不匹配
                log.info("stop() clientRequestId 不匹配，跳过关闭: stored={}, requested={}", // 日志
                        storedRequestId, clientRequestId);
                return;                                                         //    直接返回
            }
        }

        AiChatSseEmitter emitter = runningEmitters.remove(memoryId);            // 6. 取 emitter 并移除
        if (emitter != null) {                                                  //    存在
            try {                                                               //    try
                if (!emitter.isClosed()) {                                      //    未关闭
                    emitter.send("error", Map.of("message", "会话已被新的请求替换")); //    发送通知
                    emitter.complete();                                         //    关闭
                    log.info(">>> 已关闭 emitter: memoryId={}", memoryId);        //    日志
                }
            } catch (Exception e) {                                             //    异常
                log.warn("关闭 emitter 异常: memoryId={}", memoryId, e);          //    日志
            }
        } else {                                                                //    不存在
            log.info("stop() emitter 已不存在, memoryId={}", memoryId);          //    日志
        }
        runningRequestIds.remove(memoryId);                                     // 7. 清理 requestId

        String token = runningTokens.remove(memoryId);                          // 8. 取 token 并移除
        if (token != null) {                                                    //    存在
            String lockKey = "ai:chat:lock:" + memoryId;                        //    构造锁 key
            try {                                                               //    try
                redisLockHelper.unlock(lockKey, token);                         //    释放锁
                log.info("<<< 释放 Redis 锁: lockKey={}", lockKey);              //    日志
            } catch (Exception e) {                                             //    异常
                log.error("释放 Redis 锁失败: {}", lockKey, e);                   //    日志
            }
        }

        runningRunIds.remove(memoryId);                                         // 9. 清理 runId
    }
}