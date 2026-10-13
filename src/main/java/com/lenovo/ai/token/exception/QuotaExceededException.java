package com.lenovo.ai.token.exception;

public class QuotaExceededException extends TokenException {
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