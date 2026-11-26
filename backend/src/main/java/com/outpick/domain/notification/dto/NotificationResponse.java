package com.outpick.domain.notification.dto;

import java.time.LocalDateTime;

public record NotificationResponse(long notificationId, long eventId, long placeId, long placeNoticeId,
                                   String title, LocalDateTime createdAt, LocalDateTime readAt,
                                   String sourceUrl, boolean noticeAvailable) {
}
