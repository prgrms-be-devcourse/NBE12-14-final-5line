package com.outpick.domain.place.dto;

import java.util.List;

public record AdminPlaceCandidateResponse(List<Item> items, int page, int size, long totalElements) {
    public record Item(String candidateId, String facilityName, String reviewStatus, boolean recommendable,
                       Long existingPlaceId) {}
}
