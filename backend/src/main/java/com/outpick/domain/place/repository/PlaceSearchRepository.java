package com.outpick.domain.place.repository;

import com.outpick.domain.place.dto.PlacePageResponse;
import com.outpick.domain.place.dto.PlaceSearchRequest;
import com.outpick.domain.place.dto.PlaceSummaryResponse;
import com.outpick.domain.place.entity.Place;
import com.outpick.domain.place.entity.PlaceActivity;
import com.outpick.domain.place.entity.PlaceActivitySeason;
import com.outpick.domain.place.entity.PlacePublishStatus;
import jakarta.persistence.EntityManager;
import jakarta.persistence.criteria.AbstractQuery;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import java.time.Clock;
import java.time.LocalDate;
import java.util.Map;
import java.util.HashMap;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.List;

@Repository
@RequiredArgsConstructor
public class PlaceSearchRepository {
    private final EntityManager entityManager;
    private final NamedParameterJdbcTemplate jdbc;
    private final Clock placeClock;

    public PlacePageResponse search(PlaceSearchRequest request) {
        var cb = entityManager.getCriteriaBuilder();
        var query = cb.createQuery(PlaceSummaryResponse.class);
        var place = query.from(Place.class);
        var image = place.join("representativeImage", JoinType.LEFT);
        query.select(cb.construct(PlaceSummaryResponse.class,
                place.get("id"), place.get("name"), place.get("address"), place.get("latitude"), place.get("longitude"),
                image.get("url"), image.get("attribution"), image.get("sourceUrl"), image.get("licenseType")));
        query.where(predicates(request, cb, query, place));
        var nameOrder = "name-desc".equals(request.sort()) ? cb.desc(place.get("name")) : cb.asc(place.get("name"));
        query.orderBy(nameOrder, cb.asc(place.get("id")));
        var pageItems = entityManager.createQuery(query)
                .setFirstResult(Math.multiplyExact(request.page(), request.size()))
                .setMaxResults(request.size()).getResultList();
        var previews = pricePreviews(pageItems);
        var items = pageItems.stream().map(item -> item.withPricePreview(previews.get(item.id()))).toList();

        var countQuery = cb.createQuery(Long.class);
        var countedPlace = countQuery.from(Place.class);
        countQuery.select(cb.count(countedPlace));
        countQuery.where(predicates(request, cb, countQuery, countedPlace));
        long total = entityManager.createQuery(countQuery).getSingleResult();
        return new PlacePageResponse(items, request.page(), request.size(), total);
    }


    /** Show only a verified, current, general admission price; never use rental or child-only prices as a minimum. */
    private Map<Long, String> pricePreviews(List<PlaceSummaryResponse> items) {
        if (items.isEmpty()) return Map.of();
        var ids = items.stream().map(PlaceSummaryResponse::id).toList();
        LocalDate today = LocalDate.now(placeClock);
        Map<Long, String> result = new HashMap<>();
        jdbc.query("""
                SELECT place_id, MIN(amount_krw) AS amount,
                       MAX(CASE WHEN free_of_charge=1 THEN 1 ELSE 0 END) AS free
                FROM place_price
                WHERE place_id IN (:ids) AND verification_status='VERIFIED' AND verified_at IS NOT NULL
                  AND NOT EXISTS (SELECT 1 FROM place_activity pa JOIN activity_type atp ON atp.activity_type_id=pa.activity_type_id
                    WHERE pa.place_id=place_price.place_id AND atp.name IN ('해수욕장','스노클링'))
                  AND price_basis='NORMAL' AND price_kind='ADMISSION'
                  AND age_band IS NULL AND day_type IS NULL AND season_label IS NULL
                  AND duration_minutes IS NULL AND use_count IS NULL AND passenger_count IS NULL
                  AND residency_condition IS NULL AND (mandatory_extra_cost IS NULL OR mandatory_extra_cost=0)
                  AND (valid_from IS NULL OR valid_from<=:today)
                  AND (valid_until IS NULL OR valid_until>=:today)
                GROUP BY place_id
                """, Map.of("ids", ids, "today", today), rs -> {
                    long id = rs.getLong("place_id");
                    Integer amount = (Integer) rs.getObject("amount");
                    if (rs.getInt("free") == 1 && amount != null && amount == 0) result.put(id, "무료");
                    else if (amount != null && amount > 0) result.put(id, "기본 입장료 " + String.format("%,d", amount) + "원");
                });
        return result;
    }
    private Predicate[] predicates(PlaceSearchRequest request, CriteriaBuilder cb, AbstractQuery<?> query, Root<Place> place) {
        List<Predicate> predicates = new ArrayList<>();
        predicates.add(cb.equal(place.get("publishStatus"), PlacePublishStatus.PUBLISHED));
        if (request.query() != null) {
            String text = "%" + escapeLike(request.query()) + "%";
            predicates.add(cb.or(cb.like(place.get("name"), text, '\\'), cb.like(place.get("nameAlias"), text, '\\')));
        }
        if (request.regionId() != null) {
            var region = place.join("region");
            var parent = region.join("parent", JoinType.LEFT);
            // 현재 지역 모델의 시·도 / 시·군·구 탐색을 지원한다.
            predicates.add(cb.or(cb.equal(region.get("id"), request.regionId()), cb.equal(parent.get("id"), request.regionId())));
        }
        if (request.south() != null) {
            predicates.add(cb.between(place.get("latitude"), request.south(), request.north()));
            predicates.add(cb.between(place.get("longitude"), request.west(), request.east()));
        }
        {
            var activityQuery = query.subquery(Integer.class);
            var activity = activityQuery.from(PlaceActivity.class);
            List<Predicate> conditions = new ArrayList<>();
            conditions.add(cb.equal(activity.get("place"), place));
            conditions.add(activity.get("activityType").get("category").get("name")
                    .in(com.outpick.domain.place.service.PlaceFilterService.PUBLIC_CATEGORIES));
            conditions.add(activity.get("activityType").get("name")
                    .in(com.outpick.domain.place.service.PlaceFilterService.PUBLIC_ACTIVITY_TYPES));
            if (!request.activityTypeIds().isEmpty()) conditions.add(activity.get("activityType").get("id").in(request.activityTypeIds()));
            if (request.environmentType() != null) conditions.add(cb.equal(activity.get("environmentType"), request.environmentType()));
            if (request.season() != null) {
                var seasonQuery = activityQuery.subquery(Integer.class);
                var season = seasonQuery.from(PlaceActivitySeason.class);
                seasonQuery.select(cb.literal(1)).where(
                        cb.equal(season.get("placeActivity"), activity), cb.equal(season.get("season"), request.season()));
                conditions.add(cb.exists(seasonQuery));
            }
            activityQuery.select(cb.literal(1)).where(conditions.toArray(Predicate[]::new));
            // EXISTS로 장소 행을 늘리지 않아 페이지와 결과 수에 중복이 생기지 않는다.
            predicates.add(cb.exists(activityQuery));
        }
        return predicates.toArray(Predicate[]::new);
    }

    private String escapeLike(String value) {
        return value.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }
}
