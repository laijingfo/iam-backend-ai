package com.lenovo.ai.llm.impl;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lenovo.ai.infra.sse.AiChatSseEmitter;
import com.lenovo.ai.llm.LlmClient;
import com.lenovo.ai.llm.LlmResponse;
import com.lenovo.ai.llm.Message;
import com.lenovo.ai.llm.cache.CachedTokenExtractor;
import com.lenovo.ai.llm.cache.CachedTokenListener;
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
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicIntegerArray;

@Slf4j
@Component
public class LangChain4jLlmClient implements LlmClient {

    private static final int STREAM_TIMEOUT_SECONDS = 120;
    private static final int CHAT_SIMPLE_MAX_RETRIES = 3;
    private static final long CHAT_SIMPLE_RETRY_BACKOFF_MS = 500;
    private static final int LOG_RESP_PREVIEW_LEN = 500;

    private final StreamingChatModel streamingModel;
    private final ChatModel chatModel;

    /**
     * Bean 默认模型名。
     * 用于两处：① modelName 为 null 时的兜底；② 目标模型不存在时的回退重试目标。
     */
    @Value("${langchain4j.open-ai.streaming-chat-model.model-name:}")
    private String defaultModelName;

    /** 兜底的兜底 */
    private static final String LAST_RESORT_MODEL = "qwen3-max";

    /**
     * ★ 修复：原实现构造器接收了 objectMapper 却丢弃不用，
     * 方法内改用 private static final 实例，导致 JacksonConfig
     * 统一配置的模块 / 时区 / 命名策略在此处完全失效。
     */
    private final ObjectMapper objectMapper;

    public LangChain4jLlmClient(StreamingChatModel streamingModel,
                                ChatModel chatModel,
                                ObjectMapper objectMapper) {
        this.streamingModel = streamingModel;
        this.chatModel = chatModel;
        this.objectMapper = (objectMapper != null ? objectMapper : new ObjectMapper())
                .copy()
                .setSerializationInclusion(JsonInclude.Include.ALWAYS);   // 允许 null
    }

    // =================================================================
    //  chatStream
    // =================================================================

    @Override
    public LlmResponse chatStream(List<Message> messages, List<ToolSpec> tools,
                                  AiChatSseEmitter emitter) {
        return chatStream(messages, tools, emitter, null);
    }

    @Override
    public LlmResponse chatStream(List<Message> messages, List<ToolSpec> tools,
                                  AiChatSseEmitter emitter, String modelName) {
        String target = resolveModel(modelName);
        LlmResponse resp = doStream(messages, tools, emitter, target);

        // ★ 模型不存在 → 回退 Bean 默认模型重试一次
        //   仅在尚未产出任何 delta 时重试，避免把两段内容拼在一起推给前端
        String fallback = resolveModel(null);
        if (resp.isFailed()
                && isModelUnavailable(resp.error())
                && !target.equals(fallback)
                && (resp.content() == null || resp.content().isEmpty())) {
            log.warn(">>> 模型[{}]不可用，自动回退默认模型[{}]重试", target, fallback);
            return doStream(messages, tools, emitter, fallback);
        }
        return resp;
    }

