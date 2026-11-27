package com.outpick.domain.place.dto;

import tools.jackson.databind.JsonNode;
import java.time.LocalDateTime;
import java.util.List;

/** Unknown/absent fields are never converted to false or safe. */
public record SnorkelingSafetyResponse(long placeId, String siteType, String entryStatus,
        boolean recommendable, JsonNode verifiedSafetyDetails, List<String> commonPrecautions,
        List<String> confirmedWarnings, String sourceUrl, LocalDateTime checkedAt,
        LocalDateTime verifiedAt) {
    public SnorkelingSafetyResponse {
        commonPrecautions=List.copyOf(commonPrecautions);
        confirmedWarnings=List.copyOf(confirmedWarnings);
    }
}
