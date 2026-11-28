package com.outpick.domain.place.service;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class PlaceCanonicalResolver {
    private final JdbcTemplate jdbc;

    public long resolve(long placeId) {
        return jdbc.query("SELECT canonical_place_id FROM place_duplicate_alias WHERE duplicate_place_id = ?",
                rs -> rs.next() ? rs.getLong(1) : placeId, placeId);
    }
}
