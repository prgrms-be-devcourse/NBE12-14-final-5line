package com.outpick.domain.place.dto;

import jakarta.validation.constraints.NotNull;

public record AdminPlaceNoticeVisibilityRequest(@NotNull Boolean visible) {
}
