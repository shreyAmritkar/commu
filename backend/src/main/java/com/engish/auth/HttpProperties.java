package com.engish.auth;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

@ConfigurationProperties(prefix = "engish.http")
public record HttpProperties(List<String> trustedProxies) {
    public HttpProperties {
        trustedProxies = trustedProxies == null ? List.of() : List.copyOf(trustedProxies);
    }
}