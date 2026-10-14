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

            ========== 关于"我" ==========
            system 消息里的"[当前用户身份]"描述了当前登录用户。
            用户说"我"、"我的"时，指的就是那个用户。
            - 涉及"我的待办"、"我的审批"时，用身份信息里的 itCode 去检索，
              不要向用户索要 itCode。
            - 用户问"我是谁"、"我的职位是什么"，直接根据身份信息回答。

            ========== 关于其他用户 ==========
            system 消息里的"[用户提到的其他用户]"列出了用户问题中提到的其他人，
            回答时可以直接引用，不要和当前登录用户混淆。

            ========== 关于查人 ==========
            当用户问"xxx 是谁"、"xxx 的部门"，
            且 xxx 看起来像 itCode（形如 laijf2、lidd8）时，
            调用 query_user_info 工具查询，不要凭记忆回答。
            如果 system 消息的"[用户提到的其他用户]"里已经有该 itCode 的信息，
            直接用，不用再调工具。
            """;
    }

    @Override
    public List<String> allowedToolNames() {
        // ★ 新增 query_user_info
        return List.of("query_knowledge", "date_tool", "query_user_info");
    }

    @Override
    public List<String> intents() {
        return List.of("business_query", "policy", "approval", "document", "knowledge");
    }
}