package com.outpick.domain.place.importer;

import com.outpick.domain.place.entity.ActivityCategory;
import com.outpick.domain.place.entity.ActivityType;
import com.outpick.domain.place.entity.Place;
import com.outpick.domain.place.entity.PlaceActivity;
import com.outpick.domain.place.entity.PlacePublishStatus;
import com.outpick.domain.place.entity.Region;
import com.outpick.domain.place.entity.SourceRecord;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;

@Repository
@RequiredArgsConstructor
@Transactional
public class TourImportWriter {
    private final EntityManager em;
    private final Clock placeClock;

    @Transactional(readOnly = true)
    public Long findExistingPlaceId(String contentId) {
        var imported = em.find(TourApiImportRecord.class, contentId);
        return imported == null ? null : imported.getPlaceId();
    }

    public TourImportResult save(TourImportCandidate candidate) {
        em.createNativeQuery("SELECT lock_id FROM tour_api_import_lock WHERE lock_id = 1 FOR UPDATE", Integer.class).getSingleResult();
        String contentId = candidate.target().contentId();
        var existing = em.find(TourApiImportRecord.class, contentId);
        if (existing != null) return new TourImportResult(contentId, TourImportResult.Status.SKIPPED, existing.getPlaceId(), null);
        var province = region(candidate.regionCode(), candidate.regionName(), null);
        var district = region(candidate.districtCode(), candidate.districtName(), province);
        var place = persist(Place.builder().region(district).name(candidate.name()).introduce(candidate.description())
                .address(candidate.address()).latitude(candidate.latitude()).longitude(candidate.longitude())
                .homepage(candidate.homepage()).publishStatus(PlacePublishStatus.DRAFT).build());
        var source = persist(SourceRecord.builder().place(place).type("TOUR_API").originId(contentId)
                .url("https://www.data.go.kr/data/15101578/openapi.do").originUpdatedAt(candidate.originUpdatedAt())
                .usageTermsMemo("한국관광공사 TourAPI 국문관광정보. 이미지 미수집. 응답 이미지 공공누리 유형: "
                        + candidate.imageCopyrightCode() + ". 시설 운영·종목·이용 조건은 검수 전.").build());
        saveActivities(place, candidate.target());
        persist(new TourApiImportRecord(contentId, place.getId(), source.getId(), LocalDateTime.now(placeClock).withNano(0)));
        em.flush();
        return new TourImportResult(contentId, TourImportResult.Status.CREATED, place.getId(), null);
    }

    /** 종목 후보가 있는 장소에 분류와 종목을 연결한다. 미분류 초안은 검수 단계에서 분류할 수 있다. */
    private void saveActivities(Place place, TourImportTarget target) {
        if (target.activityNames().isEmpty()) return;

        var category = em.createQuery("select c from ActivityCategory c where c.name = :name order by c.id", ActivityCategory.class)
                .setParameter("name", target.categoryName()).setMaxResults(1).getResultList().stream().findFirst()
                .orElseGet(() -> persist(ActivityCategory.builder().name(target.categoryName()).build()));
        for (String activityName : target.activityNames()) {
            var type = em.createQuery("select t from ActivityType t where t.category = :category and t.name = :name order by t.id", ActivityType.class)
                    .setParameter("category", category).setParameter("name", activityName).setMaxResults(1).getResultList().stream().findFirst()
                    .orElseGet(() -> persist(ActivityType.builder().category(category).name(activityName).build()));
            persist(PlaceActivity.builder().place(place).activityType(type).build());
        }
    }

    private Region region(String code, String name, Region parent) {
        var existing = em.createQuery("select r from Region r where r.code = :code order by r.id", Region.class)
                .setParameter("code", code).setMaxResults(1).getResultList();
        if (!existing.isEmpty()) return existing.getFirst();
        // 기존 지역을 덮어쓰지 않고, 동일 상위 지역/이름이 하나 있을 때만 재사용한다.
        var named = parent == null
                ? em.createQuery("select r from Region r where r.parent is null and r.name = :name order by r.id", Region.class)
                    .setParameter("name", name).getResultList()
                : em.createQuery("select r from Region r where r.parent = :parent and r.name = :name order by r.id", Region.class)
                    .setParameter("parent", parent).setParameter("name", name).getResultList();
        if (named.size() == 1) return named.getFirst();
        return persist(Region.builder().code(code).name(name).parent(parent).build());
    }

    private <T> T persist(T entity) { em.persist(entity); return entity; }
}
