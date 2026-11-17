package com.outpick.domain.member.dto;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;

public record WithdrawalRequest(
        @NotBlank String password,
        @AssertTrue(message = "회원 탈퇴를 최종 확인해 주세요.") boolean confirmed
) {
}
