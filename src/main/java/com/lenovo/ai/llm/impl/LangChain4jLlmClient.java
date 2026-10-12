package com.lenovo.ai.llm.impl;                                                 // 包声明

import com.fasterxml.jackson.core.type.TypeReference;                           // JSON 类型引用
import com.fasterxml.jackson.databind.ObjectMapper;                             // JSON 序列化
import com.lenovo.ai.infra.sse.AiChatSseEmitter;                                // SSE 发射器
import com.lenovo.ai.llm.LlmClient;                                             // LLM 客户端接口
import com.lenovo.ai.llm.LlmResponse;                                           // LLM 响应
import com.lenovo.ai.llm.Message;                                               // 消息模型
import com.lenovo.ai.llm.enums.MessageRole;                                     // 消息角色枚举
import com.lenovo.ai.tools.ToolSpec;                                            // 工具描述
import dev.langchain4j.agent.tool.ToolExecutionRequest;                         // L4j 工具执行请求
import dev.langchain4j.agent.tool.ToolSpecification;                            // L4j 工具描述
import dev.langchain4j.data.message.AiMessage;                                  // L4j AI 消息
import dev.langchain4j.data.message.ChatMessage;                                // L4j 聊天消息
import dev.langchain4j.data.message.SystemMessage;                              // L4j 系统消息
import dev.langchain4j.data.message.ToolExecutionResultMessage;                 // L4j 工具结果消息
import dev.langchain4j.data.message.UserMessage;                                // L4j 用户消息
import dev.langchain4j.model.chat.ChatModel;                                    // L4j 非流式模型
import dev.langchain4j.model.chat.StreamingChatModel;                           // L4j 流式模型
import dev.langchain4j.model.chat.request.ChatRequest;                          // L4j 聊天请求
import dev.langchain4j.model.chat.request.json.JsonObjectSchema;                // L4j JSON Schema
import dev.langchain4j.exception.InternalServerException;                       // L4j 内部服务器异常
import dev.langchain4j.model.chat.response.ChatResponse;                        // L4j 聊天响应
import dev.langchain4j.model.chat.response.StreamingChatResponseHandler;        // L4j 流式回调
import lombok.extern.slf4j.Slf4j;                                               // 日志
import org.springframework.stereotype.Component;                                // Spring 组件

import java.util.ArrayList;                                                     // 数组
import java.util.HashMap;                                                       // HashMap
import java.util.List;                                                          // 列表
import java.util.Map;                                                           // Map
import java.util.concurrent.CompletableFuture;                                  // 异步任务
import java.util.concurrent.TimeUnit;                                           // 时间单位

/**                                                                             // Javadoc
 * 基于 LangChain4j 的 LlmClient 实现：真流式输出 + 工具调用。                       // 说明
 */                                                                             // 结束
@Slf4j                                                                          // 日志
@Component                                                                      // 注册 Bean
public class LangChain4jLlmClient implements LlmClient {                        // 实现类

    private static final int STREAM_TIMEOUT_SECONDS = 120;                      // 流式调用超时（秒）
    private static final int CHAT_SIMPLE_MAX_RETRIES = 3;                       // chatSimple 空 choices 最大重试次数
    private static final long CHAT_SIMPLE_RETRY_BACKOFF_MS = 500;               // chatSimple 重试初始间隔（毫秒）
    private static final int LOG_RESP_PREVIEW_LEN = 500;                        // ✅ chatSimple 返回日志截断长度
    private static final ObjectMapper MAPPER = new ObjectMapper();              // 共享 JSON 序列化器

    private final StreamingChatModel streamingModel;                            // 流式模型
    private final ChatModel chatModel;                                          // 非流式模型

    /**                                                                         // Javadoc
     * 构造注入两个模型。                                                         // 说明
     */                                                                         // 结束
    public LangChain4jLlmClient(StreamingChatModel streamingModel, ChatModel chatModel) { // 构造器
        this.streamingModel = streamingModel;                                   // 赋值
        this.chatModel = chatModel;                                             // 赋值
    }

