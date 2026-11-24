package com.outpick.domain.notification.repository;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;

@Repository
@RequiredArgsConstructor
public class NotificationStore {
    private final JdbcTemplate jdbc;

    public void insertForEligibleRecipients(long eventId, long placeId, LocalDateTime eligibleAt,
                                            LocalDateTime createdAt) {
        jdbc.update("""
                INSERT INTO notification (place_change_event_id, user_id, created_at)
                SELECT ?, f.user_id, ?
                FROM favorite f
                WHERE f.place_id = ? AND f.is_notified = true AND f.notified_enabled_at <= ?
                """, eventId, createdAt, placeId, eligibleAt);
    }
}
