package com.outpick.domain.place.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.time.LocalDateTime;

public record AdminFactDraftRequest(
        @NotBlank @Size(max=80) String fieldKey,
        @NotBlank String valueJson,
        @NotBlank String sourceKind,
        @NotBlank String sourceUrl,
        @NotNull LocalDateTime checkedAt,
        LocalDate validFrom,
        LocalDate validUntil) {}
