package com.outpick.domain.place.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.Objects;

@Entity
@Table(name = "source_record")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SourceRecord {

    public static final String VERIFIED_FIELD_NOTICE = "notice";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "source_record_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "place_id", nullable = false)
    private Place place;

    @Column(name = "type", length = 50)
    private String type;

    @Column(name = "origin_id", length = 50)
    private String originId;

    @Column(name = "url", length = 100)
    private String url;

    @Column(name = "verified_fields", length = 50)
    private String verifiedFields;

    @Column(name = "origin_updated_at", columnDefinition = "DATETIME")
    private LocalDateTime originUpdatedAt;

    @Column(name = "verified_at", columnDefinition = "DATETIME")
    private LocalDateTime verifiedAt;

    @Column(name = "verifier_id")
    private Long verifierId;

    @Column(name = "usage_terms_memo", columnDefinition = "TEXT")
    private String usageTermsMemo;

    @Builder
    public SourceRecord(
            Place place,
            String type,
            String originId,
            String url,
            String verifiedFields,
            LocalDateTime originUpdatedAt,
            LocalDateTime verifiedAt,
            Long verifierId,
            String usageTermsMemo) {
        this.place = Objects.requireNonNull(place, "place");
        this.type = type;
        this.originId = originId;
        this.url = url;
        this.verifiedFields = verifiedFields;
        this.originUpdatedAt = originUpdatedAt;
        this.verifiedAt = verifiedAt;
        this.verifierId = verifierId;
        this.usageTermsMemo = usageTermsMemo;
    }
}
