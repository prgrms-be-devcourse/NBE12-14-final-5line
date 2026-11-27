package com.outpick.domain.place.service;

import com.outpick.domain.place.dto.PlaceFilterResponse;
import com.outpick.domain.place.entity.ActivityCategory;
import com.outpick.domain.place.entity.ActivityType;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PlaceFilterService {
    public static final java.util.Set<String> PUBLIC_CATEGORIES = java.util.Set.of(
            "아이스스케이팅", "해수욕장", "스노클링", "워터파크",
            "루지·레일바이크·중력 레이싱", "스키", "눈썰매");
    public static final java.util.Set<String> PUBLIC_ACTIVITY_TYPES = java.util.Set.of(
            "아이스스케이팅", "해수욕장", "스노클링", "워터파크",
            "루지", "레일바이크", "중력 레이싱", "스키", "눈썰매");
    private final EntityManager em;

    public PlaceFilterResponse getFilters() {
        var regions = em.createQuery("""
                select new com.outpick.domain.place.dto.PlaceFilterResponse$RegionOption(r.id, r.name)
                from Region r where r.parent is null and r.name is not null order by r.name, r.id
                """, PlaceFilterResponse.RegionOption.class).getResultList();
        var types = em.createQuery("select a from ActivityType a join fetch a.category order by a.id", ActivityType.class)
                .getResultList().stream().filter(type -> PUBLIC_ACTIVITY_TYPES.contains(type.getName()))
                .collect(Collectors.groupingBy(
                        type -> type.getCategory().getId(), Collectors.mapping(ActivityType::getId, Collectors.toList())));
        java.util.Map<Long, Long> counts = new java.util.HashMap<>();
        @SuppressWarnings("unchecked")
        List<Object[]> countRows = em.createNativeQuery("""
                SELECT c.activity_category_id, COUNT(DISTINCT p.place_id)
                FROM activity_category c
                JOIN activity_type t ON t.activity_category_id=c.activity_category_id
                JOIN place_activity pa ON pa.activity_type_id=t.activity_type_id
                JOIN place p ON p.place_id=pa.place_id
                WHERE p.publish_status=1 AND t.name IN ('아이스스케이팅','해수욕장','스노클링','워터파크','루지','레일바이크','중력 레이싱','스키','눈썰매')
                GROUP BY c.activity_category_id
                """).getResultList();
        for (Object[] row : countRows) counts.put(((Number)row[0]).longValue(), ((Number)row[1]).longValue());
        var categories = em.createQuery("select c from ActivityCategory c where c.name is not null order by c.id", ActivityCategory.class)
                .getResultList().stream().filter(category -> PUBLIC_CATEGORIES.contains(category.getName()))
                .map(category -> new PlaceFilterResponse.CategoryOption(
                        category.getId(), category.getName(), types.getOrDefault(category.getId(), List.of()),
                        counts.getOrDefault(category.getId(), 0L))).toList();
        return new PlaceFilterResponse(regions, categories);
    }
}
