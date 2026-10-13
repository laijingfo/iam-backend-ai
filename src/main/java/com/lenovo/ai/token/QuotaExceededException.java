package com.lenovo.ai.token;

/**
 * @author laifo
 * @version 1.0
 * @date 2026-10-12 17:18
 * @project iam-backend-ai
 * @description
 */
public class QuotaExceededException extends RuntimeException {
    private final String userId;
    private final String reason;

    public QuotaExceededException(String userId, String reason) {
        super("Token 配额超限: userId=" + userId + ", reason=" + reason);
        this.userId = userId;
        this.reason = reason;
    }

    public String getUserId() { return userId; }
    public String getReason() { return reason; }
}
