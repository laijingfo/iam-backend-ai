package com.lenovo.ai.agent.loop;

import com.lenovo.ai.infra.history.ChatHistoryStore;
import com.lenovo.ai.infra.sse.AiChatSseEmitter;
import com.lenovo.ai.llm.LlmClient;
import com.lenovo.ai.llm.LlmResponse;
import com.lenovo.ai.llm.Message;
import com.lenovo.ai.token.QuotaExceededException;
import com.lenovo.ai.token.QuotaStatus;
import com.lenovo.ai.token.TokenCounter;
import com.lenovo.ai.token.TokenQuotaService;
import com.lenovo.ai.token.TokenUsage;
import com.lenovo.ai.tools.AiTool;
import com.lenovo.ai.tools.ToolRegistry;
import com.lenovo.ai.tools.ToolResult;
import com.lenovo.ai.tools.ToolSpec;
import com.lenovo.ai.tools.context.AiToolContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

@Slf4j
@Component
@RequiredArgsConstructor
public class AgentLoop {

    private static final int MAX_STEPS = 8;
    private static final int TOOL_TIMEOUT_SECONDS = 30;

    private final LlmClient llmClient;
    private final ToolRegistry toolRegistry;
    private final ChatHistoryStore history;
    private final PendingStateStore pendingStateStore;

    // ★ 新增
    private final TokenCounter tokenCounter;
    private final TokenQuotaService quotaService;

    public void run(AgentContext ctx) {
        try {
            List<Message> messages = new ArrayList<>(ctx.messages());

            List<ToolSpec> tools = toolRegistry.availableTools(
                    ctx.toolContext(), ctx.allowedToolNames());

            String modelName = ctx.getModelName() != null
                    ? ctx.getModelName() : "kimi-k2.6";
            String userId = ctx.toolContext().getUserId();

            log.info(">>> AgentLoop 启动, runId={}, agent={}, 可用工具 {} 个, model={}",
                    ctx.runId(), ctx.agentName(), tools.size(), modelName);

            int maxSteps = ctx.maxSteps() > 0 ? ctx.maxSteps() : MAX_STEPS;

            for (int step = 0; step < maxSteps; step++) {
                if (ctx.emitter().isClosed()) {
                    log.info(">>> AgentLoop 检测到 SSE 已关闭, runId={}, step={}",
                            ctx.runId(), step);
                    return;
                }
                log.info(">>> AgentLoop step={}, runId={}, agent={}",
                        step, ctx.runId(), ctx.agentName());

                // ==================================================
                // 6. LLM 调用
                // ==================================================
                LlmResponse resp = llmClient.chatStream(messages, tools, ctx.emitter());

                // ==================================================
                // ★★★ 改动 1：记录 token ★★★
                // ==================================================
                try {
                    TokenUsage usage = TokenUsage.fromLlmResponse(resp);
                    tokenCounter.record(userId, ctx.sessionId(), ctx.runId(),
                            modelName, usage);
                    log.debug(">>> token 已记录: runId={}, step={}, in={}, out={}",
                            ctx.runId(), step, usage.inputTokens(), usage.outputTokens());
                } catch (Exception e) {
                    log.warn(">>> token 记录失败: runId={}, step={}", ctx.runId(), step, e);
                    // 不影响主流程
                }

                // ==================================================
                // ★★★ 改动 2：二次校验配额 ★★★
                // ==================================================
                try {
                    QuotaStatus status = quotaService.check(userId);
                    if (status.exceeded()) {
                        log.warn("[quota] 会话中途超限: userId={}, runId={}, reason={}",
                                userId, ctx.runId(), status.reason());
                        throw new QuotaExceededException(userId, status.reason());
                    }
                } catch (QuotaExceededException qe) {
                    // 配额超限 → 发送专门的错误事件并结束
                    sendEvent(ctx.emitter(), "error", Map.of(
                            "code", "QUOTA_EXCEEDED",
                            "message", qe.getReason()));
                    history.appendAssistant(ctx.assistantMessageId(), ctx.sessionId(),
                            "[已超出 token 配额，对话终止]");
                    ctx.emitter().complete();
                    return;
                } catch (Exception e) {
                    log.warn(">>> 配额检查失败，忽略继续: runId={}", ctx.runId(), e);
                }

                // ==================================================
                // 7. 无工具调用 → 最终答案
                // ==================================================
                if (!resp.hasToolCalls()) {
                    history.appendAssistant(ctx.assistantMessageId(), ctx.sessionId(), resp.content());
                    sendEvent(ctx.emitter(), "done", Map.of("messageId", ctx.assistantMessageId()));
                    ctx.emitter().complete();
                    return;
                }

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

                    if (!toolRegistry.isAllowed(call.name(), ctx.toolContext())) {
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

                    sendEvent(ctx.emitter(), "tool", Map.of(
                            "name", call.name(),
                            "args", call.args(),
                            "status", result.success() ? "completed" : "failed",
                            "result", result.content()));

                    messages.add(Message.toolResult(call.id(), result.content()));
                }
            }

            history.appendAssistant(ctx.assistantMessageId(), ctx.sessionId(),
                    "[已达到最大工具调用步数]");
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

    private void replaceMessages(List<Message> target, List<Message> source) {
        target.clear();
        target.addAll(source);
    }

    private ToolResult executeWithGuard(AiTool tool, LlmResponse.ToolCall call, AgentContext ctx) {
        try {
            return CompletableFuture.supplyAsync(() -> {
                        AiToolContext.set(ctx.toolContext());
                        try {
                            return tool.execute(call.args(), ctx.toolContext());
                        } finally {
                            AiToolContext.clear();
                        }
                    })
                    .orTimeout(TOOL_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                    .exceptionally(e -> {
                        log.error("工具执行异常: {}", tool.name(), e);
                        return ToolResult.fail("工具执行失败: " + e.getMessage());
                    })
                    .join();
        } catch (Exception e) {
            log.error("工具提交失败: {}", tool.name(), e);
            return ToolResult.fail("工具执行失败: " + e.getMessage());
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