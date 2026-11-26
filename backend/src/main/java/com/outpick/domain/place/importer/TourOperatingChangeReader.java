package com.outpick.domain.place.importer;

import com.outpick.domain.place.entity.PlacePublishStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;

@RequiredArgsConstructor
class TourOperatingChangeReader {
    private final JdbcTemplate jdbc;

    List<TourOperatingChangeReport.Baseline> findPublishedAfter(long afterPlaceId, int limit) {
        return jdbc.query("""
                SELECT p.place_id, p.name, o.source_url, o.operating_hours, o.rest_days, o.contact_phone, i.content_id
                FROM place_operating_info o
                JOIN place p ON p.place_id = o.place_id
                JOIN tour_api_import i ON i.content_id = o.source_content_id AND i.place_id = p.place_id
                WHERE p.publish_status = ? AND p.place_id > ?
                ORDER BY p.place_id
                LIMIT ?
                """, (row, index) -> new TourOperatingChangeReport.Baseline(
                row.getLong("place_id"), row.getString("name"), row.getString("source_url"),
                new TourOperatingSnapshot(row.getString("content_id"), row.getString("operating_hours"),
                        row.getString("rest_days"), row.getString("contact_phone"))),
                PlacePublishStatus.PUBLISHED.getCode(), afterPlaceId, limit);
    }
}
