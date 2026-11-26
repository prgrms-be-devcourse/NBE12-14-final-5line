package com.outpick.domain.place.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Convert;
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
@Table(name = "place_notice")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PlaceNotice {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "place_notice_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "place_id", nullable = false)
    private Place place;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "source_record_id", nullable = false)
    private SourceRecord sourceRecord;

    @Column(name = "title", length = 50)
    private String title;

    @Column(name = "content", columnDefinition = "TEXT")
    private String content;

    @Column(name = "valid_from", columnDefinition = "DATETIME")
    private LocalDateTime validFrom;

    @Column(name = "valid_until", columnDefinition = "DATETIME")
    private LocalDateTime validUntil;

    @Convert(converter = PlacePublishStatusConverter.class)
    @Column(name = "publish_status", nullable = false)
    private PlacePublishStatus publishStatus = PlacePublishStatus.DRAFT;

    @Column(name = "publication_request_id", length = 36, updatable = false)
    private String publicationRequestId;

    @Builder
    public PlaceNotice(
            Place place,
            SourceRecord sourceRecord,
            String title,
            String content,
            LocalDateTime validFrom,
            LocalDateTime validUntil,
            PlacePublishStatus publishStatus,
            String publicationRequestId) {
        this.place = Objects.requireNonNull(place, "place");
        this.sourceRecord = Objects.requireNonNull(sourceRecord, "sourceRecord");
        this.title = title;
        this.content = content;
        this.validFrom = validFrom;
        this.validUntil = validUntil;
        this.publishStatus = publishStatus == null ? PlacePublishStatus.DRAFT : publishStatus;
        this.publicationRequestId = publicationRequestId;
    }

    public void revise(SourceRecord sourceRecord, String title, String content,
                       LocalDateTime validFrom, LocalDateTime validUntil) {
        this.sourceRecord = Objects.requireNonNull(sourceRecord, "sourceRecord");
        this.title = title;
        this.content = content;
        this.validFrom = validFrom;
        this.validUntil = validUntil;
    }

    public void publish() {
        publishStatus = PlacePublishStatus.PUBLISHED;
    }

    public void hide() {
        publishStatus = PlacePublishStatus.HIDDEN;
    }
}
