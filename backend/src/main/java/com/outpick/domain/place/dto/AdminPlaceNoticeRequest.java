package com.outpick.domain.place.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import org.hibernate.validator.constraints.URL;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;

public record AdminPlaceNoticeRequest(
        @NotBlank @Size(max = 50) String title,
        @NotBlank String content,
        LocalDateTime validFrom,
        LocalDateTime validUntil,
        @Positive Long sourceRecordId,
        @Valid SourceRequest source
) {
    public AdminPlaceNoticeRequest {
        title = title == null ? null : title.strip();
        content = content == null ? null : content.strip();
        validFrom = validFrom == null ? null : validFrom.truncatedTo(ChronoUnit.SECONDS);
        validUntil = validUntil == null ? null : validUntil.truncatedTo(ChronoUnit.SECONDS);
    }

    @Schema(hidden = true)
    @AssertTrue(message = "기존 출처 ID와 새 출처 중 하나를 입력해 주세요.")
    public boolean isSourceSelectionValid() {
        return (sourceRecordId == null) != (source == null);
    }

    @Schema(hidden = true)
    @AssertTrue(message = "유효기간은 1000년부터 9999년까지이며 종료 시각은 시작 시각보다 늦어야 합니다.")
    public boolean isValidityPeriodValid() {
        return isDatabaseDateTime(validFrom) && isDatabaseDateTime(validUntil)
                && (validFrom == null || validUntil == null || validFrom.isBefore(validUntil));
    }

    @Schema(hidden = true)
    @AssertTrue(message = "공지 본문은 UTF-8 기준 65,535바이트까지 입력할 수 있습니다.")
    public boolean isContentLengthValid() {
        return content == null || content.getBytes(StandardCharsets.UTF_8).length <= 65_535;
    }

    private static boolean isDatabaseDateTime(LocalDateTime value) {
        return value == null || (value.getYear() >= 1000 && value.getYear() <= 9999);
    }

    public record SourceRequest(
            @NotBlank @Size(max = 100)
            @URL(regexp = "(?i)https?://.+", message = "출처 URL은 HTTP 또는 HTTPS 주소여야 합니다.")
            String url,
            @NotNull LocalDateTime verifiedAt
    ) {
        public SourceRequest {
            url = url == null ? null : url.strip();
            verifiedAt = verifiedAt == null ? null : verifiedAt.truncatedTo(ChronoUnit.SECONDS);
        }

        @Schema(hidden = true)
        @AssertTrue(message = "출처 확인일은 1000년부터 9999년까지 입력할 수 있습니다.")
        public boolean isVerifiedAtValid() {
            return isDatabaseDateTime(verifiedAt);
        }
    }
}
