package com.lenovo.ai.memory;

import java.util.List;

public interface MemoryVectorStore {

    /** 写入：namespace 用 userId，保证隔离 */
    void add(String userId, MemoryItem item, List<Double> embedding);

    /** 检索：只搜该 userId 下的记忆 */
    List<MemoryHit> search(String userId, List<Double> queryEmbedding, int topK);

    /** 更新（命中累加） */
    void update(MemoryItem item);
}