    /** 实际执行流式调用 */
    /** 实际执行流式调用 */
    private LlmResponse doStream(List<Message> messages, List<ToolSpec> tools,
                                 AiChatSseEmitter emitter, String modelName) {
        List<ChatMessage> chatMessages = toChatMessages(messages);
        List<ToolSpecification> toolSpecs = toToolSpecifications(tools);

        ChatRequest.Builder requestBuilder = ChatRequest.builder().messages(chatMessages);
        if (!toolSpecs.isEmpty()) {
            requestBuilder.toolSpecifications(toolSpecs);
        }
        // ★ 让 ModelRouter 的选择真正生效（原实现从未设置 modelName）
        if (modelName != null && !modelName.isBlank()) {
            requestBuilder.modelName(modelName);
        }
        ChatRequest request = requestBuilder.build();

        CompletableFuture<LlmResponse> future = new CompletableFuture<>();
        StringBuilder fullContent = new StringBuilder();
        List<ToolExecutionRequest> toolRequests = new ArrayList<>();

        // ★ R3 修复：usageHolder 用 AtomicIntegerArray，避免 onComplete/onError
        //   在极端并发下读写 int[] 的可见性问题（虽然流式回调通常串行，
        //   但不同的 LangChain4j 实现不保证）。
        final AtomicIntegerArray usageHolder = new AtomicIntegerArray(4);

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
                    if (aiMessage != null && aiMessage.hasToolExecutionRequests()) {
                        toolRequests.addAll(aiMessage.toolExecutionRequests());
                    }

                    TokenUsage tu = completeResponse.tokenUsage();
                    if (tu != null) {
                        usageHolder.set(0, tu.inputTokenCount() == null ? 0 : tu.inputTokenCount());
                        usageHolder.set(1, tu.outputTokenCount() == null ? 0 : tu.outputTokenCount());
                        usageHolder.set(2, tu.totalTokenCount() == null ? 0 : tu.totalTokenCount());
                        usageHolder.set(3, extractCachedTokens(completeResponse));
                    }

                    future.complete(toLlmResponse(
                            fullContent.toString(), toolRequests,
                            usageHolder.get(0), usageHolder.get(1),
                            usageHolder.get(2), usageHolder.get(3)));
                } catch (Exception e) {
                    future.completeExceptionally(e);
                }
            }

            @Override
            public void onError(Throwable error) {
                log.error("StreamingChatResponseHandler onError, model={}", modelName, error);
                future.complete(LlmResponse.failed(
                        fullContent.toString(),
                        toLlmResponse("", toolRequests,
                                usageHolder.get(0), usageHolder.get(1),
                                usageHolder.get(2), usageHolder.get(3)),
                        error));
            }
        });

        try {
            return future.get(STREAM_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        } catch (TimeoutException e) {
            log.error("chatStream 超时 {}s, model={}", STREAM_TIMEOUT_SECONDS, modelName);
            return LlmResponse.failed(fullContent.toString(), e);
        } catch (Exception e) {
            log.error("chatStream 异常, model={}", modelName, e);
            return LlmResponse.failed(fullContent.toString(), e);
        }
    }

    /** 决定实际使用的模型：入参为空则用 Bean 默认模型 */
    private String resolveModel(String modelName) {
        if (modelName != null && !modelName.isBlank()) {
            return modelName.trim();
        }
        return (defaultModelName == null || defaultModelName.isBlank())
                ? LAST_RESORT_MODEL
                : defaultModelName.trim();
    }

    /**
     * 判断是否为"模型不存在 / 模型无权限"类错误。
     * <p>逐层检查 cause，覆盖不同 LangChain4j 版本抛出的异常类型。</p>
     */
    private boolean isModelUnavailable(Throwable e) {
        for (Throwable t = e; t != null; t = t.getCause()) {
            if (t instanceof dev.langchain4j.exception.ModelNotFoundException) {
                return true;
            }
            String msg = t.getMessage();
            if (msg == null) continue;
            String lower = msg.toLowerCase();
            if (lower.contains("model_not_found")
                    || lower.contains("model not exist")
                    || lower.contains("model does not exist")
                    || lower.contains("model_not_exist")
                    || lower.contains("invalid model")
                    || lower.contains("unknown model")
                    || lower.contains("model is not available")) {
                return true;
            }
        }
        return false;
    }

    /**
     * 从响应中提取 cachedTokens。
     *
     * <p>★ P0-3 修复：优先从 {@code aiMessage.attributes()} 读取
     * （由 {@link CachedTokenListener} 在同一次响应对象上写入，天然隔离）；
     * 取不到再从响应 metadata / aiMessage attributes 里用
     * {@link CachedTokenExtractor} 兜底。</p>
     */
    private int extractCachedTokens(ChatResponse response) {
        try {
            // 路径 1：listener 写入 aiMessage.attributes
            if (response.aiMessage() != null) {
                int fromAttr = CachedTokenListener.readFrom(response.aiMessage());
                if (fromAttr > 0) {
                    return fromAttr;
                }
            }
        } catch (Exception e) {
            log.debug("从 attributes 读取 cachedTokens 失败", e);
        }

        try {
            // 路径 2：metadata
            Object metadata = response.metadata();
            if (metadata != null) {
                int v = CachedTokenExtractor.extractFromNode(objectMapper.valueToTree(metadata));
                if (v > 0) return v;
            }
            // 路径 3：aiMessage attributes 反查（上游可能直接写了 prompt_tokens_details）
            if (response.aiMessage() != null && response.aiMessage().attributes() != null) {
                int v = CachedTokenExtractor.extractFromNode(
                        objectMapper.valueToTree(response.aiMessage().attributes()));
                if (v > 0) return v;
            }
        } catch (Exception e) {
            log.debug("提取 cachedTokens 失败", e);
        }
        return 0;
    }

    private LlmResponse toLlmResponse(String content,
                                      List<ToolExecutionRequest> requests,
                                      int inputTokens, int outputTokens, int totalTokens,
                                      int cachedTokens) {
        if (requests.isEmpty()) {
            return LlmResponse.of(content, List.of(),
                    inputTokens, outputTokens, totalTokens, cachedTokens);
        }
        List<LlmResponse.ToolCall> toolCalls = requests.stream().map(req -> {
            Map<String, Object> args = parseJson(req.arguments());
            return new LlmResponse.ToolCall(req.id(), req.name(), args);
        }).toList();
        return LlmResponse.of(content, toolCalls,
                inputTokens, outputTokens, totalTokens, cachedTokens);
    }

    // =================================================================
    //  chatSimple
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
    //  内部消息 → L4j 消息
    // =================================================================

    private List<ChatMessage> toChatMessages(List<Message> messages) {
        List<ChatMessage> result = new ArrayList<>();
        if (messages == null) return result;

        for (Message msg : messages) {
            if (msg == null) continue;
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
                    result.add(AiMessage.from(
                            msg.content() == null ? "" : msg.content(), requests));
                } else {
                    // ★ 修复：原实现未判空，content 为 null 时 NPE
                    result.add(AiMessage.from(msg.content() == null ? "" : msg.content()));
                }
            } else if (msg.role() == MessageRole.TOOL) {
                result.add(ToolExecutionResultMessage.from(msg.toolCallId(), null, msg.content()));
            }
        }
        return result;
    }

    // =================================================================
    //  工具描述转换
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
    //  Map → JSON / JSON → Map
    // =================================================================

    private String toJson(Map<String, Object> map) {
        if (map == null || map.isEmpty()) return "{}";
        try {
            return objectMapper.writeValueAsString(map);
        } catch (Exception e) {
            log.warn("toJson failed", e);
            return "{}";
        }
    }

    private Map<String, Object> parseJson(String json) {
        if (json == null || json.isBlank()) return Map.of();
        try {
            return objectMapper.readValue(json, new TypeReference<Map<String, Object>>() {});
        } catch (Exception e) {
            log.warn("parseJson failed: {}", json, e);
            return Map.of();
        }
    }
}
