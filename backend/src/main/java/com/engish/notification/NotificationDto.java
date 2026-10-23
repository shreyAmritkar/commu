package com.engish.notification;

import java.time.Instant;
import java.util.UUID;

public record NotificationDto(Long id, String type, UUID sessionId, String title, String body, boolean read, Instant createdAt) {
    public static NotificationDto from(Notification n) {
        return new NotificationDto(n.getId(), n.getType(), n.getSessionId(), n.getTitle(), n.getBody(), n.getReadAt() != null, n.getCreatedAt());
    }
}