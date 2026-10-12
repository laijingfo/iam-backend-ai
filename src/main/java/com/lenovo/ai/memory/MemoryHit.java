package com.lenovo.ai.memory;

import java.util.List;
/**
 * @author laifo
 * @version 1.0
 * @date 2026-10-12 15:01
 * @project iam-backend-ai
 * @description
 */
public record MemoryHit(MemoryItem item, double score) {}
