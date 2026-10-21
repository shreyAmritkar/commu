package com.engish.ai;

import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.net.SocketTimeoutException;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

@Component
public class OllamaClient implements LlmClient {
    private static final Logger log = LoggerFactory.getLogger(OllamaClient.class);

    private final OllamaProperties props;
    private final RestClient rest;
    private final Semaphore slots;                       // per-instance cap
    private final GlobalConcurrencyLimiter globalLimiter; // cluster-wide cap (Redis)
    private final Duration queueWait;                    // one budget shared by both waits
    private final int maxOutputTokens;

    public OllamaClient(OllamaProperties props, RestClient.Builder builder, GlobalConcurrencyLimiter globalLimiter) {
        if (props.maxConcurrent() < 1) throw new IllegalStateException("OLLAMA_MAX_CONCURRENT must be at least 1");
        if (props.queueWaitSeconds() < 1) throw new IllegalStateException("OLLAMA_QUEUE_WAIT_SECONDS must be at least 1");
        if (props.timeoutSeconds() < 5) throw new IllegalStateException("OLLAMA_TIMEOUT_SECONDS must be at least 5");
        if (props.maxOutputTokens() < 1) throw new IllegalStateException("OLLAMA_MAX_OUTPUT_TOKENS must be at least 1");
        this.props = props;
        this.globalLimiter = globalLimiter;
        this.slots = new Semaphore(props.maxConcurrent(), true);
        this.queueWait = Duration.ofSeconds(props.queueWaitSeconds());
        this.maxOutputTokens = props.maxOutputTokens();
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(5));
        factory.setReadTimeout(Duration.ofSeconds(props.timeoutSeconds()));
        this.rest = builder.baseUrl(props.baseUrl()).requestFactory(factory).build();
    }

    @Override
    public String complete(String systemPrompt, String userPrompt, boolean jsonMode) {
        if (props.model() == null || props.model().isBlank()) throw AiException.notConfigured();

        long deadline = System.nanoTime() + queueWait.toNanos();

        // 1) per-instance permit first, so a request never holds a global permit while queueing locally
        boolean local;
        try {
            local = slots.tryAcquire(queueWait.toNanos(), TimeUnit.NANOSECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw AiException.busy();
        }
        if (!local) throw AiException.busy();
        try {
            // 2) cluster-wide permit, using whatever is left of the same wait budget
            Duration remaining = Duration.ofNanos(Math.max(0, deadline - System.nanoTime()));
            try (GlobalConcurrencyLimiter.Permit ignored = globalLimiter.acquire(remaining)) {
                return call(systemPrompt, userPrompt, jsonMode);
            }
        } finally {
            slots.release();
        }
    }

    private String call(String systemPrompt, String userPrompt, boolean jsonMode) {
        Map<String, Object> options = new LinkedHashMap<>();
        options.put("temperature", props.temperature());
        options.put("num_predict", maxOutputTokens);

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", props.model());
        body.put("stream", false);
        body.put("messages", List.of(
                Map.of("role", "system", "content", systemPrompt),
                Map.of("role", "user", "content", userPrompt)));
        if (jsonMode) body.put("format", "json");
        body.put("options", options);

        try {
            JsonNode res = rest.post().uri("/api/chat").contentType(MediaType.APPLICATION_JSON).body(body)
                    .retrieve().body(JsonNode.class);
            String content = res == null ? null : res.path("message").path("content").asText(null);
            if (content == null || content.isBlank()) throw AiException.badResponse();
            return content;
        } catch (HttpClientErrorException.NotFound e) {
            throw AiException.unavailable("Ollama does not have the model '" + props.model() + "'. Run: ollama pull " + props.model());
        } catch (RestClientResponseException e) {
            log.warn("Ollama returned HTTP {}", e.getStatusCode().value());
            throw AiException.unavailable("Ollama returned an error (HTTP " + e.getStatusCode().value() + "). Check the Ollama logs.");
        } catch (ResourceAccessException e) {
            if (e.getCause() instanceof SocketTimeoutException) throw AiException.timeout();
            log.warn("Cannot reach Ollama at {}", props.baseUrl());
            throw AiException.unavailable("Cannot reach Ollama at " + props.baseUrl() + ". Is it running? Start it with: ollama serve");
        }
    }
}