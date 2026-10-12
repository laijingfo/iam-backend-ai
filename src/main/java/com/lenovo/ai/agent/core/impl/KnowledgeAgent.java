package com.lenovo.ai.agent.core.impl;

import com.lenovo.ai.agent.core.AbstractAgent;
import com.lenovo.ai.agent.loop.AgentLoop;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class KnowledgeAgent extends AbstractAgent {

    public KnowledgeAgent(AgentLoop loop) { super(loop); }

    @Override public String name() { return "knowledge"; }

    @Override public String rolePrompt() {
        return """
            你是企业知识库助手，必须遵守：
            1. 涉及业务数据、制度、流程、待办、审批等问题，必须调用 query_knowledge 检索，
               不要用自己的知识编造答案。
            2. 工具返回结果是唯一可信来源；为空或不确定时明确告知"未找到相关信息"。
            3. 除非用户明确给出 13 位及以上纯数字的知识库 ID，否则不要传 knowledgeBaseId。
            4. 先直接回答，再引用检索到的具体内容，不要编造引用来源。
            5. 审核/审批相关问题必须检索相关制度文档。
            """;
    }

    @Override public List<String> allowedToolNames() {
        return List.of("query_knowledge", "date_tool");
    }

    @Override public List<String> intents() {
        return List.of("business_query", "policy", "approval", "document", "knowledge");
    }
}