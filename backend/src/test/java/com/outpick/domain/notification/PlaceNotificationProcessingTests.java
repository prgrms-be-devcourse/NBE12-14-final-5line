package com.outpick.domain.notification;

import com.outpick.domain.notification.repository.NotificationStore;
import com.outpick.domain.notification.service.PlaceNotificationProcessor;
import com.outpick.domain.notification.service.PlaceNotificationScheduler;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.config.ScheduledTaskHolder;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.mysql.MySQLContainer;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;

@SpringBootTest(properties = {"spring.jpa.hibernate.ddl-auto=validate", "outpick.notifications.poll-delay=1d"})
@Import(PlaceNotificationProcessingTests.TestTime.class)
@Testcontainers
class PlaceNotificationProcessingTests {
    private static final LocalDateTime ELIGIBLE_AT = LocalDateTime.parse("2026-11-24T11:00:00");
    private static final LocalDateTime NOW = ELIGIBLE_AT.plusSeconds(5);

    @Container
    @ServiceConnection
    static MySQLContainer mysql = new MySQLContainer("mysql:8.4.13");

    @Autowired JdbcTemplate jdbc;
    @Autowired PlaceNotificationScheduler scheduler;
    @Autowired PlaceNotificationProcessor processor;
    @Autowired ScheduledTaskHolder scheduledTasks;
    @MockitoSpyBean NotificationStore notifications;

    @TestConfiguration(proxyBeanMethods = false)
    static class TestTime {
        @Bean
        @Primary
        Clock fixedPlaceClock() {
            var seoul = ZoneId.of("Asia/Seoul");
            return Clock.fixed(NOW.atZone(seoul).toInstant(), seoul);
        }
    }

    @Test
    @DisplayName("현재 ON이며 기준 시각까지 알림을 켜 둔 해당 장소의 회원에게만 알림을 생성한다")
    void sendsOnlyToEligibleRecipients() {
        assertThat(scheduledTasks.getScheduledTasks()).isNotEmpty();
        createPlace(101);
        createPlace(102);
        createEvent(101, 101, ELIGIBLE_AT);
        favorite(1001, 101, true, ELIGIBLE_AT.minusMinutes(30));
        favorite(1002, 101, true, ELIGIBLE_AT);
        favorite(1003, 101, true, ELIGIBLE_AT.plusSeconds(2));
        favorite(1004, 101, false, null);
        favorite(1005, 102, true, ELIGIBLE_AT.minusHours(1));
        favorite(1006, 101, true, ELIGIBLE_AT.minusHours(1));
        jdbc.update("DELETE FROM favorite WHERE user_id = ?", 1006);

        scheduler.processDueEvents();
        scheduler.processDueEvents();

        assertThat(recipients(101)).containsExactly(1001L, 1002L);
        assertThat(status(101)).isEqualTo("COMPLETED");
        assertThat(jdbc.queryForObject("""
                SELECT processed_at FROM place_change_event WHERE place_change_event_id = 101
                """, LocalDateTime.class)).isEqualTo(NOW);
        assertThat(jdbc.queryForList("""
                SELECT created_at FROM notification WHERE place_change_event_id = 101
                """, LocalDateTime.class)).containsExactly(NOW, NOW);
    }

    @Test
    @DisplayName("수신자 없는 이벤트는 완료하고 기준 시각이 없거나 아직 노출되지 않은 공지는 대기시킨다")
    void skipsLegacyFutureAndUnavailableNotices() {
        createPlace(201);
        createEvent(201, 201, ELIGIBLE_AT);
        createEvent(202, 201, null);
        createEvent(203, 201, NOW.plusHours(1));
        createEvent(204, 201, ELIGIBLE_AT);
        createEvent(205, 201, ELIGIBLE_AT);
        createEvent(206, 201, ELIGIBLE_AT);
        jdbc.update("UPDATE place_notice SET publish_status = 2 WHERE place_notice_id = 204");
        jdbc.update("UPDATE place_notice SET valid_until = ? WHERE place_notice_id = 205", NOW);
        jdbc.update("UPDATE place_notice SET valid_from = ? WHERE place_notice_id = 206", NOW.plusHours(1));

        scheduler.processDueEvents();
        processor.process(204);
        processor.process(205);
        processor.process(206);

        assertThat(status(201)).isEqualTo("COMPLETED");
        assertThat(recipients(201)).isEmpty();
        for (long eventId = 202; eventId <= 206; eventId++) {
            assertThat(status(eventId)).isEqualTo("PENDING");
            assertThat(recipients(eventId)).isEmpty();
        }
    }

