package com.lenovo.ai.service;

import com.lenovo.ai.agent.AiAgentRunner;
import com.lenovo.ai.agent.ChatRequest;
import com.lenovo.ai.agent.runtime.AiChatExecutor;
import com.lenovo.ai.infra.history.ChatHistoryStore;
import com.lenovo.ai.infra.lock.RedisLockHelper;
import com.lenovo.ai.infra.sse.AiChatSseEmitter;
import com.lenovo.ai.token.exception.QuotaExceededException;
import com.lenovo.ai.token.quota.TokenQuotaService;
import com.lenovo.ai.tools.context.AiToolContext;
import com.lenovo.util.RedisUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Service
public class AiAgentService {

    private final ChatHistoryStore history;
    private final RedisLockHelper redisLockHelper;
    private final RedisUtils redisUtils;
    private final AiChatExecutor chatExecutor;
    private final AiAgentRunner runner;
    private final TokenQuotaService quotaService;

    private final Map<String, RequestState> requestsById = new ConcurrentHashMap<>();
    private final Map<String, String> activeByMemory = new ConcurrentHashMap<>();
    private final Map<String, RequestState> activeBySession = new ConcurrentHashMap<>();

    public AiAgentService(ChatHistoryStore history,
                          RedisLockHelper redisLockHelper,
                          RedisUtils redisUtils,
                          AiChatExecutor chatExecutor,
                          AiAgentRunner runner,
                          TokenQuotaService quotaService) {
        this.history = history;
        this.redisLockHelper = redisLockHelper;
        this.redisUtils = redisUtils;
        this.chatExecutor = chatExecutor;
        this.runner = runner;
        this.quotaService = quotaService;
    }

    public SseEmitter chat(ChatRequest request, String userId) {

        try {
            quotaService.checkOrThrow(userId);
        } catch (QuotaExceededException e) {
            log.warn("[quota] 用户 {} 配额超限: {}", userId, e.getReason());
            AiChatSseEmitter reject = new AiChatSseEmitter();
            try {
                reject.send("error", Map.of(
                        "code", "QUOTA_EXCEEDED",
                        "message", e.getReason()));
            } catch (Exception ignored) {}
            reject.complete();
            return reject;
        }

        String sessionId = request.sessionId();
        String memoryId = userId + ":" + sessionId;
        String lockKey = "ai:chat:lock:" + memoryId;
        String lockToken = UUID.randomUUID().toString();
        String runId = UUID.randomUUID().toString();
        String requestId = (request.clientRequestId() != null && !request.clientRequestId().isBlank())
                ? request.clientRequestId()
                : UUID.randomUUID().toString();

        AiChatSseEmitter emitter = new AiChatSseEmitter();

        try {
            // 2. 替换同一会话的旧请求
            String oldRequestId = activeByMemory.get(memoryId);
            if (oldRequestId != null && !oldRequestId.equals(requestId)) {
                RequestState oldState = requestsById.remove(oldRequestId);
                if (oldState != null) {
                    if (oldState.sessionId != null) {
                        activeBySession.remove(oldState.sessionId, oldState);
                    }
                    try {
                        // ★ P1 修复：用 unlock(token) 而非 forceUnlock，
                        //   避免误删已被新请求拿到的同名锁
                        redisLockHelper.unlock(oldState.lockKey, oldState.lockToken);
                    } catch (Exception e) {
                        log.warn("释放旧锁失败", e);
                    }
                    if (!oldState.emitter.isClosed()) {
                        try {
                            oldState.emitter.send("error", Map.of("message", "会话已被新请求替换"));
                            oldState.emitter.complete();
                        } catch (Exception e) {
                            log.warn("关闭旧 emitter 异常", e);
                        }
                    }
                    log.info(">>> 已替换旧请求: memoryId={}, old={}, new={}",
                            memoryId, oldRequestId, requestId);
                }
            }

            // 3. 注册请求状态（★ P0-2：activeBySession 挪到锁成功后再注册）
            RequestState state = new RequestState(
                    requestId, runId, memoryId, sessionId, lockToken, lockKey, emitter);
            requestsById.put(requestId, state);
            activeByMemory.put(memoryId, requestId);
            // ★ activeBySession 不在这里 put —— 见下方 tryLock 之后

            // 4. 加锁
            boolean locked = redisLockHelper.tryLock(lockKey, lockToken, 300_000L);
            if (!locked) {
                log.error(">>> 锁冲突: memoryId={}", memoryId);
                // ★ P0-2 修复：锁失败时 activeBySession 尚未写入当前 state，
                //   cleanup 不会误删旧请求的映射。旧请求仍可被 stop(sessionId) 停止。
                cleanup(requestId);
                try {
                    emitter.send(SseEmitter.event().name("error")
                            .data(Map.of("message", "上一次请求尚未完成，请稍后再试")));
                } catch (Exception ignored) {}
                emitter.complete();
                return emitter;
            }
            // ★ P0-2：锁成功后才注册 session 映射
            activeBySession.put(sessionId, state);

            log.info(">>> 成功获取锁: memoryId={}, requestId={}, runId={}",
                    memoryId, requestId, runId);

            // 5. 会话初始化（★ 这段之前丢了，必须补回）
            boolean newSession = !history.exists(sessionId);
            if (!newSession) {
                history.assertOwner(sessionId, userId);
            }

            AiToolContext context = AiToolContext.capture(userId, redisUtils);
            long userMessageId = history.nextMessageId();
            long assistantMessageId = history.nextMessageId();

            // 6. 生命周期回调
            emitter.addCloseCallback(() -> {
                cleanup(requestId);
                log.debug(">>> emitter close: requestId={}", requestId);
            });

            // 7. 提交线程池
            boolean accepted = chatExecutor.submit(() -> runner.run(
                    request, context, userId, sessionId,
                    userMessageId, assistantMessageId,
                    memoryId, lockKey, lockToken, emitter,
                    runId, requestId));

            if (!accepted) {
                log.error(">>> 线程池已满，请求被拒绝: memoryId={}, requestId={}",
                        memoryId, requestId);
                cleanup(requestId);
                try {
                    emitter.send(SseEmitter.event().name("error")
                            .data(Map.of("message", "系统繁忙，请稍后再试")));
                } catch (Exception ignored) {}
                emitter.complete();
                redisLockHelper.unlock(lockKey, lockToken);
            }

            return emitter;

        } catch (Exception e) {
            log.error("chat() 入口异常", e);
            cleanup(requestId);
            try {
                emitter.send(SseEmitter.event().name("error")
                        .data(Map.of("message", "系统繁忙，请稍后再试")));
            } catch (Exception ignored) {}
            emitter.complete();
            return emitter;
        }
    }

