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
    @Column(name = "source_content_id", nullable = false, length = 50)
    private String sourceContentId;
    @Column(name = "operating_hours", columnDefinition = "TEXT")
    private String operatingHours;
    @Column(name = "rest_days", columnDefinition = "TEXT")
    private String restDays;
    @Column(name = "contact_phone", length = 100)
    private String contactPhone;
    @Column(name = "opening_period", columnDefinition = "TEXT")
    private String openingPeriod;
    @Column(name = "usage_fee", columnDefinition = "TEXT")
    private String usageFee;
    @Column(name = "source_url", nullable = false, length = 1000)
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
        this(place, contentId, hours, restDays, phone, null, null, collectedAt);
    }

    public PlaceOperatingInfo(Place place, String contentId, String hours, String restDays, String phone,
                              String openingPeriod, String usageFee, LocalDateTime collectedAt) {
        this.place = Objects.requireNonNull(place, "place");
        if (contentId == null || !contentId.matches("[0-9]{1,50}")) throw new IllegalArgumentException("잘못된 콘텐츠 ID");
        this.sourceContentId = contentId;
        this.operatingHours = nullableText(hours, 10000);
        this.restDays = nullableText(restDays, 10000);
        this.contactPhone = nullableText(phone, 100);
        this.openingPeriod = nullableText(openingPeriod, 10000);
        this.usageFee = nullableText(usageFee, 10000);
        if (operatingHours == null && this.restDays == null && contactPhone == null
                && this.openingPeriod == null && this.usageFee == null)
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
