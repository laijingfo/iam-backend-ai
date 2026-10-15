package com.lenovo.ai.agent.loop;

import com.lenovo.ai.infra.history.ChatHistoryStore;
import com.lenovo.ai.infra.sse.AiChatSseEmitter;
import com.lenovo.ai.llm.LlmClient;
import com.lenovo.ai.llm.LlmResponse;
import com.lenovo.ai.llm.Message;
import com.lenovo.ai.token.counter.TokenCounter;
import com.lenovo.ai.token.model.QuotaStatus;
import com.lenovo.ai.token.model.TokenUsage;
import com.lenovo.ai.token.quota.TokenQuotaService;
import com.lenovo.ai.tools.AiTool;
import com.lenovo.ai.tools.ToolRegistry;
import com.lenovo.ai.tools.ToolResult;
import com.lenovo.ai.tools.ToolSpec;
import com.lenovo.ai.tools.context.AiToolContext;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Agent 执行主循环：LLM ↔ 工具 的多轮交互。
 *
 * <p><b>关键约定</b>：</p>
 * <ol>
 *   <li>循环操作的是 {@code messages} 本地副本，<b>每一次终止都必须回写 ctx</b>，
 *       否则上层（Orchestrator / AgentContext.lastAssistantContent）读取结果恒为空。</li>
 *   <li>落库以 {@link AgentContext#shouldPersist()} 为前提：内部 Agent 不落库、
 *       子任务（哨兵 messageId）不落库。</li>
 *   <li>LLM 失败（{@link LlmResponse#isFailed()}）不再静默成空答案，
 *       走 error 帧 + 关闭，不落库、不发 done。</li>
 *   <li>终结帧由可见方统一发出：子视图 emitter 会吞掉 done / complete。</li>
 * </ol>
 */
@Slf4j
@Component
public class AgentLoop {

    private static final int MAX_STEPS = 8;
    private static final int TOOL_TIMEOUT_SECONDS = 30;
    private static final int TOOL_RESULT_MAX_CHARS = 8000;

    private final LlmClient llmClient;
    private final ToolRegistry toolRegistry;
    private final ChatHistoryStore history;
    private final PendingStateStore pendingStateStore;
    private final TokenCounter tokenCounter;
    private final TokenQuotaService quotaService;

    /**
     * 工具执行线程池。
     *
     * <p><b>必须是独立的 aiToolExecutor</b>，绝不能复用 AiChatExecutor：
     * AgentLoop 就跑在 ai-chat-N 线程上，若工具也提交到该池，
     * 会出现"执行者阻塞等待被排队任务"的自死锁（corePoolSize=0 不会扩容），
     * 表现为工具固定 30s 超时。</p>
     */
    private final Executor toolExecutor;

    public AgentLoop(LlmClient llmClient,
                     ToolRegistry toolRegistry,
                     ChatHistoryStore history,
                     PendingStateStore pendingStateStore,
                     TokenCounter tokenCounter,
                     TokenQuotaService quotaService,
                     @Qualifier("aiToolExecutor") Executor toolExecutor) {
        this.llmClient = llmClient;
        this.toolRegistry = toolRegistry;
        this.history = history;
        this.pendingStateStore = pendingStateStore;
        this.tokenCounter = tokenCounter;
        this.quotaService = quotaService;
        this.toolExecutor = toolExecutor;
    }

    public void run(AgentContext ctx) {
        try {
            List<Message> messages = new ArrayList<>(ctx.messages());
            List<ToolSpec> tools = toolRegistry.availableTools(
                    ctx.toolContext(), ctx.allowedToolNames());

            // ★ 不再硬编码兜底模型名："kimi-k2.6" 在当前网关并不存在。
            //   为 null 时交给 LlmClient 用 Bean 默认模型（已验证可用）兜底。
            String modelName = ctx.getModelName();
            String userId = ctx.toolContext().getUserId();

            log.info(">>> AgentLoop 启动, runId={}, agent={}, 可用工具 {} 个, model={}, stream={}",
                    ctx.runId(), ctx.agentName(), tools.size(), modelName, ctx.streamToUser());

            int maxSteps = ctx.maxSteps() > 0 ? ctx.maxSteps() : MAX_STEPS;

            for (int step = 0; step < maxSteps; step++) {
                if (ctx.emitter().isClosed()) {
                    log.info(">>> AgentLoop 检测到 SSE 已关闭, runId={}, step={}",
                            ctx.runId(), step);
                    return;
                }

                // ==================================================
                // ★ 配额检查前置
                // ==================================================
                try {
                    QuotaStatus status = quotaService.check(userId);
                    if (status.exceeded()) {
                        log.warn("[quota] 调用前超限: userId={}, runId={}, reason={}",
                                userId, ctx.runId(), status.reason());
                        sendEvent(ctx.emitter(), "error", Map.of(
                                "code", "QUOTA_EXCEEDED",
                                "message", status.reason()));
                        // ★ 只有对用户可见、且需要落库的 ctx 才写库
                        persist(ctx, "[已超出 token 配额，对话终止]");
                        ctx.emitter().complete();
                        return;
                    }
                } catch (Exception e) {
                    log.warn(">>> 配额检查失败，忽略继续: runId={}", ctx.runId(), e);
                }

                log.info(">>> AgentLoop step={}, runId={}, agent={}",
                        step, ctx.runId(), ctx.agentName());

                // ==================================================
                // LLM 调用
                // ==================================================
                // ★ 修复：透传 modelName，否则 ModelRouter 的选择不生效
                LlmResponse resp = llmClient.chatStream(messages, tools, ctx.emitter(), modelName);

                // 记录 token（不阻断）
                try {
                    TokenUsage usage = TokenUsage.fromLlmResponse(resp);
                    tokenCounter.record(userId, ctx.sessionId(), ctx.runId(),
                            modelName, usage);
                } catch (Exception e) {
                    log.warn(">>> token 记录失败: runId={}, step={}", ctx.runId(), step, e);
                }

                // ==================================================
                // ★ LLM 调用失败：不再静默成空答案
                // ==================================================
                if (resp.isFailed()) {
                    Throwable cause = resp.error();
                    log.error(">>> LLM 调用失败, runId={}, agent={}, step={}",
                            ctx.runId(), ctx.agentName(), step, cause);
                    sendEvent(ctx.emitter(), "error", Map.of(
                            "code", "LLM_ERROR",
                            "message", "模型调用失败: "
                                    + (cause == null ? "未知原因" : cause.getMessage())));
                    ctx.emitter().complete();
                    return;
                }

                // ==================================================
                // 无工具调用 → 最终答案
                // ==================================================
                if (!resp.hasToolCalls()) {
                    // ★ 修复：必须回写 ctx，否则上层 lastAssistantContent() 恒为空
                    messages.add(Message.assistant(resp.content()));
                    replaceMessages(ctx.messages(), messages);

                    persist(ctx, resp.content());
                    sendEvent(ctx.emitter(), "done",
                            Map.of("messageId", ctx.assistantMessageId()));
                    ctx.emitter().complete();
                    return;
                }

                // ==================================================
                // 有工具调用
                // ==================================================
                List<Message.ToolCallRef> refs = resp.toolCalls().stream()
                        .map(tc -> new Message.ToolCallRef(tc.id(), tc.name(), tc.args()))
                        .toList();
                messages.add(Message.assistantWithTools(resp.content(), refs));

                for (LlmResponse.ToolCall call : resp.toolCalls()) {
                    AiTool tool = toolRegistry.get(call.name());
                    if (tool == null) {
                        messages.add(Message.toolResult(call.id(), "工具不存在: " + call.name()));
                        continue;
                    }

                    // ★ 双重校验：角色 + Agent 白名单
                    if (!toolRegistry.isAllowed(call.name(), ctx.toolContext(),
                            ctx.allowedToolNames())) {
                        messages.add(Message.toolResult(call.id(), "无权限调用该工具"));
                        sendEvent(ctx.emitter(), "tool", Map.of(
                                "name", call.name(),
                                "status", "denied"));
                        continue;
                    }

                    if (tool.requiresConfirmation() && !ctx.confirmed(call.id())) {
                        replaceMessages(ctx.messages(), messages);
                        pendingStateStore.save(ctx);
                        sendEvent(ctx.emitter(), "tool_confirm", Map.of(
                                "callId", call.id(),
                                "tool", call.name(),
                                "args", call.args()));
                        ctx.emitter().complete();
                        return;
                    }

                    sendEvent(ctx.emitter(), "tool", Map.of(
                            "name", call.name(),
                            "args", call.args(),
                            "status", "executing"));

                    ToolResult result = executeWithGuard(tool, call, ctx);
                    String truncated = truncateToolResult(result.content(), TOOL_RESULT_MAX_CHARS);

                    sendEvent(ctx.emitter(), "tool", Map.of(
                            "name", call.name(),
                            "args", call.args(),
                            "status", result.success() ? "completed" : "failed",
                            "result", truncated));

                    messages.add(Message.toolResult(call.id(), truncated));
                }
            }

            // ==================================================
            // 达到最大步数
            // ==================================================
            replaceMessages(ctx.messages(), messages);
            persist(ctx, "[已达到最大工具调用步数]");
            sendEvent(ctx.emitter(), "done", Map.of("messageId", ctx.assistantMessageId()));
            ctx.emitter().complete();

        } catch (Exception e) {
            log.error("AgentLoop 异常, runId={}, agent={}", ctx.runId(), ctx.agentName(), e);
            try {
                sendEvent(ctx.emitter(), "error", Map.of(
                        "code", "INTERNAL_ERROR",
                        "message", "处理失败: " + e.getMessage()));
            } catch (Exception ignored) {}
            try {
                ctx.emitter().complete();
            } catch (Exception ignored) {}
        }
    }

    /**
     * 落库：仅当本 ctx 可见且需要持久化。
     * <p>子任务使用哨兵 messageId，不会写入，避免污染主消息。</p>
     */
    private void persist(AgentContext ctx, String content) {
        if (!ctx.streamToUser() || !ctx.shouldPersist()) return;
        try {
            history.appendAssistant(ctx.assistantMessageId(), ctx.sessionId(), content);
        } catch (Exception e) {
            log.warn(">>> 落库失败: runId={}, messageId={}", ctx.runId(),
                    ctx.assistantMessageId(), e);
        }
    }

    private String truncateToolResult(String content, int maxLen) {
        if (content == null) return "";
        if (content.length() <= maxLen) return content;
        log.warn(">>> 工具结果超长截断: {} -> {}", content.length(), maxLen);
        return content.substring(0, maxLen) + "\n...(结果过长已截断)";
    }

    private void replaceMessages(List<Message> target, List<Message> source) {
        target.clear();
        target.addAll(source);
    }

    private ToolResult executeWithGuard(AiTool tool, LlmResponse.ToolCall call, AgentContext ctx) {
        // ★ 修复：工具是阻塞 IO，原来跑在 ForkJoinPool.commonPool 会污染公共池。
        //   优先使用 AI 专用线程池，未装配时回退默认池（行为不变）。
        java.util.function.Supplier<ToolResult> task = () -> {
            AiToolContext.set(ctx.toolContext());
            try {
                return tool.execute(call.args(), ctx.toolContext());
            } finally {
                AiToolContext.clear();
            }
        };

        CompletableFuture<ToolResult> future;
        try {
            future = CompletableFuture.supplyAsync(task, toolExecutor);
        } catch (Exception e) {
            log.error("工具提交到线程池失败: {}", tool.name(), e);
            return ToolResult.fail("工具执行失败（线程池已满）: "
                    + (e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage()));
        }

        long start = System.currentTimeMillis();
        try {
            return future.orTimeout(TOOL_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                    .exceptionally(e -> {
                        Throwable cause = (e instanceof java.util.concurrent.CompletionException
                                && e.getCause() != null) ? e.getCause() : e;
                        // ★ 超时单独给出可读信息（TimeoutException.getMessage() 恒为 null）
                        if (cause instanceof TimeoutException) {
                            log.error("工具执行超时: tool={}, 预算={}s —— 请检查工具是否阻塞、"
                                            + "以及是否误用了 AiChatExecutor 导致自死锁",
                                    tool.name(), TOOL_TIMEOUT_SECONDS);
                            return ToolResult.fail("工具执行超时（" + TOOL_TIMEOUT_SECONDS + "秒）");
                        }
                        log.error("工具执行异常: tool={}", tool.name(), cause);
                        return ToolResult.fail("工具执行失败: "
                                + (cause.getMessage() == null
                                ? cause.getClass().getSimpleName() : cause.getMessage()));
                    })
                    .join();
        } catch (Exception e) {
            log.error("工具等待异常: tool={}, 耗时={}ms", tool.name(),
                    System.currentTimeMillis() - start, e);
            return ToolResult.fail("工具执行失败: "
                    + (e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage()));
        }
    }

    private void sendEvent(AiChatSseEmitter emitter, String name, Map<String, Object> data) {
        String frontendEventName = switch (name) {
            case "tool_call" -> "tool";
            case "tool_confirm" -> "tool_confirm";
            default -> name;
        };
        emitter.send(frontendEventName, data);
    }
}
