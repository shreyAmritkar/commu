package com.engish.common;

import org.springframework.http.HttpStatus;

public class ApiException extends RuntimeException {
    private final HttpStatus status;
    private final String code;
    private final Integer retryAfterSeconds;

    public ApiException(HttpStatus status, String code, String message) {
        this(status, code, message, null);
    }

    public ApiException(HttpStatus status, String code, String message, Integer retryAfterSeconds) {
        super(message);
        this.status = status;
        this.code = code;
        this.retryAfterSeconds = retryAfterSeconds;
    }

    public HttpStatus getStatus() { return status; }
    public String getCode() { return code; }
    public Integer getRetryAfterSeconds() { return retryAfterSeconds; }
}