    @Override                                                                   // 实现接口
    public LlmResponse chatStream(List<Message> messages, List<ToolSpec> tools, AiChatSseEmitter emitter) { // 流式调用
        List<ChatMessage> chatMessages = toChatMessages(messages);              // 1. 内部消息 → L4j 消息
        List<ToolSpecification> toolSpecs = toToolSpecifications(tools);        // 2. 内部工具 → L4j 工具描述

        ChatRequest.Builder requestBuilder = ChatRequest.builder().messages(chatMessages); // 3. 构建请求
        if (!toolSpecs.isEmpty()) {                                             // 4. 若有工具
            requestBuilder.toolSpecifications(toolSpecs);                       //    加入工具描述
        }
        ChatRequest request = requestBuilder.build();                           // 5. 构建完成

        CompletableFuture<LlmResponse> future = new CompletableFuture<>();      // 6. 异步结果容器
        StringBuilder fullContent = new StringBuilder();                        // 7. 累积完整内容
        List<ToolExecutionRequest> toolRequests = new ArrayList<>();            // 8. 累积工具请求

        streamingModel.chat(request, new StreamingChatResponseHandler() {       // 9. 发起流式调用
            @Override                                                           // 回调：增量
            public void onPartialResponse(String partialResponse) {             // 增量片段
                if (partialResponse != null && !partialResponse.isEmpty()) {    //    非空
                    fullContent.append(partialResponse);                        //    累积
                    emitter.send("delta", Map.of("text", partialResponse));     //    推送 delta 事件
                }
            }

            @Override                                                           // 回调：完成
            public void onCompleteResponse(ChatResponse completeResponse) {     // 完成响应
                try {                                                           //    捕获异常
                    AiMessage aiMessage = completeResponse.aiMessage();         //    取 AI 消息
                    String content = aiMessage.text();                          //    取文本
                    if (content != null && !content.isEmpty()) {                //    非空
                        fullContent.setLength(0);                               //    清空累积
                        fullContent.append(content);                            //    写入完整内容
                        emitter.send("done", Map.of("answer", content));        //    推送 done 事件
                    }
                    if (aiMessage.hasToolExecutionRequests()) {                 //    有工具请求
                        toolRequests.addAll(aiMessage.toolExecutionRequests()); //    收集
                        emitter.send("tool", Map.of("name", "tool_execution")); //    推送 tool 事件
                    }
                    future.complete(toLlmResponse(fullContent.toString(), toolRequests)); // 完成 future
                } catch (Exception e) {                                         //    异常
                    future.completeExceptionally(e);                            //    异常完成
                }
            }

            @Override                                                           // 回调：错误
            public void onError(Throwable error) {                              // 错误
                log.error("StreamingChatResponseHandler onError", error);       //    日志
                emitter.send("error", Map.of("message", error.getMessage()));   //    推送 error
                future.completeExceptionally(error);                            //    异常完成
            }
        });

        try {                                                                   // 10. 等待结果
            return future.get(STREAM_TIMEOUT_SECONDS, TimeUnit.SECONDS);        //    带超时获取
        } catch (Exception e) {                                                 //    超时/异常
            log.error("chatStream 超时或异常", e);                                //    日志
            return new LlmResponse("", List.of());                              //    返回空响应
        }
    }

