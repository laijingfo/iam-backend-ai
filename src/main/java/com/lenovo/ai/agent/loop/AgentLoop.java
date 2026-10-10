package com.lenovo.ai.agent.loop;                                               // 包声明

import com.lenovo.ai.infra.history.ChatHistoryStore;                            // 历史存储
import com.lenovo.ai.infra.sse.AiChatSseEmitter;                                // SSE 发射器
import com.lenovo.ai.llm.LlmClient;                                             // LLM 客户端接口
import com.lenovo.ai.llm.LlmResponse;                                           // LLM 响应
import com.lenovo.ai.llm.Message;                                               // 消息模型
import com.lenovo.ai.memory.MemoryStore;                                        // 记忆存储（当前未使用，可能预留）
import com.lenovo.ai.tools.AiTool;                                              // 工具接口
import com.lenovo.ai.tools.ToolRegistry;                                        // 工具注册表
import com.lenovo.ai.tools.ToolResult;                                          // 工具结果
import com.lenovo.ai.tools.ToolSpec;                                            // 工具描述
import com.lenovo.ai.tools.context.AiToolContext;                               // 工具上下文
import lombok.RequiredArgsConstructor;                                          // 构造注入
import lombok.extern.slf4j.Slf4j;                                               // 日志
import org.springframework.stereotype.Component;                                // Spring 组件

import java.util.ArrayList;                                                     // 动态数组
import java.util.List;                                                          // 列表
import java.util.Map;                                                           // Map
import java.util.concurrent.CompletableFuture;                                  // 异步任务
import java.util.concurrent.TimeUnit;                                           // 时间单位

@Slf4j                                                                          // 日志
@Component                                                                      // 注册 Bean
@RequiredArgsConstructor                                                        // 构造注入
public class AgentLoop {                                                        // Agent 主循环

    private static final int MAX_STEPS = 8;                                     // 最大工具调用步数（防止无限循环）
    private static final int TOOL_TIMEOUT_SECONDS = 30;                         // 单次工具执行超时（秒）

    private final LlmClient llmClient;                                          // LLM 客户端
    private final ToolRegistry toolRegistry;                                    // 工具注册表
    private final ChatHistoryStore history;                                     // 历史存储
    private final PendingStateStore pendingStateStore;                          // 挂起状态存储（需确认工具）
    private final MemoryStore memoryStore;                                      // 记忆存储（当前未使用）

    /**                                                                         // Javadoc
     * 运行 Agent 循环。                                                          // 说明
     */                                                                         // 结束
    public void run(AgentContext ctx) {                                         // 入口方法
        try {                                                                   // 主流程 try
            List<Message> messages = new ArrayList<>(ctx.messages());           // 1. 拷贝消息列表（避免外部修改）
            List<ToolSpec> tools = toolRegistry.availableTools(ctx.toolContext());// 2. 按角色过滤可用工具
            log.info(">>> AgentLoop 启动, runId={}, 可用工具 {} 个", ctx.runId(), tools.size()); // 日志

            for (int step = 0; step < MAX_STEPS; step++) {                      // 3. 循环最多 MAX_STEPS 次
                // SSE 连接已关闭（被 stop 或新请求取代），立即退出                 //    提前退出条件
                if (ctx.emitter().isClosed()) {                                 // 4. 检测 SSE 是否关闭
                    log.info(">>> AgentLoop 检测到 SSE 已关闭, runId={}, step={}", ctx.runId(), step); // 日志
                    return;                                                     //    直接返回
                }
                log.info(">>> AgentLoop step={}, runId={}", step, ctx.runId()); // 5. 记录步数

                LlmResponse resp = llmClient.chatStream(messages, tools, ctx.emitter()); // 6. 流式调用 LLM

                if (!resp.hasToolCalls()) {                                     // 7. 若无工具调用 → 最终答案
                    history.appendAssistant(ctx.assistantMessageId(), ctx.sessionId(), resp.content()); // 7.1 落库 assistant 消息
                    sendEvent(ctx.emitter(), "done", Map.of("messageId", ctx.assistantMessageId())); // 7.2 发送 done 事件
                    ctx.emitter().complete();                                   // 7.3 关闭 SSE
                    return;                                                     // 7.4 结束
                }

                List<Message.ToolCallRef> refs = resp.toolCalls().stream()      // 8. 有工具调用：转换格式
                        .map(tc -> new Message.ToolCallRef(tc.id(), tc.name(), tc.args())) //    映射为内部 ToolCallRef
                        .toList();                                              //    收集为 List
                messages.add(Message.assistantWithTools(resp.content(), refs)); // 8.1 将 assistant+toolCalls 加入消息列表

                for (LlmResponse.ToolCall call : resp.toolCalls()) {            // 9. 逐个执行工具调用
                    AiTool tool = toolRegistry.get(call.name());                // 9.1 查找工具
                    if (tool == null) {                                         // 9.2 工具不存在
                        messages.add(Message.toolResult(call.id(), "工具不存在: " + call.name())); //    回填错误结果
                        continue;                                               //    继续下一个
                    }

                    if (!toolRegistry.isAllowed(call.name(), ctx.toolContext())) { // 9.3 权限二次校验
                        messages.add(Message.toolResult(call.id(), "无权限调用该工具")); //    回填拒绝结果
                        sendEvent(ctx.emitter(), "tool", Map.of(                //    发送 tool 事件
                                "name", call.name(),                            //    工具名
                                "status", "denied"                              //    状态：拒绝
                        ));
                        continue;                                               //    继续下一个
                    }

                    if (tool.requiresConfirmation() && !ctx.confirmed(call.id())) { // 9.4 需确认且未确认
                        replaceMessages(ctx.messages(), messages);              //    将本地消息列表回写 ctx
                        pendingStateStore.save(ctx);                            //    保存挂起状态到 Redis
                        sendEvent(ctx.emitter(), "tool_confirm", Map.of(        //    发送 tool_confirm 事件
                                "callId", call.id(),                            //    调用 ID
                                "tool", call.name(),                            //    工具名
                                "args", call.args()                             //    参数
                        ));
                        ctx.emitter().complete();                               //    关闭 SSE（等待用户确认后恢复）
                        return;                                                 //    结束本次 run
                    }

                    sendEvent(ctx.emitter(), "tool", Map.of(                    // 9.5 发送 tool 事件（执行中）
                            "name", call.name(),                                //    工具名
                            "args", call.args(),                                //    参数
                            "status", "executing"                               //    状态：执行中
                    ));

                    ToolResult result = executeWithGuard(tool, call, ctx);      // 9.6 带超时/异常保护执行工具

                    sendEvent(ctx.emitter(), "tool", Map.of(                    // 9.7 发送 tool 事件（完成）
                            "name", call.name(),                                //    工具名
                            "args", call.args(),                                //    参数
                            "status", result.success() ? "completed" : "failed",//    状态
                            "result", result.content()                          //    结果内容
                    ));

                    messages.add(Message.toolResult(call.id(), result.content())); // 9.8 将工具结果加入消息列表（下一轮 LLM 可见）
                }
            }

            history.appendAssistant(ctx.assistantMessageId(), ctx.sessionId(), "[已达到最大工具调用步数]"); // 10. 达到最大步数：落库兜底消息
            sendEvent(ctx.emitter(), "done", Map.of("messageId", ctx.assistantMessageId())); // 11. 发送 done
            ctx.emitter().complete();                                           // 12. 关闭 SSE

        } catch (Exception e) {                                                 // 13. 异常兜底
            log.error("AgentLoop 异常, runId={}", ctx.runId(), e);              //    日志
            try {                                                               //    尝试发送 error 事件
                sendEvent(ctx.emitter(), "error", Map.of(                       //    发送 error
                        "code", "INTERNAL_ERROR",                                   //    错误码
                        "message", "处理失败: " + e.getMessage()                    //    错误消息
                ));
            } catch (Exception ignored) {}                                      //    忽略发送异常
            try {                                                               //    尝试关闭 SSE
                ctx.emitter().complete();                                       //    关闭
            } catch (Exception ignored) {}                                      //    忽略异常
        }
    }

