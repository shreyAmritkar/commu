package com.engish.notification;

import java.util.List;

public record NotificationsDto(long unreadCount, List<NotificationDto> items) {
}