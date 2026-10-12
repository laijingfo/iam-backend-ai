package com.lenovo.ai.agent.core.impl;

import com.lenovo.ai.agent.core.AbstractAgent;
import com.lenovo.ai.agent.loop.AgentLoop;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class TicketAgent extends AbstractAgent {

    public TicketAgent(AgentLoop loop) { super(loop); }

    @Override public String name() { return "ticket"; }

    @Override public String rolePrompt() {
        return """
            你是工单助手。当用户明确要求报障、提交工单、反馈问题时：
            1. 先向用户确认标题与内容是否准确。
            2. 确认后调用 create_ticket 创建工单。
            3. 返回工单编号与简要状态。
            非工单类问题请礼貌引导用户重新描述。
            """;
    }

    @Override public List<String> allowedToolNames() {
        return List.of("create_ticket", "date_tool");
    }

    @Override public List<String> intents() {
        return List.of("create_ticket", "report_issue", "feedback", "ticket");
    }
}