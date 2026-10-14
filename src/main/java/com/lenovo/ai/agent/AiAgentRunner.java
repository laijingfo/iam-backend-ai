package com.lenovo.ai.agent;

import com.lenovo.ai.agent.blackboard.Blackboard;
import com.lenovo.ai.agent.blackboard.BlackboardFactory;
import com.lenovo.ai.agent.core.Agent;
import com.lenovo.ai.agent.loop.AgentContext;
import com.lenovo.ai.agent.Orchestrator;
import com.lenovo.ai.agent.router.AgentRouter;
import com.lenovo.ai.agent.router.ModelRouter;
import com.lenovo.ai.agent.router.RoutingRequest;
import com.lenovo.ai.infra.history.ChatHistoryStore;
import com.lenovo.ai.infra.lock.RedisLockHelper;
import com.lenovo.ai.infra.sse.AiChatSseEmitter;
import com.lenovo.ai.llm.Message;
import com.lenovo.ai.llm.enums.MessageRole;
import com.lenovo.ai.memory.context.ContextBuilder;
import com.lenovo.ai.memory.extractor.MemoryExtractor;
import com.lenovo.ai.memory.model.MemoryItem;
import com.lenovo.ai.memory.store.UserMemoryStore;
import com.lenovo.ai.token.context.ContextTrimmer;
import com.lenovo.ai.tools.context.AiToolContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Component
@RequiredArgsConstructor
public class AiAgentRunner {

    private static final String MULTI_AGENT = "planner";

    private final ContextBuilder contextBuilder;
    private final UserMemoryStore userMemoryStore;
    private final MemoryExtractor memoryExtractor;
    private final ChatHistoryStore history;
    private final RedisLockHelper redisLockHelper;

    private final AgentRouter agentRouter;
    private final BlackboardFactory blackboardFactory;
    private final Orchestrator orchestrator;

    private final ContextTrimmer contextTrimmer;
    private final ModelRouter modelRouter;

    public void run(ChatRequest request, AiToolContext toolContext, String userId,
                    boolean newSession, String sessionId,
                    long userMessageId, long assistantMessageId,
                    Locale locale, String memoryId, String lockKey,
                    String token, AiChatSseEmitter emitter, String runId) {
        try {
            log.info(">>> 本轮用户输入原文: [{}], userId={}, sessionId={}, runId={}",
                    request.message(), userId, sessionId, runId);

            // 0. 下发 meta 帧
            safeSend(emitter, "meta", Map.of(
                    "requestId", runId,
                    "sessionId", sessionId));

            // 1. 落库 user 消息
            history.appendUser(userMessageId, sessionId, userId, request.message());

            // 2. 长期记忆召回
            List<MemoryItem> memories = userMemoryStore.recall(userId, request.message(), 5);
            log.info(">>> 记忆召回: userId={}, 召回 {} 条", userId, memories.size());

            String memoryBlock = memories.stream()
                    .map(m -> "- [" + m.type() + "] " + m.content())
                    .collect(Collectors.joining("\n"));

            // 3. 构建上下文
            List<Message> rawMessages = contextBuilder.build(
                    sessionId, request.message(), memoryBlock);
            List<Message> messages = trimContext(rawMessages);

            // 4. 路由
            Agent agent = agentRouter.route(new RoutingRequest(
                    request.message(), userId, sessionId,
                    request.agentName(), toolContext.roleSet()));
            log.info(">>> 路由结果: agent={}, runId={}", agent.name(), runId);

            // 5. 模型路由
            String modelName = modelRouter.select(
                    agent.name(), request.message(), Math.max(0, messages.size() - 1));
            log.info(">>> 模型路由: model={}, agent={}, runId={}",
                    modelName, agent.name(), runId);

            // 6. 运行上下文
            AgentContext ctx = new AgentContext(
                    messages, toolContext, emitter, sessionId,
                    assistantMessageId, runId, memoryId);
            ctx.setAgentName(agent.name());
            ctx.setAllowedToolNames(agent.allowedToolNames());
            ctx.setMaxSteps(agent.maxSteps());
            ctx.setStreamToUser(agent.streamToUser());
            ctx.setModelName(modelName);

            // 7. 黑板
            Blackboard board = blackboardFactory.forRun(runId);
            ctx.setBlackboard(board);

            // 8. 执行
            String requested = request.agentName();
            boolean isMulti = MULTI_AGENT.equalsIgnoreCase(
                    requested == null ? "" : requested.trim());

            if (isMulti || MULTI_AGENT.equals(agent.name())) {
                log.info(">>> 进入多 Agent 编排, runId={}", runId);
                orchestrator.execute(ctx);
            } else {
                log.info(">>> 单 Agent 执行: agent={}, model={}, runId={}",
                        agent.name(), modelName, runId);
                agent.execute(ctx);
            }

            // 9. 异步抽取记忆（加空值校验）
            String assistantContent = history.loadAssistant(assistantMessageId);
            if (assistantContent == null || assistantContent.isBlank()) {
                log.warn(">>> 助手消息为空，跳过记忆抽取: runId={}", runId);
            } else {
                List<Message> turn = List.of(
                        Message.user(request.message()),
                        Message.assistant(assistantContent)
                );
                memoryExtractor.extract(userId, turn);
            }

        } catch (Exception e) {
            // ============================================================
            // ★ 优化 3：异常兜底
            // ============================================================
            log.error("AiAgentRunner 异常，runId={}", runId, e);
            safeSend(emitter, "error", Map.of(
                    "code", "INTERNAL_ERROR",
                    "message", e.getMessage() == null ? "处理失败" : e.getMessage()));
        } finally {
            // ★ 兜底关闭 emitter
            try {
                emitter.unmute();
                emitter.setSuppressComplete(false);
                emitter.complete();
            } catch (Exception ignored) {}

            // ★ 释放锁
            try {
                redisLockHelper.unlock(lockKey, token);
            } catch (Exception e) {
                log.warn("解锁失败: lockKey={}", lockKey, e);
            }
        }
    }

    /** 安全发送事件（不抛异常） */
    private void safeSend(AiChatSseEmitter emitter, String event, Object data) {
        try {
            emitter.unmute();
            emitter.setSuppressComplete(false);
            emitter.send(event, data);
        } catch (Exception e) {
            log.warn("发送 {} 帧失败", event, e);
        }
    }

    private List<Message> trimContext(List<Message> rawMessages) {
        if (rawMessages == null || rawMessages.isEmpty()) {
            return rawMessages;
        }
        try {
            List<Message> systems = rawMessages.stream()
                    .filter(m -> m.role() == MessageRole.SYSTEM)   // ★ 枚举比较
                    .toList();
            List<Message> others = rawMessages.stream()
                    .filter(m -> m.role() != MessageRole.SYSTEM)
                    .collect(Collectors.toList());

            Message current = null;
            for (int i = others.size() - 1; i >= 0; i--) {
                if (others.get(i).role() == MessageRole.USER) {    // ★ 枚举比较
                    current = others.remove(i);
                    break;
                }
            }
            if (current == null) {
                return rawMessages;
            }
            return contextTrimmer.trim(systems, others, current, 0);
        } catch (Exception e) {
            log.warn("[context-trim] 裁剪失败，使用原始上下文", e);
            return rawMessages;
        }
    }
}