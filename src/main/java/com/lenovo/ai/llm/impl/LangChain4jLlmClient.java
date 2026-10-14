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
import dev.langchain4j.exception.InternalServerException;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.request.json.JsonObjectSchema;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.chat.response.StreamingChatResponseHandler;
import dev.langchain4j.model.output.TokenUsage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

@Slf4j
@Component
public class LangChain4jLlmClient implements LlmClient {

    private static final int STREAM_TIMEOUT_SECONDS = 120;
    private static final int CHAT_SIMPLE_MAX_RETRIES = 3;
    private static final long CHAT_SIMPLE_RETRY_BACKOFF_MS = 500;
    private static final int LOG_RESP_PREVIEW_LEN = 500;
//    private static final ObjectMapper MAPPER = new ObjectMapper();
    private final StreamingChatModel streamingModel;
    private final ChatModel chatModel;
    private final ObjectMapper objectMapper;   // ★ 新增

    public LangChain4jLlmClient(StreamingChatModel streamingModel,
                                ChatModel chatModel,
                                ObjectMapper objectMapper) {
        this.streamingModel = streamingModel;
        this.chatModel = chatModel;
        this.objectMapper = objectMapper;
    }

    // =================================================================
    //  chatStream
    // =================================================================
    @Override
    public LlmResponse chatStream(List<Message> messages, List<ToolSpec> tools,
                                  AiChatSseEmitter emitter) {
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

        // ★ usage 累积容器（在回调里填，主线程读取）
        final int[] usageHolder = new int[]{0, 0, 0, 0};

        streamingModel.chat(request, new StreamingChatResponseHandler() {
            @Override
            public void onPartialResponse(String partialResponse) {
                if (partialResponse != null && !partialResponse.isEmpty()) {
                    fullContent.append(partialResponse);
                    emitter.send("delta", Map.of("text", partialResponse));
                }
            }

            @Override
            public void onCompleteResponse(ChatResponse completeResponse) {
                try {
                    AiMessage aiMessage = completeResponse.aiMessage();
                    if (aiMessage.hasToolExecutionRequests()) {
                        toolRequests.addAll(aiMessage.toolExecutionRequests());
                    }

                    // ★ 提取 token usage
                    TokenUsage tu = completeResponse.tokenUsage();
                    if (tu != null) {
                        usageHolder[0] = tu.inputTokenCount() == null ? 0 : tu.inputTokenCount();
                        usageHolder[1] = tu.outputTokenCount() == null ? 0 : tu.outputTokenCount();
                        usageHolder[2] = tu.totalTokenCount() == null ? 0 : tu.totalTokenCount();

                        // ★ 提取 cachedTokens（从原始响应的 prompt_tokens_details.cached_tokens）
                        usageHolder[3] = extractCachedTokens(completeResponse);
                    }

                    future.complete(toLlmResponse(
                            fullContent.toString(), toolRequests,
                            usageHolder[0], usageHolder[1], usageHolder[2], usageHolder[3]));
                } catch (Exception e) {
                    future.completeExceptionally(e);
                }
            }

            @Override
            public void onError(Throwable error) {
                log.error("StreamingChatResponseHandler onError", error);
                // ★ 出错也返回已产生的 usage
                future.complete(toLlmResponse(
                        fullContent.toString(), toolRequests,
                        usageHolder[0], usageHolder[1], usageHolder[2], usageHolder[3]));
            }
        });

        try {
            return future.get(STREAM_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        } catch (Exception e) {
            log.error("chatStream 超时或异常", e);
            // ★ 使用 of() 补 0 usage
            return LlmResponse.of("", List.of());
        }
    }
    /** 从 LangChain4j 响应中提取 cachedTokens */
    private int extractCachedTokens(ChatResponse response) {
        try {
            // LangChain4j 的 TokenUsage 不直接暴露 cachedTokens
            // 尝试从 metadata 或原始响应中提取
            Object metadata = response.metadata();
            if (metadata instanceof Map<?, ?> map) {
                Object details = map.get("prompt_tokens_details");
                if (details instanceof Map<?, ?> detailsMap) {
                    Object cached = detailsMap.get("cached_tokens");
                    if (cached instanceof Number n) {
                        return n.intValue();
                    }
                }
            }
        } catch (Exception e) {
            log.debug("提取 cachedTokens 失败", e);
        }
        return 0;
    }

    private LlmResponse toLlmResponse(String content,
                                      List<ToolExecutionRequest> requests,
                                      int inputTokens, int outputTokens, int totalTokens,
                                      int cachedTokens) {          // ★ 新增参数
        if (requests.isEmpty()) {
            return LlmResponse.of(content, List.of(),
                    inputTokens, outputTokens, totalTokens, cachedTokens);   // ★ 传 5 个
        }
        List<LlmResponse.ToolCall> toolCalls = requests.stream().map(req -> {
            Map<String, Object> args = parseJson(req.arguments());
            return new LlmResponse.ToolCall(req.id(), req.name(), args);
        }).toList();
        return LlmResponse.of(content, toolCalls,
                inputTokens, outputTokens, totalTokens, cachedTokens);       // ★ 传 5 个
    }
    // =================================================================
    //  chatSimple（逻辑完全不变）
    // =================================================================
    @Override
    public String chatSimple(String prompt) {
        if (prompt == null || prompt.isBlank()) return "";
        int maxAttempts = CHAT_SIMPLE_MAX_RETRIES + 1;
        InternalServerException lastException = null;
        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            try {
                ChatMessage userMsg = UserMessage.from(prompt);
                ChatRequest request = ChatRequest.builder()
                        .messages(List.of(userMsg))
                        .build();
                ChatResponse response = chatModel.chat(request);
                AiMessage aiMessage = response.aiMessage();

                if (aiMessage == null) {
                    log.warn(">>> chatSimple aiMessage 为 null，attempt={}/{}, promptLen={}",
                            attempt, maxAttempts, prompt.length());
                    if (attempt < maxAttempts) {
                        long sleepMs = CHAT_SIMPLE_RETRY_BACKOFF_MS * (1L << (attempt - 1));
                        try { Thread.sleep(sleepMs); }
                        catch (InterruptedException ie) {
                            Thread.currentThread().interrupt();
                            break;
                        }
                        continue;
                    }
                    return "";
                }

                String text = aiMessage.text() != null ? aiMessage.text() : "";
                log.info(">>> chatSimple 返回, promptLen={}, respLen={}, resp={}",
                        prompt.length(), text.length(),
                        text.length() <= LOG_RESP_PREVIEW_LEN
                                ? text
                                : text.substring(0, LOG_RESP_PREVIEW_LEN) + "...(truncated)");

                if (attempt > 1) {
                    log.info(">>> chatSimple 重试成功，第 {}/{} 次", attempt, maxAttempts);
                }
                return text;

            } catch (InternalServerException e) {
                lastException = e;
                boolean willRetry = attempt < maxAttempts;
                log.warn(">>> chatSimple 调用失败（可能上游返回空 choices），尝试 {}/{}，prompt 长度={}，是否继续重试={}",
                        attempt, maxAttempts, prompt.length(), willRetry, e);
                if (!willRetry) {
                    break;
                }
                long sleepMs = CHAT_SIMPLE_RETRY_BACKOFF_MS * (1L << (attempt - 1));
                try {
                    Thread.sleep(sleepMs);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    log.warn(">>> chatSimple 重试等待被中断");
                    break;
                }
            } catch (Exception e) {
                log.error("chatSimple 异常, promptLen={}", prompt.length(), e);
                return "";
            }
        }
        log.error(">>> chatSimple 在 {} 次尝试后仍失败，返回空", maxAttempts, lastException);
        return "";
    }

