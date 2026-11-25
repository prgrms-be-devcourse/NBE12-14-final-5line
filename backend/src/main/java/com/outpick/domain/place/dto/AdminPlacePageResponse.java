package com.outpick.domain.place.dto;

import java.util.List;

public record AdminPlacePageResponse(List<AdminPlaceResponse> items, int page, int size, long totalElements) {
    public AdminPlacePageResponse {
        items = List.copyOf(items);
    }
}
