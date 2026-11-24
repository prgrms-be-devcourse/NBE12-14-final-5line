package com.outpick.domain.place.repository;

import com.outpick.domain.place.entity.PlaceChangeEvent;
import com.outpick.domain.place.entity.PlaceChangeEventStatus;
import com.outpick.domain.place.entity.PlacePublishStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface PlaceChangeEventRepository extends JpaRepository<PlaceChangeEvent, Long> {
    @Query("""
            select e.id as id, e.eligibleAt as eligibleAt
            from PlaceChangeEvent e join e.notice n join n.place p join n.sourceRecord s
            where e.taskStatus = :status and e.eligibleAt <= :now
                and (e.eligibleAt > :afterTime or (e.eligibleAt = :afterTime and e.id > :afterId))
                and n.publishStatus = :published and p.publishStatus = :published
                and s.verifiedAt is not null
                and (n.validFrom is null or n.validFrom <= :now)
                and (n.validUntil is null or n.validUntil > :now)
            order by e.eligibleAt, e.id
            """)
    List<DueEvent> findDueEvents(@Param("status") PlaceChangeEventStatus status,
                                @Param("published") PlacePublishStatus published,
                                @Param("now") LocalDateTime now,
                                @Param("afterTime") LocalDateTime afterTime,
                                @Param("afterId") long afterId, Pageable pageable);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select e from PlaceChangeEvent e
            join fetch e.notice n join fetch n.place join fetch n.sourceRecord
            where e.id = :id
            """)
    Optional<PlaceChangeEvent> findForProcessing(@Param("id") long id);

    interface DueEvent {
        long getId();
        LocalDateTime getEligibleAt();
    }
}
