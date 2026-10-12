package com.lenovo.ai.agent.core;

import com.lenovo.ai.agent.loop.AgentContext;
import com.lenovo.ai.agent.loop.AgentLoop;
import com.lenovo.ai.llm.Message;
import lombok.extern.slf4j.Slf4j;

/**
 * Agent 基类：注入角色 prompt + 设置工具白名单 + 委托 AgentLoop。
 * 子类只需实现 name/rolePrompt/allowedToolNames/intents。
 *
 * 关于 SSE 静默：
 *  - streamToUser() = true 的 Agent，正常往 emitter 推 delta/done；
 *  - streamToUser() = false 的内部 Agent（planner/synthesizer），
 *    执行期间 mute emitter 并抑制 complete()，避免中间输出污染前端、
 *    避免第一个子任务完成就关闭连接。
 */
@Slf4j
public abstract class AbstractAgent implements Agent {

    protected final AgentLoop agentLoop;

    protected AbstractAgent(AgentLoop agentLoop) {
        this.agentLoop = agentLoop;
    }

    @Override
    public void execute(AgentContext ctx) {
        // 1. 把角色提示词插到 messages 最前面
        ctx.messages().add(0, Message.system(rolePrompt()));
        // 2. 告诉 ctx 本 Agent 的身份 / 工具白名单 / 最大步数
        ctx.setAgentName(name());
        ctx.setAllowedToolNames(allowedToolNames());
        ctx.setMaxSteps(maxSteps());

        // 3. 决定是否对前端可见
        boolean stream = this.streamToUser() && ctx.streamToUser();
        ctx.setStreamToUser(stream);

        if (!stream) {
            ctx.emitter().mute();
            ctx.emitter().setSuppressComplete(true);
        }
        try {
            log.info(">>> Agent[{}] 开始执行, runId={}, stream={}", name(), ctx.runId(), stream);
            // 4. 委托给现有 AgentLoop（循环 / SSE / 工具执行全部复用）
            agentLoop.run(ctx);
        } finally {
            if (!stream) {
                ctx.emitter().unmute();
                ctx.emitter().setSuppressComplete(false);
            }
        }
    }
}