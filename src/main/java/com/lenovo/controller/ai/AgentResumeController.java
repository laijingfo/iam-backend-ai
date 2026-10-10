package com.lenovo.controller.ai;

import com.lenovo.ai.agent.loop.AgentContext;
import com.lenovo.ai.agent.loop.AgentLoop;
import com.lenovo.ai.agent.loop.PendingStateStore;
import com.lenovo.ai.agent.runtime.AiChatExecutor;
import com.lenovo.ai.infra.sse.AiChatSseEmitter;
import com.lenovo.ai.tools.context.AiToolContext;
import com.lenovo.security.utils.SecurityUtils;
import com.lenovo.util.RedisUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.Map;

/**
 * Agent 挂起状态恢复接口：前端确认工具调用后，通过此接口恢复 AgentLoop 执行。
 */
@Slf4j
@RestController
@RequestMapping("/agent")
@RequiredArgsConstructor
public class AgentResumeController {

    private final PendingStateStore pendingStateStore;
    private final AgentLoop agentLoop;
    private final RedisUtils redisUtils;
    private final AiChatExecutor chatExecutor;

    @GetMapping("/resume")
    public SseEmitter resume(@RequestParam String runId,
                             @RequestParam String callId,
                             @RequestParam boolean approved) {
        String userId = SecurityUtils.getCurrentUserId();
        PendingStateStore.PendingState state = pendingStateStore.load(runId);
        AiChatSseEmitter emitter = new AiChatSseEmitter();
        if (state == null) {
            try {
                emitter.send(SseEmitter.event().name("error")
                        .data(Map.of("message", "挂起状态不存在或已过期")));
            } catch (Exception ignored) {}
            emitter.complete();
            return emitter;
        }

        AiToolContext toolContext = AiToolContext.capture(state.userId(), redisUtils);
        AgentContext ctx = new AgentContext(
                state.messages(), toolContext, emitter,
                state.sessionId(), state.assistantMessageId(), state.runId(),
                state.memoryId());

        for (String c : state.confirmedCalls()) ctx.markConfirmed(c);
        if (approved) ctx.markConfirmed(callId);

        pendingStateStore.remove(runId);

        boolean accepted = chatExecutor.submit(() -> {
            AiToolContext.set(toolContext);
            try {
                agentLoop.run(ctx);
            } finally {
                AiToolContext.clear();
            }
        });
        if (!accepted) {
            try {
                emitter.send(SseEmitter.event().name("error")
                        .data(Map.of("message", "系统繁忙，请稍后重试")));
            } catch (Exception ignored) {}
            emitter.complete();
        }
        return emitter;
    }
}