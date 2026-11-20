package com.outpick.domain.member.dto;

import jakarta.validation.constraints.NotBlank;

public record PasswordResetValidateRequest(@NotBlank String token) {
}
