package com.outpick.domain.notification.dto;

import java.time.LocalDateTime;

public record NotificationReadResponse(long notificationId, LocalDateTime readAt) {
}
