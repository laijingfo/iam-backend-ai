package com.lenovo.ai.token.exception;

/**
 * @author laifo
 * @version 1.0
 * @date 2026-10-13 10:39
 * @project iam-backend-ai
 * @description
 */
public class TokenException extends RuntimeException {
    public TokenException(String msg) { super(msg); }
    public TokenException(String msg, Throwable cause) { super(msg, cause); }
}