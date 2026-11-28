package com.outpick.domain.place.dto;

import tools.jackson.databind.JsonNode;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/** Only facts explicitly verified for the requested date are exposed publicly. */
public record PlaceVerifiedDetailsResponse(long placeId, Map<String, JsonNode> operating,
                                           Map<String, JsonNode> activityDetails, List<String> tags,
                                           List<Fact> facts, List<Price> prices) {
    public PlaceVerifiedDetailsResponse {
        operating = Map.copyOf(operating);
        activityDetails = Map.copyOf(activityDetails);
        tags = List.copyOf(tags);
        facts = List.copyOf(facts);
        prices = List.copyOf(prices);
    }

    public record Fact(String fieldKey, JsonNode value, String sourceKind, String sourceUrl,
                       LocalDateTime checkedAt, LocalDateTime verifiedAt,
                       LocalDate validFrom, LocalDate validUntil) { }

    public record Price(String priceKind, String ageBand, String dayType, String seasonLabel,
                        Integer amountKrw, boolean freeOfCharge, String sourceKind, String sourceUrl,
                        LocalDateTime checkedAt, LocalDateTime verifiedAt,
                        LocalDate validFrom, LocalDate validUntil,
                        String ticketName, String priceType, String priceUnit, Integer durationMinutes,
                        Integer useCount, Integer passengerCount, String equipmentType,
                        Boolean equipmentIncluded, Boolean mandatoryExtraCost, String residencyCondition,
                        String rawPriceText, JsonNode additionalConditions) { }
}
