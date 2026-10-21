package com.engish.redis;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "engish.redis")
public record RedisSettings(String host, int port, String password, boolean ssl, int database,
                            int connectTimeoutMs, int commandTimeoutMs, String keyPrefix) {
    public RedisSettings {
        if (host == null || host.isBlank()) host = "localhost";
        if (port <= 0) port = 6379;
        if (connectTimeoutMs <= 0) connectTimeoutMs = 1000;
        if (commandTimeoutMs <= 0) commandTimeoutMs = 500;
        if (keyPrefix == null || keyPrefix.isBlank()) keyPrefix = "engish";
    }
}