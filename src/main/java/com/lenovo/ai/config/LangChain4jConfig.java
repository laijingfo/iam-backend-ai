package com.lenovo.ai.config;

import dev.langchain4j.http.client.HttpClientBuilder;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.openai.OpenAiEmbeddingModel;
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

    @Value("${langchain4j.open-ai.embedding-model.model-name}")
    private String embeddingModelName;

    @Value("${langchain4j.http.timeout-seconds:60}")
    private int timeoutSeconds;

    @Value("${langchain4j.http.max-retries:3}")
    private int maxRetries;

    @Value("${langchain4j.http.log-requests:false}")
    private boolean logRequests;

    @Value("${langchain4j.http.log-responses:false}")
    private boolean logResponses;

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

    private HttpClientBuilder newHttpClientBuilder() {
        return new dev.langchain4j.http.client.jdk.JdkHttpClientBuilder();
    }
}