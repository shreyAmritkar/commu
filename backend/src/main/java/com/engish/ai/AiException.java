package com.engish.ai;

import com.engish.common.ApiException;
import org.springframework.http.HttpStatus;

public class AiException extends ApiException {
    public AiException(HttpStatus status, String code, String message) {
        super(status, code, message);
    }

    public AiException(HttpStatus status, String code, String message, int retryAfterSeconds) {
        super(status, code, message, retryAfterSeconds);
    }

    public static AiException notConfigured() {
        return new AiException(HttpStatus.SERVICE_UNAVAILABLE, "AI_NOT_CONFIGURED", "No Ollama model is configured. Set OLLAMA_MODEL in backend/.env.");
    }

    public static AiException unavailable(String detail) {
        return new AiException(HttpStatus.SERVICE_UNAVAILABLE, "AI_UNAVAILABLE", detail);
    }

    public static AiException timeout() {
        return new AiException(HttpStatus.GATEWAY_TIMEOUT, "AI_TIMEOUT", "The model took too long to respond. Try again, or raise OLLAMA_TIMEOUT_SECONDS / use a smaller model.");
    }

    public static AiException badResponse() {
        return new AiException(HttpStatus.BAD_GATEWAY, "AI_BAD_RESPONSE", "The model returned an unusable response. Please try again.");
    }

    public static AiException busy() {
        return new AiException(HttpStatus.SERVICE_UNAVAILABLE, "AI_BUSY", "The AI is busy with other requests. Please try again in a moment.", 5);
    }

    public static AiException coordinationUnavailable() {
        return new AiException(HttpStatus.SERVICE_UNAVAILABLE, "AI_COORDINATION_UNAVAILABLE", "The AI service could not be coordinated right now. Please try again shortly.", 10);
    }
}