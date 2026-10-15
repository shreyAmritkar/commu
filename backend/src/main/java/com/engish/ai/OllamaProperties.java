package com.engish.ai;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "engish.ollama")
public record OllamaProperties(String baseUrl, String model, int timeoutSeconds, double temperature) {
}
