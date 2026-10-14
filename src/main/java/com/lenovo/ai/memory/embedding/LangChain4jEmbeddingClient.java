package com.lenovo.ai.memory.embedding;

import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.output.Response;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 基于 LangChain4j EmbeddingModel 的 EmbeddingClient 实现。
 * <p>
 * 复用 LangChain4jConfig 中已配置的 EmbeddingModel Bean，避免重复创建 HTTP 连接。
 */
@Slf4j
@Component
public class LangChain4jEmbeddingClient implements EmbeddingClient {

    private final EmbeddingModel embeddingModel;

    public LangChain4jEmbeddingClient(EmbeddingModel embeddingModel) {
        this.embeddingModel = embeddingModel;
    }

    @Override
    public List<Double> embed(String text) {
        if (text == null || text.isBlank()) {
            return List.of();
        }
        try {
            Response<dev.langchain4j.data.embedding.Embedding> response = embeddingModel.embed(text);
            float[] vector = response.content().vector();
            List<Double> result = new ArrayList<>(vector.length);
            for (float v : vector) {
                result.add((double) v);
            }
            return result;
        } catch (Exception e) {
            log.error("embed 失败: text={}", text.length() > 50 ? text.substring(0, 50) + "..." : text, e);
            throw new RuntimeException("Embedding 调用失败", e);
        }
    }

    @Override
    public int dimension() {
        try {
            return embeddingModel.dimension();
        } catch (Exception e) {
            log.warn("获取 embedding dimension 失败，返回默认值 1024", e);
            return 1024;
        }
    }
}