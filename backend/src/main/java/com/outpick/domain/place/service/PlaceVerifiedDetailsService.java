package com.outpick.domain.place.service;

import com.outpick.domain.place.dto.PlaceVerifiedDetailsResponse;
import com.outpick.domain.place.exception.PlaceErrorCode;
import com.outpick.domain.place.repository.PlaceDetailRepository;
import com.outpick.global.error.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

import java.time.Clock;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PlaceVerifiedDetailsService {
    private static final Set<String> OPERATING_KEYS = Set.of("operatingHours", "restDays", "openingDate",
            "closingDate", "operationStatus", "publicHours", "weeklyClosure", "seasonStart", "seasonEnd");
    private final PlaceDetailRepository places;
    private final JdbcTemplate jdbc;
    private final Clock placeClock;
    private final PlaceCanonicalResolver canonicalResolver;
    private final JsonMapper json = JsonMapper.builder().build();

    public PlaceVerifiedDetailsResponse get(long placeId) {
        return get(placeId, LocalDate.now(placeClock));
    }

    public PlaceVerifiedDetailsResponse get(long placeId, LocalDate visitDate) {
        placeId = canonicalResolver.resolve(placeId);
        places.findPublishedPlace(placeId)
                .orElseThrow(() -> new BusinessException(PlaceErrorCode.PLACE_NOT_FOUND.getErrorCode()));
        return getForPublishedOn(placeId, visitDate);
    }

    /** Caller has already verified the place is published. */
    public PlaceVerifiedDetailsResponse getForPublished(long placeId) {
        return getForPublishedOn(placeId, LocalDate.now(placeClock));
    }

    /** Allows cost estimates for a future visit without treating today's price as a future price. */
    public PlaceVerifiedDetailsResponse getForPublishedOn(long placeId, LocalDate visitDate) {
        LocalDate today = LocalDate.now(placeClock);
        List<PlaceVerifiedDetailsResponse.Fact> facts = jdbc.query("""
                SELECT field_key, value_json, source_kind, source_url, checked_at, verified_at, valid_from, valid_until
                FROM place_detail_fact
                WHERE place_id = ? AND verification_status = 'VERIFIED' AND verified_at IS NOT NULL
                  AND (valid_from IS NULL OR valid_from <= ?)
                  AND (valid_until IS NULL OR valid_until >= ?)
                ORDER BY field_key,
                  CASE source_kind WHEN 'OFFICIAL_WEBSITE' THEN 0 WHEN 'OFFICIAL_PUBLIC_DATA' THEN 1
                    WHEN 'OFFICIAL_TOURISM_ARTICLE' THEN 2 WHEN 'TOUR_API' THEN 3 ELSE 4 END,
                  verified_at DESC, fact_id DESC
                """, (rs, n) -> new PlaceVerifiedDetailsResponse.Fact(
                rs.getString("field_key"), json.readTree(rs.getString("value_json")),
                rs.getString("source_kind"), rs.getString("source_url"),
                rs.getTimestamp("checked_at") == null ? null : rs.getTimestamp("checked_at").toLocalDateTime(),
                rs.getTimestamp("verified_at").toLocalDateTime(),
                rs.getDate("valid_from") == null ? null : rs.getDate("valid_from").toLocalDate(),
                rs.getDate("valid_until") == null ? null : rs.getDate("valid_until").toLocalDate()),
                placeId, today, today);
        List<PlaceVerifiedDetailsResponse.Price> prices = jdbc.query("""
                SELECT price_kind, age_band, day_type, season_label, amount_krw, free_of_charge,
                       ticket_name, price_type, price_unit, duration_minutes, use_count, passenger_count,
                       equipment_type, equipment_included, mandatory_extra_cost, residency_condition,
                       raw_price_text, additional_conditions, source_kind, source_url, checked_at, verified_at, valid_from, valid_until
                FROM place_price
                WHERE place_id = ? AND verification_status = 'VERIFIED' AND verified_at IS NOT NULL
                  AND price_basis = 'NORMAL'
                  AND (valid_from IS NULL OR valid_from <= ?)
                  AND (valid_until IS NULL OR valid_until >= ?)
                  AND (season_label IS NULL OR (valid_from IS NOT NULL AND valid_until IS NOT NULL))
                  AND (? = 0 OR (valid_from IS NOT NULL AND valid_until IS NOT NULL))
                ORDER BY price_kind, day_type, age_band, season_label,
                  CASE source_kind WHEN 'OFFICIAL_WEBSITE' THEN 0 WHEN 'OFFICIAL_PUBLIC_DATA' THEN 1
                    WHEN 'OFFICIAL_TOURISM_ARTICLE' THEN 2 WHEN 'TOUR_API' THEN 3 ELSE 4 END,
                  verified_at DESC, price_id DESC
                """, (rs, n) -> new PlaceVerifiedDetailsResponse.Price(
                rs.getString("price_kind"), rs.getString("age_band"), rs.getString("day_type"),
                rs.getString("season_label"), (Integer) rs.getObject("amount_krw"), rs.getBoolean("free_of_charge"),
                rs.getString("source_kind"), rs.getString("source_url"),
                rs.getTimestamp("checked_at") == null ? null : rs.getTimestamp("checked_at").toLocalDateTime(),
                rs.getTimestamp("verified_at").toLocalDateTime(),
                rs.getDate("valid_from") == null ? null : rs.getDate("valid_from").toLocalDate(),
                rs.getDate("valid_until") == null ? null : rs.getDate("valid_until").toLocalDate(),
                rs.getString("ticket_name"), rs.getString("price_type"), rs.getString("price_unit"),
                (Integer) rs.getObject("duration_minutes"), (Integer) rs.getObject("use_count"),
                (Integer) rs.getObject("passenger_count"), rs.getString("equipment_type"),
                (Boolean) rs.getObject("equipment_included"), (Boolean) rs.getObject("mandatory_extra_cost"),
                rs.getString("residency_condition"), rs.getString("raw_price_text"),
                rs.getString("additional_conditions") == null ? null : json.readTree(rs.getString("additional_conditions"))),
                placeId, visitDate, visitDate, visitDate.isAfter(today) ? 1 : 0);
        List<String> tags = jdbc.query("""
                SELECT DISTINCT tag FROM place_tag
                WHERE place_id = ? AND verification_status = 'VERIFIED' AND verified_at IS NOT NULL
                  AND (valid_from IS NULL OR valid_from <= ?)
                  AND (valid_until IS NULL OR valid_until >= ?)
                ORDER BY tag
                """, (rs,n) -> rs.getString(1), placeId, today, today);
        Map<String,tools.jackson.databind.JsonNode> operating = new LinkedHashMap<>();
        Map<String,tools.jackson.databind.JsonNode> activityDetails = new LinkedHashMap<>();
        for (var fact : facts) {
            if (OPERATING_KEYS.contains(fact.fieldKey())) operating.putIfAbsent(fact.fieldKey(), fact.value());
            else activityDetails.putIfAbsent(fact.fieldKey(), fact.value());
        }
        Map<String,PlaceVerifiedDetailsResponse.Price> bestPrices = new LinkedHashMap<>();
        for (var price : prices) {
            String dimension = java.util.Arrays.asList(price.priceKind(), price.ageBand(), price.dayType(), price.seasonLabel(),
                    price.ticketName(), price.priceType(), price.priceUnit(), price.durationMinutes(),
                    price.useCount(), price.passengerCount(), price.equipmentType(), price.equipmentIncluded(),
                    price.mandatoryExtraCost(), price.residencyCondition(), price.additionalConditions()).toString();
            bestPrices.putIfAbsent(dimension, price);
        }
        return new PlaceVerifiedDetailsResponse(placeId, operating, activityDetails, tags, facts,
                List.copyOf(bestPrices.values()));
    }
}
