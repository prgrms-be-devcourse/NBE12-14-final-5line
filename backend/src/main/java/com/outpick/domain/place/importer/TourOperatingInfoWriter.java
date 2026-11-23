package com.outpick.domain.place.importer;

import com.outpick.domain.place.entity.Place;
import com.outpick.domain.place.entity.PlaceOperatingInfo;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;

@Repository
@RequiredArgsConstructor
public class TourOperatingInfoWriter {
    private final EntityManager em;
    private final Clock placeClock;

    /** 기본키로 다음 페이지를 읽는다. HTTP 호출 전에 읽기 트랜잭션이 끝난다. */
    @Transactional(readOnly = true)
    public List<String> findContentIdsAfter(String after, int limit) {
        if (limit < 1 || limit > 100) throw new IllegalArgumentException("페이지 크기는 1~100입니다.");
        return em.createQuery("select i.contentId from TourApiImportRecord i where i.contentId > :after order by i.contentId", String.class)
                .setParameter("after", after).setMaxResults(limit).getResultList();
    }

    @Transactional(readOnly = true)
    public boolean hasOperatingInfo(String contentId) {
        var imported = em.find(TourApiImportRecord.class, contentId);
        return imported != null && em.find(PlaceOperatingInfo.class, imported.getPlaceId()) != null;
    }

    @Transactional
    public String saveInitial(TourOperatingSnapshot snapshot) {
        em.createNativeQuery("SELECT lock_id FROM tour_api_import_lock WHERE lock_id=1 FOR UPDATE", Integer.class).getSingleResult();
        var imported = em.find(TourApiImportRecord.class, snapshot.contentId());
        if (imported == null) throw new TourApiException(TourApiException.Reason.INVALID_DATA);
        if (em.find(PlaceOperatingInfo.class, imported.getPlaceId()) != null) return "SKIPPED";
        var place = em.find(Place.class, imported.getPlaceId());
        em.persist(new PlaceOperatingInfo(place, snapshot.contentId(), snapshot.operatingHours(), snapshot.restDays(),
                snapshot.contactPhone(), LocalDateTime.now(placeClock).withNano(0)));
        em.flush();
        return "CREATED";
    }
}
