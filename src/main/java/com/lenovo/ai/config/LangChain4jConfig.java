package com.lenovo.ai.config;

import com.lenovo.ai.llm.cache.CachedTokenListener;
import dev.langchain4j.http.client.HttpClientBuilder;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.openai.OpenAiChatModel;
import dev.langchain4j.model.openai.OpenAiChatRequestParameters;
import dev.langchain4j.model.openai.OpenAiEmbeddingModel;
import dev.langchain4j.model.openai.OpenAiStreamingChatModel;
import lombok.extern.slf4j.Slf4j;
import org.conscrypt.Conscrypt;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import javax.net.ssl.SSLContext;
import java.security.Security;
import java.time.Duration;
import java.util.List;
import java.util.Map;

/**
 * LangChain4j 模型 Bean 统一配置：ChatModel / StreamingChatModel / EmbeddingModel。
 * <p>
 * 所有模型共享同一个 JDK HttpClient（通过 Conscrypt 提供 TLS 1.3 支持），
 * 避免重复创建连接池。
 */
@Slf4j
@Configuration
public class LangChain4jConfig {

    static {
        try {
            Security.insertProviderAt(Conscrypt.newProvider(), 1);
            log.info(">>> Conscrypt installed. Default SSLContext provider = {}",
                    SSLContext.getDefault().getProvider().getName());
        } catch (Exception e) {
            log.error(">>> Failed to install Conscrypt", e);
        }
    }

    @Value("${langchain4j.open-ai.base-url}")
    private String baseUrl;

    @Value("${langchain4j.open-ai.api-key}")
    private String apiKey;

    @Value("${langchain4j.open-ai.chat-model.model-name:qwen3-max}")
    private String chatModelName;

    @Value("${langchain4j.open-ai.chat-model.temperature:0.3}")
    private double chatTemperature;

    @Value("${langchain4j.open-ai.chat-model.enable-thinking:false}")
    private boolean chatEnableThinking;

    @Value("${langchain4j.open-ai.streaming-chat-model.model-name:qwen3-max}")
    private String streamingChatModelName;

    @Value("${langchain4j.open-ai.streaming-chat-model.temperature:0.7}")
    private double temperature;

    @Value("${langchain4j.open-ai.embedding-model.model-name:text-embedding-v3}")
    private String embeddingModelName;

    @Value("${langchain4j.http.timeout-seconds:60}")
    private int timeoutSeconds;

    @Value("${langchain4j.http.max-retries:3}")
    private int maxRetries;

    @Value("${langchain4j.http.log-requests:false}")
    private boolean logRequests;

    @Value("${langchain4j.http.log-responses:false}")
    private boolean logResponses;
    @Value("${langchain4j.open-ai.embedding-model.dimension:1024}")
    private int embeddingDimension;
    /**
     * 非流式 ChatModel：用于 MemoryExtractor 等需要完整响应的场景。
     * <p>挂载 CachedTokenListener 以捕获 cachedTokens（LangChain4j 默认不暴露）。</p>
     */
    @Bean
    public ChatModel chatModel(CachedTokenListener cachedTokenListener) {
        OpenAiChatRequestParameters requestParameters = OpenAiChatRequestParameters.builder()
                .modelName(chatModelName)
                .temperature(chatTemperature)
                .customParameters(Map.of("enable_thinking", chatEnableThinking))
                .build();
        return OpenAiChatModel.builder()
                .httpClientBuilder(newHttpClientBuilder())
                .baseUrl(baseUrl).apiKey(apiKey).modelName(chatModelName)
                .defaultRequestParameters(requestParameters)
                .timeout(Duration.ofSeconds(timeoutSeconds))
                .maxRetries(maxRetries)
                .logRequests(logRequests).logResponses(logResponses)
                .listeners(List.of(cachedTokenListener))    // ★ 挂监听器
                .build();
    }

    /**
     * 流式 StreamingChatModel：用于 AgentLoop 的实时流式输出。
     * <p>注意：流式模型不支持 maxRetries（与 yml 注释一致）。</p>
     * <p>挂载 CachedTokenListener 以捕获 cachedTokens。</p>
     */
    @Bean
    public StreamingChatModel streamingChatModel(CachedTokenListener cachedTokenListener) {
        return OpenAiStreamingChatModel.builder()
                .httpClientBuilder(newHttpClientBuilder())
                .baseUrl(baseUrl).apiKey(apiKey).modelName(streamingChatModelName)
                .temperature(temperature)
                .timeout(Duration.ofSeconds(timeoutSeconds))
                .logRequests(logRequests).logResponses(logResponses)
                .listeners(List.of(cachedTokenListener))    // ★ 挂监听器
                .build();
    }

    /**
     * EmbeddingModel：用于 KnowledgeRouter 和 MemoryExtractor 的向量检索。
     */
    @Bean
    public EmbeddingModel embeddingModel() {
        return OpenAiEmbeddingModel.builder()
                .httpClientBuilder(newHttpClientBuilder())
                .baseUrl(baseUrl).apiKey(apiKey).modelName(embeddingModelName)
                .dimensions(embeddingDimension)   // ★ P1 修复：显式传维度
                .timeout(Duration.ofSeconds(timeoutSeconds))
                .maxRetries(maxRetries)
                .logRequests(logRequests).logResponses(logResponses)
                .build();
    }

    private HttpClientBuilder newHttpClientBuilder() {
        return new dev.langchain4j.http.client.jdk.JdkHttpClientBuilder();
    }
}