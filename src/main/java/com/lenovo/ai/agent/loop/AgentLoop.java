package com.lenovo.ai.agent.loop;

import com.lenovo.ai.infra.history.ChatHistoryStore;
import com.lenovo.ai.infra.sse.AiChatSseEmitter;
import com.lenovo.ai.llm.LlmClient;
import com.lenovo.ai.llm.LlmResponse;
import com.lenovo.ai.llm.Message;
import com.lenovo.ai.memory.MemoryStore;
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
    private final MemoryStore memoryStore;
    public void run(AgentContext ctx) {
        try {
            List<Message> messages = new ArrayList<>(ctx.messages());
            List<ToolSpec> tools = toolRegistry.availableTools(ctx.toolContext());
            log.info(">>> AgentLoop 启动, runId={}, 可用工具 {} 个", ctx.runId(), tools.size());

            for (int step = 0; step < MAX_STEPS; step++) {
                // SSE 连接已关闭（被 stop 或新请求取代），立即退出
                if (ctx.emitter().isClosed()) {
                    log.info(">>> AgentLoop 检测到 SSE 已关闭, runId={}, step={}", ctx.runId(), step);
                    return;
                }
                log.info(">>> AgentLoop step={}, runId={}", step, ctx.runId());

                LlmResponse resp = llmClient.chatStream(messages, tools, ctx.emitter());

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
                                "status", "denied"
                        ));
                        continue;
                    }

                    if (tool.requiresConfirmation() && !ctx.confirmed(call.id())) {
                        replaceMessages(ctx.messages(), messages);
                        pendingStateStore.save(ctx);
                        sendEvent(ctx.emitter(), "tool_confirm", Map.of(
                                "callId", call.id(),
                                "tool", call.name(),
                                "args", call.args()
                        ));
                        ctx.emitter().complete();
                        return;
                    }

                    sendEvent(ctx.emitter(), "tool", Map.of(
                            "name", call.name(),
                            "args", call.args(),
                            "status", "executing"
                    ));

                    ToolResult result = executeWithGuard(tool, call, ctx);

                    sendEvent(ctx.emitter(), "tool", Map.of(
                            "name", call.name(),
                            "args", call.args(),
                            "status", result.success() ? "completed" : "failed",
                            "result", result.content()
                    ));

                    messages.add(Message.toolResult(call.id(), result.content()));
                }
            }

            history.appendAssistant(ctx.assistantMessageId(), ctx.sessionId(), "[已达到最大工具调用步数]");
            sendEvent(ctx.emitter(), "done", Map.of("messageId", ctx.assistantMessageId()));
            ctx.emitter().complete();

        } catch (Exception e) {
            log.error("AgentLoop 异常, runId={}", ctx.runId(), e);
            try {
                sendEvent(ctx.emitter(), "error", Map.of(
                    "code", "INTERNAL_ERROR",
                    "message", "处理失败: " + e.getMessage()
                ));
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
        // 统一事件名为前端约定的格式
        String frontendEventName = switch (name) {
            case "tool_call" -> "tool";
            case "tool_confirm" -> "tool_confirm";
            default -> name;
        };
        emitter.send(frontendEventName, data);
    }
}