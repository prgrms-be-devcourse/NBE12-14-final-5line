package com.outpick.domain.place.repository;

import com.outpick.domain.place.entity.Place;
import com.outpick.domain.place.entity.PlacePublishStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.domain.Pageable;
import java.util.List;
import java.util.Optional;

public interface PlaceRepository extends JpaRepository<Place, Long> {
    boolean existsByIdAndPublishStatus(long id, PlacePublishStatus publishStatus);
    long countByPublishStatus(PlacePublishStatus publishStatus);
    List<Place> findAllByOrderByIdDesc(Pageable pageable);

    /** 운영자 목록에 게시 상태와 장소명·별칭 검색 조건을 적용한 뒤 페이지를 조회합니다. */
    @Query("""
            select p from Place p
            where (:status is null or p.publishStatus = :status)
                and (:text = '' or locate(lower(:text), lower(p.name)) > 0
                    or locate(lower(:text), lower(p.nameAlias)) > 0)
            order by p.id desc
            """)
    Page<Place> findForAdmin(String text, PlacePublishStatus status, Pageable pageable);

    /** 같은 장소의 작성 즉시 공개 요청을 순서대로 처리할 수 있도록 장소 행을 잠급니다. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<Place> findLockedById(long id);
}
