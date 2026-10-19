package com.engish.auth;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "engish.security")
public record SecurityProperties(String jwtSecret, int jwtExpiryMinutes) {
}