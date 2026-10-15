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

    /**
     * 指定模型的流式调用。
     * <p>模型路由（ModelRouter）的结果必须通过本方法传入才会真正生效；
     * 走三参重载时实际使用的模型由 StreamingChatModel Bean 决定。</p>
     *
     * @param modelName 目标模型名，为 null 时回退到 Bean 默认模型
     */
    default LlmResponse chatStream(List<Message> messages,
                                   List<ToolSpec> tools,
                                   AiChatSseEmitter emitter,
                                   String modelName) {
        return chatStream(messages, tools, emitter);
    }

    String chatSimple(String prompt);
}
