package com.engish.notification;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "engish.notifications")
public record NotificationProperties(int maxStreamsPerUser, int streamTimeoutMinutes, int heartbeatSeconds, int retentionDays) {
    public NotificationProperties {
        if (maxStreamsPerUser < 1) maxStreamsPerUser = 5;
        if (streamTimeoutMinutes < 1) streamTimeoutMinutes = 10;   // short on purpose: the stream is re-authenticated on every reconnect
        if (heartbeatSeconds < 5) heartbeatSeconds = 25;
        if (retentionDays < 1) retentionDays = 30;
    }
}