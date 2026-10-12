package com.lenovo.ai.agent.core.impl;

import com.lenovo.ai.agent.core.AbstractAgent;
import com.lenovo.ai.agent.loop.AgentLoop;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 兜底 Agent：路由失败时使用，拥有全部工具权限。
 */
@Component
public class GeneralAgent extends AbstractAgent {

    public GeneralAgent(AgentLoop loop) { super(loop); }

    @Override public String name() { return "general"; }

    @Override public String rolePrompt() {
        return """
            你是企业智能助手，专业、友好、简洁。
            业务相关问题优先调用 query_knowledge 检索；
            闲聊与通用知识可直接回答，但保持专业。
            不要编造未在检索结果中出现的信息。
            """;
    }

    @Override public List<String> allowedToolNames() { return List.of("*"); }

    @Override public List<String> intents() { return List.of("chitchat", "general", "unknown"); }
}