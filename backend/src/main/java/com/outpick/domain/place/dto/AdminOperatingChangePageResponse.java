package com.outpick.domain.place.dto;

import java.time.LocalDateTime;
import java.util.List;

public record AdminOperatingChangePageResponse(List<Item> items, int page, int size, long totalElements) {
    public AdminOperatingChangePageResponse {
        items = List.copyOf(items);
    }

    public record Item(long candidateId, long placeId, String placeName, String field, String previousValue,
                       String newValue, String previousSourceUrl, String newSourceUrl, LocalDateTime detectedAt) {}
}