    @Test
    @DisplayName("같은 이벤트를 여러 작업자가 동시에 처리해도 알림은 한 건만 저장된다")
    void serializesConcurrentProcessing() throws Exception {
        createPlace(301);
        createEvent(301, 301, ELIGIBLE_AT);
        favorite(3001, 301, true, ELIGIBLE_AT.minusMinutes(1));
        var start = new CyclicBarrier(2);

        try (var workers = Executors.newFixedThreadPool(2)) {
            var first = workers.submit(() -> {
                start.await(5, TimeUnit.SECONDS);
                processor.process(301);
                return null;
            });
            var second = workers.submit(() -> {
                start.await(5, TimeUnit.SECONDS);
                processor.process(301);
                return null;
            });
            first.get(10, TimeUnit.SECONDS);
            second.get(10, TimeUnit.SECONDS);
        }

        assertThat(recipients(301)).containsExactly(3001L);
        assertThat(status(301)).isEqualTo("COMPLETED");
    }

    @Test
    @DisplayName("개인 알림 저장 뒤 오류가 나면 롤백하고 다음 페이지 이벤트까지 처리한 뒤 재시도한다")
    void rollsBackFailedEventWithoutBlockingLaterPages() {
        createPlace(401);
        favorite(4001, 401, true, ELIGIBLE_AT);
        for (long eventId = 401; eventId <= 501; eventId++) {
            createEvent(eventId, 401, ELIGIBLE_AT);
        }
        var failOnce = new AtomicBoolean(true);
        doAnswer(invocation -> {
            invocation.callRealMethod();
            if (failOnce.getAndSet(false)) {
                throw new IllegalStateException("알림 저장 직후 오류");
            }
            return null;
        }).when(notifications).insertForEligibleRecipients(eq(401L), anyLong(), any(), any());

        scheduler.processDueEvents();

        assertThat(status(401)).isEqualTo("PENDING");
        assertThat(recipients(401)).isEmpty();
        assertThat(status(501)).isEqualTo("COMPLETED");
        assertThat(recipients(501)).containsExactly(4001L);

        scheduler.processDueEvents();

        assertThat(status(401)).isEqualTo("COMPLETED");
        assertThat(recipients(401)).containsExactly(4001L);
    }

    private void createPlace(long id) {
        jdbc.update("INSERT INTO region (region_id, name) VALUES (?, '알림 지역')", id);
        jdbc.update("INSERT INTO place (place_id, region_id, publish_status) VALUES (?, ?, 1)", id, id);
        jdbc.update("""
                INSERT INTO source_record (source_record_id, place_id, verified_at) VALUES (?, ?, ?)
                """, id, id, ELIGIBLE_AT.minusDays(1));
    }

    private void createEvent(long id, long placeId, LocalDateTime eligibleAt) {
        jdbc.update("""
                INSERT INTO place_notice (place_notice_id, place_id, source_record_id, publish_status)
                VALUES (?, ?, ?, 1)
                """, id, placeId, placeId);
        jdbc.update("""
                INSERT INTO place_change_event (place_change_event_id, place_notice_id, eligible_at) VALUES (?, ?, ?)
                """, id, id, eligibleAt);
    }

    private void favorite(long memberId, long placeId, boolean enabled, LocalDateTime enabledAt) {
        jdbc.update("""
                INSERT INTO `user` (user_id, email, nickname, password_hash, created_at, updated_at)
                VALUES (?, ?, '회원', 'hash', ?, ?)
                """, memberId, memberId + "@notification.example.com", NOW, NOW);
        jdbc.update("""
                INSERT INTO favorite (user_id, place_id, is_notified, notified_enabled_at) VALUES (?, ?, ?, ?)
                """, memberId, placeId, enabled, enabledAt);
    }

    private String status(long eventId) {
        return jdbc.queryForObject("""
                SELECT task_status FROM place_change_event WHERE place_change_event_id = ?
                """, String.class, eventId);
    }

    private List<Long> recipients(long eventId) {
        return jdbc.queryForList("""
                SELECT user_id FROM notification WHERE place_change_event_id = ? ORDER BY user_id
                """, Long.class, eventId);
    }
}
