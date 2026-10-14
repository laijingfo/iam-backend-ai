package com.lenovo.ai.service;

import com.lenovo.ai.infra.sse.AiChatSseEmitter;

/**
 * 单次请求的运行时状态。
 */
public class RequestState {
    public final String requestId;
    public final String runId;
    public final String memoryId;
    public final String lockToken;
    public final String lockKey;
    public final AiChatSseEmitter emitter;
    public final long createdAt;

    public RequestState(String requestId, String runId, String memoryId,
                        String lockToken, String lockKey, AiChatSseEmitter emitter) {
        this.requestId = requestId;
        this.runId = runId;
        this.memoryId = memoryId;
        this.lockToken = lockToken;
        this.lockKey = lockKey;
        this.emitter = emitter;
        this.createdAt = System.currentTimeMillis();
    }
}