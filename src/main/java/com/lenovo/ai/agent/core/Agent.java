package com.lenovo.ai.agent.core;

import com.lenovo.ai.agent.loop.AgentContext;

import java.util.List;

/**
 * 一个 Agent = 角色设定 + 工具白名单 + 意图标签。
 * 执行逻辑委托给 AgentLoop，Agent 本身只负责"身份"。
 */
public interface Agent {

    /** 唯一标识，如 "knowledge"、"ticket"、"general" */
    String name();

    /** 角色提示词，会注入到 messages 最前面（system 角色） */
    String rolePrompt();

    /** 可用工具名白名单；["*"] 表示全部工具；空列表表示不调工具。
     *  默认空列表（最严格），子类必须显式声明。 */
    default List<String> allowedToolNames() { return List.of(); }

    /** 可处理的意图标签，供 Router 匹配 */
    List<String> intents();

    /** 最大工具调用步数，默认 8 */
    default int maxSteps() { return 8; }

    /** 是否把 LLM 流式输出推给前端。内部 Agent（planner/synthesizer）返回 false */
    default boolean streamToUser() { return true; }

    /** 执行入口 */
    void execute(AgentContext ctx);
}