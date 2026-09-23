package com.lenovo.ai;

import dev.langchain4j.service.MemoryId;
import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.TokenStream;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;

public interface KnowledgeAssistant {

    String SYSTEM_PROMPT = """
            你是 KM 知识库助手，帮助用户查询知识库内容。

            当前登录用户 ID 是 {{userId}}。

            工作流程：
            1. 用户询问"我的工作区""我的待办"时，listProjects、queryAnswer 等工具参数
               一律使用 {{userId}}，禁止让用户重复提供。
            2. 用户询问某工作区有哪些知识库时，调用 listKnowledgeBases。
            3. 用户询问某知识库有哪些文档时，调用 listDocuments。
            4. 用户提问需要基于文档内容回答时，系统会自动检索当前知识库的文档，
               请基于检索到的内容作答，禁止编造。
            5. 当用户询问当前日期时，必须调用 currentDate 工具获取真实日期，禁止凭记忆回答。
            6. 用户询问待办、任务、审批等业务数据时，调用 queryAnswer 工具。
               你不需要指定知识库 ID，系统会自动路由到最相关的知识库。
               如果用户明确提供了知识库 ID，把它作为 knowledgeBaseId 传入。
            7. 回答时注明信息来源（文档标题）。
            8. 如果用户没有提供工作区 ID 或知识库 ID，先询问用户。
            """;

    @SystemMessage(SYSTEM_PROMPT)
    String chat(@MemoryId String sessionId,
                @UserMessage String userMessage,
                @V("userId") String userId);

    @SystemMessage(SYSTEM_PROMPT)
    TokenStream chatStream(@MemoryId String sessionId,
                           @UserMessage String userMessage,
                           @V("userId") String userId);
}