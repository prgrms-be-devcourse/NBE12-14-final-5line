package com.outpick.domain.notification.dto;

import java.util.List;

public record NotificationPageResponse(List<NotificationResponse> items, int page, int size, long totalElements) {
    public NotificationPageResponse {
        items = List.copyOf(items);
    }
}
