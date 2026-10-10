package com.lenovo.ai.llm;

import com.lenovo.ai.infra.sse.AiChatSseEmitter;
import com.lenovo.ai.tools.ToolSpec;

import java.util.List;

/**
 * @author laifo
 * @version 1.0
 * @date 2026-09-30 10:10
 * @project iam-backend-ai
 * @description
 */
public interface LlmClient {

    LlmResponse chatStream(List<Message> messages,
                           List<ToolSpec> tools,
                           AiChatSseEmitter emitter);

    String chatSimple(String prompt);
}
