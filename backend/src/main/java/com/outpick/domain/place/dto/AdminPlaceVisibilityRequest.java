package com.outpick.domain.place.dto;

import com.outpick.domain.place.entity.PlacePublishStatus;
import jakarta.validation.constraints.NotNull;

public record AdminPlaceVisibilityRequest(@NotNull PlacePublishStatus status) {}
