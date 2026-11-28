package com.outpick.domain.place.dto;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public record AdminEvidenceResponse(List<Price> prices, List<Fact> facts) {
    public record Price(long id, String ticketName, String priceKind, String priceBasis, Integer amountKrw,
                        boolean freeOfCharge, String sourceKind, String sourceUrl, LocalDateTime checkedAt,
                        LocalDateTime verifiedAt, LocalDate validFrom, LocalDate validUntil, String status) {}
    public record Fact(long id, String fieldKey, String valueJson, String sourceKind, String sourceUrl,
                       LocalDateTime checkedAt, LocalDateTime verifiedAt, LocalDate validFrom,
                       LocalDate validUntil, String status) {}
}
