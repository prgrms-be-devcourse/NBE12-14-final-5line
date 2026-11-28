package com.outpick.domain.place.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public record PlaceDetailResponse(Long id, String name, String nameAlias, String description, String address,
                                   BigDecimal latitude, BigDecimal longitude, String homepage, String operationStatus,
                                   LocalDateTime reviewedAt, String representativeImage,
                                   String imageAttribution, String imageSourceUrl, String imageLicense,
                                   List<PlaceActivityResponse> activities, List<PlaceNoticeResponse> notices,
                                   List<PlaceSourceResponse> sources, PlaceOperatingInfoResponse operatingInfo,
                                   PlaceVerifiedDetailsResponse verifiedDetails) {
    public PlaceDetailResponse {
        activities = List.copyOf(activities);
        notices = List.copyOf(notices);
        sources = List.copyOf(sources);
    }
}
