package com.outpick.domain.notification.repository;

import com.outpick.domain.notification.dto.NotificationResponse;
import com.outpick.domain.notification.dto.NotificationReadResponse;
import com.outpick.domain.place.entity.PlacePublishStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

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

    public List<NotificationResponse> findInbox(long memberId, LocalDateTime now, int size, long offset,
                                                boolean unreadOnly) {
        var published = PlacePublishStatus.PUBLISHED.getCode();
        return jdbc.query("""
                SELECT n.notification_id, n.place_change_event_id, pn.place_id, pn.place_notice_id,
                       CASE WHEN s.source_record_id IS NOT NULL THEN pn.title END AS title,
                       n.created_at, n.read_at,
                       CASE WHEN LOWER(s.url) LIKE 'https://_%' OR LOWER(s.url) LIKE 'http://_%'
                            THEN s.url END AS source_url,
                       s.source_record_id IS NOT NULL AS notice_available
                FROM notification n
                JOIN place_change_event e ON e.place_change_event_id = n.place_change_event_id
                JOIN place_notice pn ON pn.place_notice_id = e.place_notice_id
                JOIN place p ON p.place_id = pn.place_id
                LEFT JOIN source_record s ON s.source_record_id = pn.source_record_id AND s.place_id = pn.place_id
                    AND s.verified_at IS NOT NULL AND p.publish_status = ? AND pn.publish_status = ?
                    AND (pn.valid_from IS NULL OR pn.valid_from <= ?)
                    AND (pn.valid_until IS NULL OR pn.valid_until > ?)
                WHERE n.user_id = ? AND (? = false OR n.read_at IS NULL)
                ORDER BY n.created_at DESC, n.notification_id DESC
                LIMIT ? OFFSET ?
                """, (row, rowNumber) -> new NotificationResponse(
                row.getLong("notification_id"), row.getLong("place_change_event_id"),
                row.getLong("place_id"), row.getLong("place_notice_id"), row.getString("title"),
                row.getObject("created_at", LocalDateTime.class), row.getObject("read_at", LocalDateTime.class),
                row.getString("source_url"), row.getBoolean("notice_available")),
                published, published, now, now, memberId, unreadOnly, size, offset);
    }

    public long countByMemberId(long memberId, boolean unreadOnly) {
        return jdbc.queryForObject("""
                SELECT COUNT(*) FROM notification WHERE user_id = ? AND (? = false OR read_at IS NULL)
                """, Long.class, memberId, unreadOnly);
    }

    public Optional<NotificationReadResponse> findForReadUpdate(long memberId, long notificationId) {
        return jdbc.query("""
                SELECT notification_id, read_at
                FROM notification
                WHERE user_id = ? AND notification_id = ?
                FOR UPDATE
                """, rows -> rows.next()
                ? Optional.of(new NotificationReadResponse(rows.getLong("notification_id"),
                        rows.getObject("read_at", LocalDateTime.class)))
                : Optional.empty(), memberId, notificationId);
    }

    public void updateReadAt(long memberId, long notificationId, LocalDateTime readAt) {
        jdbc.update("""
                UPDATE notification
                SET read_at = ?
                WHERE user_id = ? AND notification_id = ? AND read_at IS NULL
                """, readAt, memberId, notificationId);
    }
}
