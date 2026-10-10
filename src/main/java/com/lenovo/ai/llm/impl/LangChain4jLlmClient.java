package com.lenovo.ai.llm.impl;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lenovo.ai.infra.sse.AiChatSseEmitter;
import com.lenovo.ai.llm.LlmClient;
import com.lenovo.ai.llm.LlmResponse;
import com.lenovo.ai.llm.Message;
import com.lenovo.ai.llm.enums.MessageRole;
import com.lenovo.ai.tools.ToolSpec;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.request.json.JsonObjectSchema;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.chat.response.StreamingChatResponseHandler;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/**
 * 基于 LangChain4j 的 LlmClient 实现：真流式输出 + 工具调用。
 */
@Slf4j
@Component
public class LangChain4jLlmClient implements LlmClient {

    private static final int STREAM_TIMEOUT_SECONDS = 120;
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final StreamingChatModel streamingModel;
    private final ChatModel chatModel;

    public LangChain4jLlmClient(StreamingChatModel streamingModel, ChatModel chatModel) {
        this.streamingModel = streamingModel;
        this.chatModel = chatModel;
    }

    @Override
    public LlmResponse chatStream(List<Message> messages, List<ToolSpec> tools, AiChatSseEmitter emitter) {
        List<ChatMessage> chatMessages = toChatMessages(messages);
        List<ToolSpecification> toolSpecs = toToolSpecifications(tools);

        ChatRequest.Builder requestBuilder = ChatRequest.builder().messages(chatMessages);
        if (!toolSpecs.isEmpty()) {
            requestBuilder.toolSpecifications(toolSpecs);
        }
        ChatRequest request = requestBuilder.build();

        CompletableFuture<LlmResponse> future = new CompletableFuture<>();
        StringBuilder fullContent = new StringBuilder();
        List<ToolExecutionRequest> toolRequests = new ArrayList<>();

        streamingModel.chat(request, new StreamingChatResponseHandler() {
            @Override
            public void onPartialResponse(String partialResponse) {
                if (partialResponse != null && !partialResponse.isEmpty()) {
                    fullContent.append(partialResponse);
                    emitter.send("delta", Map.of("text", partialResponse)); // 改为 delta 事件
                }
            }

            @Override
            public void onCompleteResponse(ChatResponse completeResponse) {
                try {
                    AiMessage aiMessage = completeResponse.aiMessage();
                    String content = aiMessage.text();
                    if (content != null && !content.isEmpty()) {
                        fullContent.setLength(0);
                        fullContent.append(content);
                        emitter.send("done", Map.of("answer", content)); // 确保发送完整答案
                    }
                    if (aiMessage.hasToolExecutionRequests()) {
                        toolRequests.addAll(aiMessage.toolExecutionRequests());
                        emitter.send("tool", Map.of("name", "tool_execution")); // 工具调用事件
                    }
                    future.complete(toLlmResponse(fullContent.toString(), toolRequests));
                } catch (Exception e) {
                    future.completeExceptionally(e);
                }
            }

            @Override
            public void onError(Throwable error) {
                log.error("StreamingChatResponseHandler onError", error);
                emitter.send("error", Map.of("message", error.getMessage()));
                future.completeExceptionally(error);
            }
        });

        try {
            return future.get(STREAM_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        } catch (Exception e) {
            log.error("chatStream 超时或异常", e);
            return new LlmResponse("", List.of());
        }
    }

    @Override
    public String chatSimple(String prompt) {
        if (prompt == null || prompt.isBlank()) return "";
        try {
            ChatMessage userMsg = UserMessage.from(prompt);
            ChatRequest request = ChatRequest.builder()
                    .messages(List.of(userMsg))
                    .build();
            ChatResponse response = chatModel.chat(request);
            AiMessage aiMessage = response.aiMessage();
            return aiMessage.text() != null ? aiMessage.text() : "";
        } catch (Exception e) {
            log.error("chatSimple 异常: {}", prompt.length(), e);
            return "";
        }
    }

    private List<ChatMessage> toChatMessages(List<Message> messages) {
        List<ChatMessage> result = new ArrayList<>();
        for (Message msg : messages) {
            if (msg.role() == MessageRole.SYSTEM) {
                result.add(new SystemMessage(msg.content()));
            } else if (msg.role() == MessageRole.USER) {
                result.add(UserMessage.from(msg.content()));
            } else if (msg.role() == MessageRole.ASSISTANT) {
                if (msg.toolCalls() != null && !msg.toolCalls().isEmpty()) {
                    List<ToolExecutionRequest> requests = msg.toolCalls().stream()
                            .map(ref -> ToolExecutionRequest.builder()
                                    .id(ref.id())
                                    .name(ref.name())
                                    .arguments(toJson(ref.args()))
                                    .build())
                            .toList();
                    result.add(AiMessage.from(requests));
                } else {
                    result.add(AiMessage.from(msg.content()));
                }
            } else if (msg.role() == MessageRole.TOOL) {
                result.add(ToolExecutionResultMessage.from(msg.toolCallId(), null, msg.content()));
            }
        }
        return result;
    }

    @SuppressWarnings("unchecked")
    private List<ToolSpecification> toToolSpecifications(List<ToolSpec> tools) {
        if (tools == null || tools.isEmpty()) return List.of();
        return tools.stream().map(spec -> {
            JsonObjectSchema.Builder schemaBuilder = JsonObjectSchema.builder();
            Map<String, Object> schema = spec.parameterSchema();
            if (schema != null) {
                Object propsObj = schema.get("properties");
                if (propsObj instanceof Map<?, ?> properties) {
                    List<String> requiredList = new ArrayList<>();
                    Object reqObj = schema.get("required");
                    if (reqObj instanceof List<?> reqList) {
                        for (Object r : reqList) {
                            if (r != null) requiredList.add(String.valueOf(r));
                        }
                    }
                    properties.forEach((nameObj, paramObj) -> {
                        String name = String.valueOf(nameObj);
                        if (paramObj instanceof Map<?, ?> rawMap) {
                            Map<String, Object> paramMap = (Map<String, Object>) rawMap;
                            String type = String.valueOf(paramMap.getOrDefault("type", "string"));
                            String desc = String.valueOf(paramMap.getOrDefault("description", ""));
                            switch (type) {
                                case "integer" -> schemaBuilder.addIntegerProperty(name, desc);
                                case "number" -> schemaBuilder.addNumberProperty(name, desc);
                                case "boolean" -> schemaBuilder.addBooleanProperty(name, desc);
                                default -> schemaBuilder.addStringProperty(name, desc);
                            }
                        }
                    });
                    if (!requiredList.isEmpty()) schemaBuilder.required(requiredList);
                }
            }
            return ToolSpecification.builder()
                    .name(spec.name())
                    .description(spec.description())
                    .parameters(schemaBuilder.build())
                    .build();
        }).toList();
    }

    private LlmResponse toLlmResponse(String content, List<ToolExecutionRequest> requests) {
        if (requests.isEmpty()) {
            return new LlmResponse(content, List.of());
        }
        List<LlmResponse.ToolCall> toolCalls = requests.stream().map(req -> {
            Map<String, Object> args = parseJson(req.arguments());
            return new LlmResponse.ToolCall(req.id(), req.name(), args);
        }).toList();
        return new LlmResponse(content, toolCalls);
    }

    private String toJson(Map<String, Object> map) {
        if (map == null || map.isEmpty()) return "{}";
        try {
            return MAPPER.writeValueAsString(map);
        } catch (Exception e) {
            log.warn("toJson failed", e);
            return "{}";
        }
    }

    private Map<String, Object> parseJson(String json) {
        if (json == null || json.isBlank()) return Map.of();
        try {
            return MAPPER.readValue(json, new TypeReference<Map<String, Object>>() {});
        } catch (Exception e) {
            log.warn("parseJson failed: {}", json, e);
            return Map.of();
        }
    }
}