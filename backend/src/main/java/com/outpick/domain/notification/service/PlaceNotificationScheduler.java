package com.outpick.domain.notification.service;

import com.outpick.domain.place.entity.PlaceChangeEventStatus;
import com.outpick.domain.place.entity.PlacePublishStatus;
import com.outpick.domain.place.repository.PlaceChangeEventRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.LocalDateTime;

@Slf4j
@Component
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@EnableScheduling
@RequiredArgsConstructor
public class PlaceNotificationScheduler {
    private static final int BATCH_SIZE = 100;
    private static final LocalDateTime EARLIEST_DATABASE_TIME = LocalDateTime.of(1000, 1, 1, 0, 0);

    private final PlaceChangeEventRepository placeChangeEventRepository;
    private final PlaceNotificationProcessor placeNotificationProcessor;
    private final Clock placeClock;

    @Scheduled(fixedDelayString = "${outpick.notifications.poll-delay:5s}",
            initialDelayString = "${outpick.notifications.poll-delay:5s}")
    public void processDueEvents() {
        var now = LocalDateTime.now(placeClock);
        var afterTime = EARLIEST_DATABASE_TIME;
        long afterId = 0;
        var page = PageRequest.of(0, BATCH_SIZE);

        while (true) {
            var batch = placeChangeEventRepository.findDueEvents(PlaceChangeEventStatus.PENDING, PlacePublishStatus.PUBLISHED,
                    now, afterTime, afterId, page);
            for (var event : batch) {
                try {
                    placeNotificationProcessor.process(event.getId());
                } catch (RuntimeException exception) {
                    log.warn("장소 공지 알림 처리 실패. eventId={}", event.getId(), exception);
                }
            }
            if (batch.size() < BATCH_SIZE) {
                return;
            }
            var lastEvent = batch.getLast();
            afterTime = lastEvent.getEligibleAt();
            afterId = lastEvent.getId();
        }
    }
}
