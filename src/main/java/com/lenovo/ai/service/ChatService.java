package com.lenovo.ai.service;

import com.lenovo.ai.infra.history.ChatHistoryStore;
import com.lenovo.ai.infra.sse.AiChatSseEmitter;
import com.lenovo.ai.llm.LlmClient;
import com.lenovo.ai.llm.LlmResponse;
import com.lenovo.ai.llm.Message;
import com.lenovo.ai.llm.enums.MessageRole;
import com.lenovo.ai.memory.facade.MemoryFacade;
import com.lenovo.ai.tools.ToolSpec;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class ChatService {

    private final MemoryFacade memoryFacade;
    private final ChatHistoryStore chatHistoryStore;
    private final LlmClient llmClient;

    public LlmResponse chat(String userId,
                            String sessionId,
                            String userMsg,
                            List<ToolSpec> tools,
                            AiChatSseEmitter emitter) {

        // ★ P1 修复：先落 user，与主链路 AiAgentRunner 一致。
        //   这样 LLM 失败时用户消息也不会丢，且 buildContext 里 isAlreadyPresent 会跳过重复注入。
        long userMessageId = chatHistoryStore.nextMessageId();
        chatHistoryStore.appendUser(userMessageId, sessionId, userId, userMsg);

        // 1. 构建上下文
        List<Message> context = memoryFacade.buildContext(userId, sessionId, userMsg);

        // 2. 流式调用 LLM
        LlmResponse response = llmClient.chatStream(context, tools, emitter);

        // 3. 落 assistant
        long assistantMessageId = chatHistoryStore.nextMessageId();
        chatHistoryStore.appendAssistant(assistantMessageId, sessionId, response.content());

        // 4. 异步抽取记忆
        memoryFacade.extractAsync(userId, List.of(
                Message.user(userMsg),
                new Message(MessageRole.ASSISTANT, response.content(), null, null)
        ));

        return response;
    }
}