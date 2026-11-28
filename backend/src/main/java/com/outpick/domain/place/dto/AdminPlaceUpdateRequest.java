package com.outpick.domain.place.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record AdminPlaceUpdateRequest(
        @NotBlank @Size(max = 50) String name,
        @NotBlank @Size(max = 100) String address,
        @Size(max = 2000) String description) {}
