package com.lenovo.security.exception;

import org.springframework.http.HttpStatus;

public class AuthException extends RuntimeException {

    private Integer status = HttpStatus.UNAUTHORIZED.value();

    public AuthException(String msg) {
        super(msg);
    }
}
