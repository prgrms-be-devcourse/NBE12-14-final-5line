package com.outpick.domain.place.dto;

import com.outpick.domain.place.entity.Place;
import java.math.BigDecimal;
import java.util.List;

public record AdminPlaceDetailResponse(Long id, String name, String nameAlias, String address, String status,
                                       Long regionId, String regionName, BigDecimal latitude, BigDecimal longitude,
                                       String description, String homepage, List<PlaceActivityResponse> activities,
                                       List<PlaceSourceResponse> sources) {
    public AdminPlaceDetailResponse {
        activities = List.copyOf(activities);
        sources = List.copyOf(sources);
    }

    public static AdminPlaceDetailResponse from(Place place, List<PlaceActivityResponse> activities,
                                                List<PlaceSourceResponse> sources) {
        var region = place.getRegion();
        return new AdminPlaceDetailResponse(place.getId(), place.getName(), place.getNameAlias(), place.getAddress(),
                place.getPublishStatus().name(), region.getId(), region.getName(), place.getLatitude(),
                place.getLongitude(), place.getIntroduce(), place.getHomepage(), activities, sources);
    }
}
