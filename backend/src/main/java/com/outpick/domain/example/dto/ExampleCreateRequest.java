package com.outpick.domain.example.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * 필수값, 문자열 길이, 숫자 범위를 선언하는 예시 요청 DTO입니다.
 * 컨트롤러의 {@code @Valid}가 이 제약을 검사하며 실패 응답은 공통 예외 처리기가 만듭니다.
 *
 * @param name 필수 이름으로 최대 30자까지 허용합니다.
 * @param participantCount 필수 인원수입니다. 누락 시 null로 받아 검증하도록 Integer를 사용합니다.
 */
public record ExampleCreateRequest(
        @NotBlank(message = "이름을 입력해 주세요.")
        @Size(max = 30, message = "이름은 30자 이하여야 합니다.")
        String name,

        @NotNull(message = "인원수를 입력해 주세요.")
        @Min(value = 1, message = "인원수는 1명 이상이어야 합니다.")
        @Max(value = 10, message = "인원수는 10명 이하여야 합니다.")
        Integer participantCount
) {
}
