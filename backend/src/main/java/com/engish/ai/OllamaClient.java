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

@Component
public class OllamaClient implements LlmClient {
    private static final Logger log = LoggerFactory.getLogger(OllamaClient.class);

    private final OllamaProperties props;
    private final RestClient rest;

    public OllamaClient(OllamaProperties props, RestClient.Builder builder) {
        this.props = props;
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(5));
        factory.setReadTimeout(Duration.ofSeconds(Math.max(5, props.timeoutSeconds())));
        this.rest = builder.baseUrl(props.baseUrl()).requestFactory(factory).build();
    }

    @Override
    public String complete(String systemPrompt, String userPrompt, boolean jsonMode) {
        if (props.model() == null || props.model().isBlank()) throw AiException.notConfigured();

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", props.model());
        body.put("stream", false);
        body.put("messages", List.of(
                Map.of("role", "system", "content", systemPrompt),
                Map.of("role", "user", "content", userPrompt)));
        if (jsonMode) body.put("format", "json");
        body.put("options", Map.of("temperature", props.temperature()));

        try {
            JsonNode res = rest.post().uri("/api/chat").contentType(MediaType.APPLICATION_JSON).body(body)
                    .retrieve().body(JsonNode.class);
            String content = res == null ? null : res.path("message").path("content").asText(null);
            if (content == null || content.isBlank()) throw AiException.badResponse();
            return content;
        } catch (HttpClientErrorException.NotFound e) {
            throw AiException.unavailable("Ollama does not have the model '" + props.model() + "'. Run: ollama pull " + props.model());
        } catch (RestClientResponseException e) {
            log.warn("Ollama returned HTTP {}: {}", e.getStatusCode().value(), e.getResponseBodyAsString());
            throw AiException.unavailable("Ollama returned an error (HTTP " + e.getStatusCode().value() + "). Check the Ollama logs.");
        } catch (ResourceAccessException e) {
            if (e.getCause() instanceof SocketTimeoutException) throw AiException.timeout();
            log.warn("Cannot reach Ollama at {}: {}", props.baseUrl(), e.getMessage());
            throw AiException.unavailable("Cannot reach Ollama at " + props.baseUrl() + ". Is it running? Start it with: ollama serve");
        }
    }
}
