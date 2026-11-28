package com.outpick.domain.place.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.Objects;

@Entity
@Table(name = "place_operating_info")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PlaceOperatingInfo {
    public enum VerificationStatus { UNVERIFIED, NEEDS_REVIEW, VERIFIED }

    @Id @Column(name = "place_id")
    private Long placeId;
    @MapsId @OneToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "place_id")
    private Place place;
    @Column(name = "source_content_id", length = 50)
    private String sourceContentId;
    @Column(name = "operating_hours", columnDefinition = "TEXT")
    private String operatingHours;
    @Column(name = "rest_days", columnDefinition = "TEXT")
    private String restDays;
    @Column(name = "fee_info", columnDefinition = "TEXT")
    private String feeInfo;
    @Column(name = "contact_phone", length = 100)
    private String contactPhone;
    @Column(name = "source_url", length = 1000)
    private String sourceUrl;
    @Column(name = "collected_at", nullable = false, columnDefinition = "DATETIME")
    private LocalDateTime collectedAt;
    @Enumerated(EnumType.STRING) @Column(name = "verification_status", nullable = false, length = 20)
    private VerificationStatus verificationStatus;
    @Column(name = "verified_at", columnDefinition = "DATETIME")
    private LocalDateTime verifiedAt;
    @Column(name = "review_note", columnDefinition = "TEXT")
    private String reviewNote;
    @Column(name = "official_source_url", length = 1000)
    private String officialSourceUrl;
    @Column(name = "compared_at", columnDefinition = "DATETIME")
    private LocalDateTime comparedAt;

    public PlaceOperatingInfo(Place place, String contentId, String hours, String restDays, String phone, LocalDateTime collectedAt) {
        this.place = Objects.requireNonNull(place, "place");
        if (contentId == null || !contentId.matches("[0-9]{1,50}")) throw new IllegalArgumentException("잘못된 콘텐츠 ID");
        this.sourceContentId = contentId;
        this.operatingHours = nullableText(hours, 10000);
        this.restDays = nullableText(restDays, 10000);
        this.contactPhone = nullableText(phone, 100);
        if (operatingHours == null && this.restDays == null && contactPhone == null)
            throw new IllegalArgumentException("수집한 운영 안내가 없습니다.");
        this.sourceUrl = "https://www.data.go.kr/data/15101578/openapi.do";
        this.collectedAt = Objects.requireNonNull(collectedAt, "collectedAt");
        this.verificationStatus = VerificationStatus.UNVERIFIED;
        place.attachOperatingInfo(this);
    }

    private String nullableText(String text, int max) {
        if (text == null || text.isBlank()) return null;
        if (text.length() > max) throw new IllegalArgumentException("운영 안내의 길이를 확인해 주세요.");
        return text.trim();
    }
}