    /**
     * 停止对话。
     *
     * <p><b>重要</b>：前端如果在发新请求前先 stop 旧请求，传的是<b>旧 requestId</b>。
     * 此时 {@code requestsById} 里已经找不到（旧请求已结束），
     * <b>不能</b>用 sessionId 兜底去杀当前活跃请求，否则会误杀刚发的新请求。</p>
     *
     * <p>规则：
     * <ul>
     *   <li>传了 {@code clientRequestId} 且找不到 → 忽略（旧请求）</li>
     *   <li>没传 {@code clientRequestId} → 按 {@code sessionId} 兜底（前端主动停止当前会话）</li>
     * </ul>
     */
    public void stop(String sessionId, String userId, String clientRequestId) {
        if (sessionId == null || sessionId.isBlank() || userId == null) return;

        RequestState state = null;

        if (clientRequestId != null && !clientRequestId.isBlank()) {
            // ★ 传了 requestId：只按 requestId 查，找不到就忽略（不兜底）
            state = requestsById.remove(clientRequestId);
            if (state == null) {
                log.info("stop() 未找到 requestId={}，忽略（可能是已结束的旧请求）", clientRequestId);
                return;
            }
        } else {
            // ★ 没传 requestId：按 sessionId 兜底（前端主动停止）
            state = activeBySession.remove(sessionId);
        }

        if (state == null) {
            log.info("stop() 未找到 emitter: sessionId={}", sessionId);
            return;
        }

        log.info(">>> 停止会话: memoryId={}, requestId={}, runId={}",
                state.memoryId, state.requestId, state.runId);

        // 清理其它 map（用 remove(key, value) 避免误删新请求）
        requestsById.remove(state.requestId, state);
        activeByMemory.remove(state.memoryId, state.requestId);
        activeBySession.remove(sessionId, state);

        try {
            if (!state.emitter.isClosed()) {
                state.emitter.send("error", Map.of("message", "会话已被手动停止"));
                state.emitter.complete();
            }
        } catch (Exception e) {
            log.warn("关闭 emitter 异常", e);
        }

        try {
            redisLockHelper.unlock(state.lockKey, state.lockToken);
        } catch (Exception e) {
            log.error("释放 Redis 锁失败", e);
        }
    }

    /**
     * 清理请求状态。
     *
     * <p>用 {@code remove(key, value)} 语义：只有当 map 里的值还是当前 state 时才移除，
     * 避免把已经被新请求覆盖的条目误删。</p>
     *
     * @param requestId 请求 ID
     */
    private void cleanup(String requestId) {
        RequestState state = requestsById.remove(requestId);
        if (state != null) {
            activeByMemory.remove(state.memoryId, state.requestId);
            if (state.sessionId != null) {
                activeBySession.remove(state.sessionId, state);   // ← 用 state.sessionId
            }
        }
    }

}