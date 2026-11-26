package com.outpick.domain.place.dto;

import java.util.List;

public record AdminPlaceNoticePageResponse(List<AdminPlaceNoticeResponse> items, int page, int size,
                                           long totalElements) {
    public AdminPlaceNoticePageResponse {
        items = List.copyOf(items);
    }
}
