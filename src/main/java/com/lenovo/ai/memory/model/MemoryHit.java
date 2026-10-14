package com.lenovo.ai.memory.model;

/**
 * 向量检索命中结果。
 *
 * @author laifo
 * @date 2026-10-12
 */
public record MemoryHit(MemoryItem item, double score) {}