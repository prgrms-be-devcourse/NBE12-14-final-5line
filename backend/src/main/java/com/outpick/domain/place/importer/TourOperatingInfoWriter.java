package com.outpick.domain.place.importer;

import com.outpick.domain.place.entity.Place;
import com.outpick.domain.place.entity.PlaceOperatingInfo;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;

@Repository
@RequiredArgsConstructor
public class TourOperatingInfoWriter {
    private final EntityManager em;
    private final Clock placeClock;

    @Transactional
    public String saveInitial(TourOperatingSnapshot snapshot) {
        em.createNativeQuery("SELECT lock_id FROM tour_api_import_lock WHERE lock_id=1 FOR UPDATE", Integer.class).getSingleResult();
        var imported = em.find(TourApiImportRecord.class, snapshot.contentId());
        if (imported == null) throw new TourApiException(TourApiException.Reason.INVALID_DATA);
        if (em.find(PlaceOperatingInfo.class, imported.getPlaceId()) != null) return "SKIPPED";
        var place = em.find(Place.class, imported.getPlaceId());
        em.persist(new PlaceOperatingInfo(place, snapshot.contentId(), snapshot.operatingHours(), snapshot.restDays(),
                snapshot.contactPhone(), snapshot.openingPeriod(), snapshot.usageFee(),
                LocalDateTime.now(placeClock).withNano(0)));
        em.flush();
        return "CREATED";
    }
}
