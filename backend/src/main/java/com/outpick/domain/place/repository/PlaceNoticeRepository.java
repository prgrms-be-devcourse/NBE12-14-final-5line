package com.outpick.domain.place.repository;

import com.outpick.domain.place.entity.PlaceNotice;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;

import java.util.Optional;

public interface PlaceNoticeRepository extends JpaRepository<PlaceNotice, Long> {
    @EntityGraph(attributePaths = "sourceRecord")
    Page<PlaceNotice> findAllByPlaceIdOrderByIdDesc(long placeId, Pageable pageable);

    /** 공지 수정과 공개 상태 변경이 겹치면 한 요청이 끝난 뒤 다음 요청이 같은 공지를 읽습니다. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<PlaceNotice> findByIdAndPlaceId(long id, long placeId);

    Optional<PlaceNotice> findByPlaceIdAndPublicationRequestId(long placeId, String publicationRequestId);
}
