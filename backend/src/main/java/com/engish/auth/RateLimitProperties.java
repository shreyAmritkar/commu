package com.engish.auth;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "engish.rate-limit")
public record RateLimitProperties(boolean failOpen, int windowSeconds, int authPerIp, int createPerUser,
                                  int turnPerUser, int completePerUser, int alternativePerUser) {
    public RateLimitProperties {
        if (windowSeconds <= 0) windowSeconds = 60;
        authPerIp = Math.max(1, authPerIp);
        createPerUser = Math.max(1, createPerUser);
        turnPerUser = Math.max(1, turnPerUser);
        completePerUser = Math.max(1, completePerUser);
        alternativePerUser = Math.max(1, alternativePerUser);
    }
}