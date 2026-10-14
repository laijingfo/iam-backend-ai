package com.lenovo.ai.agent;

import com.lenovo.ai.agent.blackboard.Blackboard;
import com.lenovo.ai.agent.blackboard.BlackboardFactory;
import com.lenovo.ai.agent.core.Agent;
import com.lenovo.ai.agent.loop.AgentContext;
import com.lenovo.ai.agent.orchestrate.Orchestrator;
import com.lenovo.ai.agent.router.AgentRouter;
import com.lenovo.ai.agent.router.ModelRouter;
import com.lenovo.ai.agent.router.RoutingRequest;
import com.lenovo.ai.infra.history.ChatHistoryStore;
import com.lenovo.ai.infra.lock.RedisLockHelper;
import com.lenovo.ai.infra.sse.AiChatSseEmitter;
import com.lenovo.ai.llm.Message;
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

    // ★ 上下文裁剪 + 模型路由
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
            try {
                emitter.send("meta", Map.of(
                        "requestId", runId,
                        "sessionId", sessionId));
            } catch (Exception e) {
                log.warn("发送 meta 帧失败: runId={}", runId, e);
            }

            // 1. 落库 user 消息
            history.appendUser(userMessageId, sessionId, userId, request.message());

            // 2. 长期记忆召回
            List<MemoryItem> memories = userMemoryStore.recall(userId, request.message(), 5);
            log.info(">>> 记忆召回: userId={}, 召回 {} 条, 内容={}",
                    userId, memories.size(),
                    memories.stream().map(MemoryItem::content).toList());

            String memoryBlock = memories.stream()
                    .map(m -> "- [" + m.type() + "] " + m.content())
                    .collect(Collectors.joining("\n"));

            // 3. 构建上下文
            List<Message> rawMessages = contextBuilder.build(
                    sessionId, request.message(), memoryBlock);

            // ============================================================
            // ★★★ 改动 1：上下文裁剪 ★★★
            // ============================================================
            List<Message> messages = trimContext(rawMessages);

            // 4. 路由
            Agent agent = agentRouter.route(new RoutingRequest(
                    request.message(), userId, sessionId,
                    request.agentName(), toolContext.roleSet()));
            log.info(">>> 路由结果: agent={}, 前端指定={}, runId={}",
                    agent.name(), request.agentName(), runId);

            // ============================================================
            // ★★★ 改动 2：模型路由 ★★★
            // ============================================================
            String modelName = modelRouter.select(
                    agent.name(),
                    request.message(),
                    Math.max(0, messages.size() - 1));
            log.info(">>> 模型路由: model={}, agent={}, runId={}",
                    modelName, agent.name(), runId);

            // 5. 运行上下文
            AgentContext ctx = new AgentContext(
                    messages, toolContext, emitter, sessionId,
                    assistantMessageId, runId, memoryId);
            ctx.setAgentName(agent.name());
            ctx.setAllowedToolNames(agent.allowedToolNames());
            ctx.setMaxSteps(agent.maxSteps());
            ctx.setStreamToUser(agent.streamToUser());
            ctx.setModelName(modelName);                     // ★ 新增

            // 6. 黑板
            Blackboard board = blackboardFactory.forRun(runId);
            ctx.setBlackboard(board);

            // 7. 执行
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

    // ================================================================
    // ★ 上下文裁剪
    // 说明：传入 0 表示"使用 TokenProperties.maxPromptTokens 配置值"
    //      由 ContextTrimmer 内部兜底，避免调用方硬编码默认 token 数
    // ================================================================
    private List<Message> trimContext(List<Message> rawMessages) {
        if (rawMessages == null || rawMessages.isEmpty()) {
            return rawMessages;
        }
        try {
            // 拆分 system / 非 system
            List<Message> systems = rawMessages.stream()
                    .filter(this::isSystem)
                    .toList();
            List<Message> others = rawMessages.stream()
                    .filter(m -> !isSystem(m))
                    .collect(Collectors.toList());

            // 找最后一条 user 作为本轮（从末尾向前找）
            Message current = null;
            for (int i = others.size() - 1; i >= 0; i--) {
                if (isUser(others.get(i))) {
                    current = others.remove(i);
                    break;
                }
            }
            if (current == null) {
                // 兜底：无 user 消息，直接返回原始
                return rawMessages;
            }

            // ★ 传 0：由 ContextTrimmer 内部读 TokenProperties.maxPromptTokens 兜底
            return contextTrimmer.trim(systems, others, current, 0);
        } catch (Exception e) {
            log.warn("[context-trim] 裁剪失败，使用原始上下文", e);
            return rawMessages;
        }
    }

    // ================================================================
    // ★ 角色判断辅助方法
    // 适配 Message.role() 的两种可能定义：
    //   - 返回枚举 Role：用 m.role().name()
    //   - 返回 String   ：用 m.role()
    // 下面按"枚举"写法给出，若编译失败请切换到注释里的 String 写法
    // ================================================================
    private boolean isSystem(Message m) {
        return m.role() != null && "system".equalsIgnoreCase(m.role().name());
        // 如果 Message.role() 返回 String：
        // return "system".equalsIgnoreCase(m.role());
    }

    private boolean isUser(Message m) {
        return m.role() != null && "user".equalsIgnoreCase(m.role().name());
        // 如果 Message.role() 返回 String：
        // return "user".equalsIgnoreCase(m.role());
    }
}