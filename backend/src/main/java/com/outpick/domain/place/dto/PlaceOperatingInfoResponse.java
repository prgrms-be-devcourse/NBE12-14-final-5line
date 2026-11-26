package com.outpick.domain.place.dto;

import java.time.LocalDateTime;

public record PlaceOperatingInfoResponse(String operatingHours, String restDays, String contactPhone,
                                         String openingPeriod, String usageFee,
                                         String sourceUrl, LocalDateTime collectedAt, String verificationStatus,
                                         LocalDateTime verifiedAt, String reviewNote, String officialSourceUrl,
                                         LocalDateTime comparedAt) {
}
