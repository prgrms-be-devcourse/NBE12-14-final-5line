package com.outpick.domain.place.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.Objects;

@Entity
@Table(name = "place_change_event")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PlaceChangeEvent {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "place_change_event_id")
    private Long id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "place_notice_id", nullable = false, unique = true)
    private PlaceNotice notice;

    @Enumerated(EnumType.STRING)
    @Column(name = "task_status", nullable = false, length = 20)
    private PlaceChangeEventStatus taskStatus = PlaceChangeEventStatus.PENDING;

    @Column(name = "eligible_at", columnDefinition = "DATETIME(6)")
    private LocalDateTime eligibleAt;

    @Column(name = "processed_at", columnDefinition = "DATETIME")
    private LocalDateTime processedAt;

    public PlaceChangeEvent(PlaceNotice notice, LocalDateTime eligibleAt) {
        this.notice = Objects.requireNonNull(notice, "notice");
        this.eligibleAt = Objects.requireNonNull(eligibleAt, "eligibleAt");
    }

    public void complete(LocalDateTime processedAt) {
        this.processedAt = Objects.requireNonNull(processedAt, "processedAt");
        this.taskStatus = PlaceChangeEventStatus.COMPLETED;
    }
}
