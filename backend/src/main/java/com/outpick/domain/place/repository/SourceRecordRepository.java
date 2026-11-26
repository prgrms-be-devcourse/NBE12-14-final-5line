package com.outpick.domain.place.repository;

import com.outpick.domain.place.entity.SourceRecord;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface SourceRecordRepository extends JpaRepository<SourceRecord, Long> {
    Optional<SourceRecord> findByIdAndPlaceId(long id, long placeId);
}
