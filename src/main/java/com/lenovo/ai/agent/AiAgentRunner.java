package com.lenovo.ai.agent;

import com.lenovo.ai.agent.blackboard.Blackboard;
import com.lenovo.ai.agent.blackboard.BlackboardFactory;
import com.lenovo.ai.agent.core.Agent;
import com.lenovo.ai.agent.loop.AgentContext;
import com.lenovo.ai.agent.orchestrate.Orchestrator;
import com.lenovo.ai.agent.router.AgentRouter;
import com.lenovo.ai.agent.router.RoutingRequest;
import com.lenovo.ai.infra.history.ChatHistoryStore;
import com.lenovo.ai.infra.lock.RedisLockHelper;
import com.lenovo.ai.infra.sse.AiChatSseEmitter;
import com.lenovo.ai.llm.Message;
import com.lenovo.ai.memory.ContextBuilder;
import com.lenovo.ai.memory.MemoryExtractor;
import com.lenovo.ai.memory.MemoryItem;
import com.lenovo.ai.memory.UserMemoryStore;              // ★ 换成 UserMemoryStore
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

    private final ContextBuilder contextBuilder;
    private final UserMemoryStore userMemoryStore;       // ★ 替换 MemoryStore
    private final MemoryExtractor memoryExtractor;
    private final ChatHistoryStore history;
    private final RedisLockHelper redisLockHelper;

    private final AgentRouter agentRouter;
    private final BlackboardFactory blackboardFactory;
    private final Orchestrator orchestrator;

    public void run(ChatRequest request, AiToolContext toolContext, String userId,
                    boolean newSession, String sessionId,
                    long userMessageId, long assistantMessageId,
                    Locale locale, String memoryId, String lockKey,
                    String token, AiChatSseEmitter emitter, String runId) {
        try {
            log.info(">>> 本轮用户输入原文: [{}], userId={}, sessionId={}, runId={}",
                    request.message(), userId, sessionId, runId);

            // 0. 下发 meta 帧，前端存 requestId 用于 stop
            try {
                emitter.send("meta", Map.of(
                        "requestId", runId,
                        "sessionId", sessionId));
            } catch (Exception e) {
                log.warn("发送 meta 帧失败: runId={}", runId, e);
            }

            // 1. 落库 user 消息
            history.appendUser(userMessageId, sessionId, userId, request.message());

            // 2. 长期记忆召回 ★ 改这里
            List<MemoryItem> memories = userMemoryStore.recall(userId, request.message(), 5);
            log.info(">>> 记忆召回: userId={}, 召回 {} 条, 内容={}",
                    userId, memories.size(),
                    memories.stream().map(MemoryItem::content).toList());

            String memoryBlock = memories.stream()
                    .map(m -> "- [" + m.type() + "] " + m.content())
                    .collect(Collectors.joining("\n"));

            // 3. 构建上下文
            List<Message> messages = contextBuilder.build(sessionId, request.message(), memoryBlock);

            // 4. 路由
            Agent agent = agentRouter.route(new RoutingRequest(
                    request.message(), userId, sessionId,
                    request.agentName(), toolContext.roleSet()));

            // 5. 运行上下文
            AgentContext ctx = new AgentContext(
                    messages, toolContext, emitter, sessionId,
                    assistantMessageId, runId, memoryId);

            // 6. 黑板
            Blackboard board = blackboardFactory.forRun(runId);
            ctx.setBlackboard(board);

            // 7. 执行
            if ("planner".equals(agent.name())) {
                log.info(">>> 进入多 Agent 编排, runId={}", runId);
                orchestrator.execute(ctx);
            } else {
                log.info(">>> 单 Agent 执行: agent={}, runId={}", agent.name(), runId);
                agent.execute(ctx);
            }

            // 8. 异步抽取记忆
            List<Message> turn = List.of(
                    Message.user(request.message()),
                    Message.assistant(history.loadAssistant(assistantMessageId))
            );
            log.info(">>> 准备抽取记忆: userId={}, runId={}", userId, runId);
            memoryExtractor.extract(userId, turn);
            log.info(">>> 记忆抽取已异步提交：userId={}, sessionId={}, runId={}",
                    userId, sessionId, runId);

        } catch (Exception e) {
            log.error("AiAgentRunner 异常，runId={}", runId, e);
        } finally {
            redisLockHelper.unlock(lockKey, token);
        }
    }
}