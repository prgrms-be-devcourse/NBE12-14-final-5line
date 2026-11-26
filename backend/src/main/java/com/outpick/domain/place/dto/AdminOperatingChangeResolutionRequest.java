package com.outpick.domain.place.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record AdminOperatingChangeResolutionRequest(
        @NotNull Decision decision,
        @Size(max = 500) String reason
) {
    public enum Decision { APPROVED, REJECTED }
}
