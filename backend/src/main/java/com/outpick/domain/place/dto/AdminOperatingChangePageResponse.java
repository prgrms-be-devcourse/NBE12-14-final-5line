package com.outpick.domain.place.dto;

import java.time.LocalDateTime;
import java.util.List;

public record AdminOperatingChangePageResponse(List<Item> items, int page, int size, long totalElements) {
    public AdminOperatingChangePageResponse {
        items = List.copyOf(items);
    }

    public record Item(long placeId, String placeName, List<Candidate> candidates) {
        public Item {
            candidates = List.copyOf(candidates);
        }
    }

    public record Candidate(long candidateId, String field, String previousValue, String newValue,
                            String previousSourceUrl, String newSourceUrl, LocalDateTime detectedAt) {}
}