    @Override                                                                   // 实现接口
    public String chatSimple(String prompt) {                                   // 非流式简单调用
        if (prompt == null || prompt.isBlank()) return "";                      // 1. 空入参返回空
        int maxAttempts = CHAT_SIMPLE_MAX_RETRIES + 1;                          // 2. 总尝试次数
        InternalServerException lastException = null;                           //    最后一次内部异常
        for (int attempt = 1; attempt <= maxAttempts; attempt++) {              // 3. 重试循环
            try {                                                               //    try
                ChatMessage userMsg = UserMessage.from(prompt);                 //       构造用户消息
                ChatRequest request = ChatRequest.builder()                     //       构造请求
                        .messages(List.of(userMsg))                             //       消息列表
                        .build();                                               //       构建
                ChatResponse response = chatModel.chat(request);                //       调用模型
                AiMessage aiMessage = response.aiMessage();                     //       取 AI 消息

                // ✅【新增】防御 aiMessage 为 null（上游异常时可能返回 null）
                if (aiMessage == null) {                                        //       aiMessage 为空
                    log.warn(">>> chatSimple aiMessage 为 null，attempt={}/{}, promptLen={}",
                            attempt, maxAttempts, prompt.length());
                    if (attempt < maxAttempts) {                                //          还能重试
                        long sleepMs = CHAT_SIMPLE_RETRY_BACKOFF_MS * (1L << (attempt - 1));
                        try { Thread.sleep(sleepMs); } catch (InterruptedException ie) {
                            Thread.currentThread().interrupt();
                            break;
                        }
                        continue;                                               //          继续下一次
                    }
                    return "";                                                  //          最后一次，返回空
                }

                String text = aiMessage.text() != null ? aiMessage.text() : ""; //      获取文本

                // ✅【新增】打印 LLM 返回原文（截断），排查"返回 []"或"返回脏数据"
                log.info(">>> chatSimple 返回, promptLen={}, respLen={}, resp={}",
                        prompt.length(), text.length(),
                        text.length() <= LOG_RESP_PREVIEW_LEN
                                ? text
                                : text.substring(0, LOG_RESP_PREVIEW_LEN) + "...(truncated)");

                if (attempt > 1) {                                              //       重试后成功
                    log.info(">>> chatSimple 重试成功，第 {}/{} 次", attempt, maxAttempts);
                }
                return text;                                                    //       返回文本
            } catch (InternalServerException e) {                               //    上游模型异常（含空 choices）
                lastException = e;                                              //      记录最后一次异常
                boolean willRetry = attempt < maxAttempts;                      //      是否继续重试
                // ✅【修复】异常作为最后一个参数且不占 {}，才会打印完整堆栈
                log.warn(">>> chatSimple 调用失败（可能上游返回空 choices），尝试 {}/{}，prompt 长度={}，是否继续重试={}",
                        attempt, maxAttempts, prompt.length(), willRetry, e);
                if (!willRetry) {                                               //      最后一次失败，跳出
                    break;
                }
                long sleepMs = CHAT_SIMPLE_RETRY_BACKOFF_MS * (1L << (attempt - 1)); // 指数退避：500, 1000, 2000
                try {                                                           //      短暂等待再重试
                    Thread.sleep(sleepMs);
                } catch (InterruptedException ie) {                             //      线程被中断
                    Thread.currentThread().interrupt();                         //         恢复中断标志
                    log.warn(">>> chatSimple 重试等待被中断");
                    break;                                                      //         中断退出循环
                }
            } catch (Exception e) {                                             //    其它异常不重试
                // ✅【修复】异常放最后且不占 {}，堆栈才能打出来
                log.error("chatSimple 异常, promptLen={}", prompt.length(), e);
                return "";                                                      //    返回空
            }
        }
        // ✅【修复】lastException 作为最后一个参数，堆栈能打出来
        log.error(">>> chatSimple 在 {} 次尝试后仍失败，返回空", maxAttempts, lastException);
        return "";                                                              // 返回空，避免影响上层
    }

    /**                                                                         // Javadoc
     * 内部消息列表 → L4j ChatMessage 列表。                                       // 说明
     */                                                                         // 结束
    private List<ChatMessage> toChatMessages(List<Message> messages) {          // 转换方法
        List<ChatMessage> result = new ArrayList<>();                           // 结果列表
        for (Message msg : messages) {                                          // 遍历
            if (msg.role() == MessageRole.SYSTEM) {                             // SYSTEM
                result.add(new SystemMessage(msg.content()));                   //    系统消息
            } else if (msg.role() == MessageRole.USER) {                        // USER
                result.add(UserMessage.from(msg.content()));                    //    用户消息
            } else if (msg.role() == MessageRole.ASSISTANT) {                   // ASSISTANT
                if (msg.toolCalls() != null && !msg.toolCalls().isEmpty()) {    //    有工具调用
                    List<ToolExecutionRequest> requests = msg.toolCalls().stream() // 映射
                            .map(ref -> ToolExecutionRequest.builder()          //    构建
                                    .id(ref.id())                               //    ID
                                    .name(ref.name())                           //    名称
                                    .arguments(toJson(ref.args()))              //    参数 JSON
                                    .build())                                   //    构建
                            .toList();                                          //    收集
                    result.add(AiMessage.from(requests));                       //    AI 消息（含工具调用）
                } else {                                                        //    无工具调用
                    result.add(AiMessage.from(msg.content()));                  //    AI 文本消息
                }
            } else if (msg.role() == MessageRole.TOOL) {                        // TOOL
                result.add(ToolExecutionResultMessage.from(msg.toolCallId(), null, msg.content())); // 工具结果
            }
        }
        return result;                                                          // 返回
    }

