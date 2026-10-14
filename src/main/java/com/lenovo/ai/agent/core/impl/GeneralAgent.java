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
              【回答优先级】
              1. 首先看对话历史：如果用户的问题能从对话历史里找到答案（比如刚才说过的话），
                 直接用历史里的信息回答，不要调用任何工具。
              2. 其次是工作业务问题：需要查企业数据、文档、审批、待办时，调用 query_knowledge。
              3. 最后是通用知识/闲聊：可以直接回答，保持专业。

              【禁止】
              不要编造未在对话历史或检索结果中出现的信息。

              【提醒】
              "我刚才说了什么"、"我午饭吃的什么"、"我的名字是什么"这类问题，
              答案就在对话历史里，请直接回答，不要声称"无法获取"或"没有权限"。
            """;
    }

    @Override public List<String> allowedToolNames() { return List.of("*"); }

    @Override public List<String> intents() { return List.of("chitchat", "general", "unknown"); }
}