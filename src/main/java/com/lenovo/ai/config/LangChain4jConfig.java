package com.lenovo.ai.config;

import dev.langchain4j.http.client.HttpClientBuilder;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.openai.OpenAiChatModel;
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

@Slf4j
@Configuration
public class LangChain4jConfig {

    //TODO 临时配置，后续需要根据实际情况调整 解决Lenovo IPV6IPV4拒绝云百炼问题
    //-Djdk.tls.rejectClientInitiatedRenegotiation=false -Djdk.tls.client.protocols=TLSv1.2 -Dhttps.protocols=TLSv1.2
    static {
        // 把 Conscrypt 插到最高优先级，让 SSLContext.getDefault() 返回 Conscrypt 实现
        try {
            Security.insertProviderAt(Conscrypt.newProvider(), 1);
            System.out.println(">>> Conscrypt installed. Default SSLContext provider = "
                    + SSLContext.getDefault().getProvider().getName());
        } catch (Exception e) {
            System.err.println(">>> Failed to install Conscrypt: " + e);
            e.printStackTrace();
        }
    }

    @Value("${langchain4j.open-ai.base-url}")
    private String baseUrl;

    @Value("${langchain4j.open-ai.api-key}")
    private String apiKey;

    @Value("${langchain4j.open-ai.chat-model.model-name}")
    private String chatModelName;

    @Value("${langchain4j.open-ai.embedding-model.model-name}")
    private String embeddingModelName;

    @Value("${langchain4j.http.timeout-seconds:60}")
    private int timeoutSeconds;

    @Value("${langchain4j.http.max-retries:3}")
    private int maxRetries;

    @Value("${langchain4j.http.log-requests:true}")
    private boolean logRequests;

    @Value("${langchain4j.http.log-responses:true}")
    private boolean logResponses;

    @Bean
    public ChatModel chatModel() {
        return OpenAiChatModel.builder()
                .httpClientBuilder(newHttpClientBuilder())
                .baseUrl(baseUrl).apiKey(apiKey).modelName(chatModelName)
                .temperature(0.7)
                .timeout(Duration.ofSeconds(timeoutSeconds))
                .maxRetries(maxRetries)
                .logRequests(logRequests).logResponses(logResponses)
                .build();
    }

    @Bean
    public StreamingChatModel streamingChatModel() {
        return OpenAiStreamingChatModel.builder()
                .httpClientBuilder(newHttpClientBuilder())
                .baseUrl(baseUrl).apiKey(apiKey).modelName(chatModelName)
                .temperature(0.7)
                .timeout(Duration.ofSeconds(timeoutSeconds))
                .logRequests(logRequests).logResponses(logResponses)
                .build();
    }

    @Bean
    public EmbeddingModel embeddingModel() {
        return OpenAiEmbeddingModel.builder()
                .httpClientBuilder(newHttpClientBuilder())
                .baseUrl(baseUrl).apiKey(apiKey).modelName(embeddingModelName)
                .timeout(Duration.ofSeconds(timeoutSeconds))
                .maxRetries(maxRetries)
                .logRequests(logRequests).logResponses(logResponses)
                .build();
    }

    /**
     * 用 JDK HttpClient，让 SSLContext.getDefault() 走 Conscrypt。
     */
    private HttpClientBuilder newHttpClientBuilder() {
        return new dev.langchain4j.http.client.jdk.JdkHttpClientBuilder();
    }
}