    /**                                                                         // Javadoc
     * 内部工具描述 → L4j ToolSpecification。                                      // 说明
     */                                                                         // 结束
    @SuppressWarnings("unchecked")                                              // 抑制未检查警告
    private List<ToolSpecification> toToolSpecifications(List<ToolSpec> tools) {// 转换
        if (tools == null || tools.isEmpty()) return List.of();                 // 空返回
        return tools.stream().map(spec -> {                                     // 流式映射
            JsonObjectSchema.Builder schemaBuilder = JsonObjectSchema.builder();// 构建 Schema
            Map<String, Object> schema = spec.parameterSchema();                // 取参数 Schema
            if (schema != null) {                                               // 非空
                Object propsObj = schema.get("properties");                     // 取 properties
                if (propsObj instanceof Map<?, ?> properties) {                 // 类型判断
                    List<String> requiredList = new ArrayList<>();              // required 列表
                    Object reqObj = schema.get("required");                     // 取 required
                    if (reqObj instanceof List<?> reqList) {                    // 类型判断
                        for (Object r : reqList) {                              // 遍历
                            if (r != null) requiredList.add(String.valueOf(r)); // 添加
                        }
                    }
                    properties.forEach((nameObj, paramObj) -> {                 // 遍历属性
                        String name = String.valueOf(nameObj);                  // 属性名
                        if (paramObj instanceof Map<?, ?> rawMap) {             // 类型判断
                            Map<String, Object> paramMap = (Map<String, Object>) rawMap; // 强转
                            String type = String.valueOf(paramMap.getOrDefault("type", "string")); // 类型
                            String desc = String.valueOf(paramMap.getOrDefault("description", "")); // 描述
                            switch (type) {                                     // 类型分支
                                case "integer" -> schemaBuilder.addIntegerProperty(name, desc); // 整数
                                case "number" -> schemaBuilder.addNumberProperty(name, desc);   // 数字
                                case "boolean" -> schemaBuilder.addBooleanProperty(name, desc); // 布尔
                                default -> schemaBuilder.addStringProperty(name, desc);         // 字符串
                            }
                        }
                    });
                    if (!requiredList.isEmpty()) schemaBuilder.required(requiredList); // 设置 required
                }
            }
            return ToolSpecification.builder()                                  // 构建工具描述
                    .name(spec.name())                                          // 名称
                    .description(spec.description())                            // 描述
                    .parameters(schemaBuilder.build())                          // 参数 Schema
                    .build();                                                   // 构建
        }).toList();                                                            // 收集
    }

    /**                                                                         // Javadoc
     * 内部构造 LlmResponse。                                                     // 说明
     */                                                                         // 结束
    private LlmResponse toLlmResponse(String content, List<ToolExecutionRequest> requests) { // 方法
        if (requests.isEmpty()) {                                               // 无工具调用
            return new LlmResponse(content, List.of());                         //    返回纯文本
        }
        List<LlmResponse.ToolCall> toolCalls = requests.stream().map(req -> {   // 映射工具调用
            Map<String, Object> args = parseJson(req.arguments());              // 解析参数
            return new LlmResponse.ToolCall(req.id(), req.name(), args);        // 构造
        }).toList();                                                            // 收集
        return new LlmResponse(content, toolCalls);                             // 返回
    }

    /**                                                                         // Javadoc
     * Map → JSON 字符串。                                                       // 说明
     */                                                                         // 结束
    private String toJson(Map<String, Object> map) {                            // 方法
        if (map == null || map.isEmpty()) return "{}";                          // 空返回 {}
        try {                                                                   // try
            return MAPPER.writeValueAsString(map);                              // 序列化
        } catch (Exception e) {                                                 // 异常
            log.warn("toJson failed", e);                                       //    日志
            return "{}";                                                        //    返回 {}
        }
    }

    /**                                                                         // Javadoc
     * JSON 字符串 → Map。                                                       // 说明
     */                                                                         // 结束
    private Map<String, Object> parseJson(String json) {                        // 方法
        if (json == null || json.isBlank()) return Map.of();                    // 空返回
        try {                                                                   // try
            return MAPPER.readValue(json, new TypeReference<Map<String, Object>>() {}); // 反序列化
        } catch (Exception e) {                                                 // 异常
            log.warn("parseJson failed: {}", json, e);                          //    日志
            return Map.of();                                                    //    返回空
        }
    }
}