    /**                                                                         // Javadoc
     * 将 source 内容覆盖到 target（用于挂起前回写 ctx.messages）。                // 说明
     */                                                                         // 结束
    private void replaceMessages(List<Message> target, List<Message> source) {  // 覆盖方法
        target.clear();                                                         // 清空目标
        target.addAll(source);                                                  // 添加源内容
    }

    /**                                                                         // Javadoc
     * 带超时与异常保护的工具执行。                                                // 说明
     */                                                                         // 结束
    private ToolResult executeWithGuard(AiTool tool, LlmResponse.ToolCall call, AgentContext ctx) { // 方法签名
        try {                                                                   // 外层 try
            return CompletableFuture.supplyAsync(() -> {                        // 异步执行
                        AiToolContext.set(ctx.toolContext());                   //    设置 ThreadLocal 上下文
                        try {                                                   //    内层 try
                            return tool.execute(call.args(), ctx.toolContext());//    执行工具
                        } finally {                                             //    finally
                            AiToolContext.clear();                              //    清理 ThreadLocal（防止线程池污染）
                        }
                    })
                    .orTimeout(TOOL_TIMEOUT_SECONDS, TimeUnit.SECONDS)          // 超时设置
                    .exceptionally(e -> {                                       // 异常处理
                        log.error("工具执行异常: {}", tool.name(), e);            //    日志
                        return ToolResult.fail("工具执行失败: " + e.getMessage()); //    返回失败结果
                    })
                    .join();                                                    // 阻塞等待
        } catch (Exception e) {                                                 // 提交异常
            log.error("工具提交失败: {}", tool.name(), e);                        //    日志
            return ToolResult.fail("工具执行失败: " + e.getMessage());            //    返回失败结果
        }
    }

    /**                                                                         // Javadoc
     * 发送 SSE 事件（统一前端事件名格式）。                                        // 说明
     */                                                                         // 结束
    private void sendEvent(AiChatSseEmitter emitter, String name, Map<String, Object> data) { // 方法签名
        // 统一事件名为前端约定的格式                                             // 注释
        String frontendEventName = switch (name) {                              // 事件名映射
            case "tool_call" -> "tool";                                         //    tool_call → tool
            case "tool_confirm" -> "tool_confirm";                              //    tool_confirm 保持不变
            default -> name;                                                    //    其他保持不变
        };
        emitter.send(frontendEventName, data);                                  // 发送事件
    }
}