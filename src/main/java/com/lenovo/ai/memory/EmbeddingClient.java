package com.lenovo.ai.memory;

import java.util.List;

/**
 * Embedding 客户端接口
 *
 * @author laifo
 * @date 2026-09-30
 */
public interface EmbeddingClient {
    List<Double> embed(String text);
    int dimension();
}