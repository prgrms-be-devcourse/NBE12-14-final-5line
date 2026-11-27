package com.outpick.domain.place.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

public record AdminEvidenceReviewRequest(
        @NotNull @Pattern(regexp = "VERIFIED|NEEDS_REVIEW|REJECTED") String status) {}
