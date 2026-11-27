package com.outpick.domain.place.dto;

import java.util.List;

public record PlaceFilterResponse(List<RegionOption> regions, List<CategoryOption> categories) {
    public record RegionOption(Long id, String name) {}
    public record CategoryOption(Long id, String name, List<Long> activityTypeIds, long publishedPlaceCount) {}
}