    // =================================================================
    //  内部消息 → L4j 消息（不变）
    // =================================================================
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

    // =================================================================
    //  工具描述转换（不变）
    // =================================================================
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
                                case "number"  -> schemaBuilder.addNumberProperty(name, desc);
                                case "boolean" -> schemaBuilder.addBooleanProperty(name, desc);
                                default        -> schemaBuilder.addStringProperty(name, desc);
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

    // =================================================================
    //  ★ 核心改动：toLlmResponse 增加 usage 参数
    // =================================================================
    private LlmResponse toLlmResponse(String content,
                                      List<ToolExecutionRequest> requests,
                                      int inputTokens, int outputTokens, int totalTokens) {
        if (requests.isEmpty()) {
            // ★ 使用 of() 补 usage
            return LlmResponse.of(content, List.of(), inputTokens, outputTokens, totalTokens);
        }
        List<LlmResponse.ToolCall> toolCalls = requests.stream().map(req -> {
            Map<String, Object> args = parseJson(req.arguments());
            return new LlmResponse.ToolCall(req.id(), req.name(), args);
        }).toList();
        return LlmResponse.of(content, toolCalls, inputTokens, outputTokens, totalTokens);
    }

    // =================================================================
    //  Map → JSON / JSON → Map（不变）
    // =================================================================
    private String toJson(Map<String, Object> map) {
        if (map == null || map.isEmpty()) return "{}";
        try {
            // 原：return MAPPER.writeValueAsString(map);
            return objectMapper.writeValueAsString(map);
        } catch (Exception e) {
            log.warn("toJson failed", e);
            return "{}";
        }
    }

    private Map<String, Object> parseJson(String json) {
        if (json == null || json.isBlank()) return Map.of();
        try {
            // 原：return MAPPER.readValue(json, new TypeReference<Map<String, Object>>() {});
            return objectMapper.readValue(json, new TypeReference<Map<String, Object>>() {});
        } catch (Exception e) {
            log.warn("parseJson failed: {}", json, e);
            return Map.of();
        }
    }
}