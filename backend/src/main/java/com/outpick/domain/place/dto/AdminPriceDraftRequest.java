package com.outpick.domain.place.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.time.LocalDateTime;

public record AdminPriceDraftRequest(
        @NotBlank @Size(max=30) String priceKind,
        @Size(max=160) String ticketName,
        @PositiveOrZero Integer amountKrw,
        boolean freeOfCharge,
        @Size(max=30) String ageBand,
        @Size(max=30) String dayType,
        @Size(max=80) String seasonLabel,
        String priceUnit,
        @Positive Integer durationMinutes,
        @Positive Integer useCount,
        @Positive Integer passengerCount,
        @Size(max=100) String equipmentType,
        Boolean equipmentIncluded,
        Boolean mandatoryExtraCost,
        @Size(max=160) String residencyCondition,
        String rawPriceText,
        @NotBlank String sourceKind,
        @NotBlank String sourceUrl,
        @NotNull LocalDateTime checkedAt,
        LocalDate validFrom,
        LocalDate validUntil) {}
