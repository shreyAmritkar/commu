package com.engish.common;

import java.time.Instant;
import java.util.Map;

public record ErrorResponse(String code, String message, Instant timestamp, Map<String, String> fieldErrors) {
    public static ErrorResponse of(String code, String message) {
        return new ErrorResponse(code, message, Instant.now(), null);
    }
}
