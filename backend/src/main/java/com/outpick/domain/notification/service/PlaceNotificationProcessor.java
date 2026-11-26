package com.outpick.domain.notification.service;

import com.outpick.domain.notification.repository.NotificationStore;
import com.outpick.domain.place.entity.PlaceChangeEventStatus;
import com.outpick.domain.place.entity.PlaceNotice;
import com.outpick.domain.place.entity.PlacePublishStatus;
import com.outpick.domain.place.repository.PlaceChangeEventRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class PlaceNotificationProcessor {
    private final PlaceChangeEventRepository placeChangeEventRepository;
    private final NotificationStore notificationStore;
    private final Clock placeClock;

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public void process(long eventId) {
        var event = placeChangeEventRepository.findForProcessing(eventId).orElse(null);
        var now = LocalDateTime.now(placeClock);
        if (event == null || event.getTaskStatus() != PlaceChangeEventStatus.PENDING
                || event.getEligibleAt() == null || event.getEligibleAt().isAfter(now)
                || !isVisible(event.getNotice(), now)) {
            return;
        }

        notificationStore.insertForEligibleRecipients(eventId, event.getNotice().getPlace().getId(),
                event.getEligibleAt(), now);
        event.complete(now);
    }

    private boolean isVisible(PlaceNotice notice, LocalDateTime now) {
        return notice.getPublishStatus() == PlacePublishStatus.PUBLISHED
                && notice.getPlace().getPublishStatus() == PlacePublishStatus.PUBLISHED
                && notice.getSourceRecord().getVerifiedAt() != null
                && (notice.getValidFrom() == null || !notice.getValidFrom().isAfter(now))
                && (notice.getValidUntil() == null || notice.getValidUntil().isAfter(now));
    }
}
