package com.outpick.domain.place.importer;

public record TourOperatingSnapshot(String contentId, String operatingHours, String restDays, String contactPhone,
                                    String openingPeriod, String usageFee) {
    public TourOperatingSnapshot(String contentId, String operatingHours, String restDays, String contactPhone) {
        this(contentId, operatingHours, restDays, contactPhone, null, null);
    }
}
