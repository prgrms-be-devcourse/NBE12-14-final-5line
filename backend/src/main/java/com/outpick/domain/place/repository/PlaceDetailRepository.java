package com.outpick.domain.place.repository;

import com.outpick.domain.place.dto.PlaceActivityResponse;
import com.outpick.domain.place.dto.PlaceNoticeResponse;
import com.outpick.domain.place.dto.PlaceSourceResponse;
import com.outpick.domain.place.entity.Place;
import com.outpick.domain.place.entity.PlaceActivity;
import com.outpick.domain.place.entity.PlaceActivitySeason;
import com.outpick.domain.place.entity.PlacePublishStatus;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Repository
@RequiredArgsConstructor
public class PlaceDetailRepository {
    private final EntityManager entityManager;

    public Optional<Place> findPublishedPlace(long placeId) {
        return entityManager.createQuery("""
                select p from Place p left join fetch p.representativeImage left join fetch p.operatingInfo
                where p.id = :id and p.publishStatus = :published
                """, Place.class)
                .setParameter("id", placeId).setParameter("published", PlacePublishStatus.PUBLISHED)
                .getResultStream().findFirst();
    }

    /** 종목과 시즌을 각각 한 번씩 조회해 장소 상세에 표시할 종목별 시즌 목록을 만듭니다. */
    public List<PlaceActivityResponse> findActivityResponses(long placeId) {
        var activities = findActivities(placeId);
        var seasons = findSeasons(placeId).stream().collect(Collectors.groupingBy(
                season -> season.getPlaceActivity().getId(),
                Collectors.mapping(PlaceActivitySeason::getSeason, Collectors.toList())));
        return activities.stream().map(activity -> new PlaceActivityResponse(
                activity.getId(), activity.getActivityType().getId(), activity.getActivityType().getName(),
                activity.getEnvironmentType(), seasons.getOrDefault(activity.getId(), List.of()))).toList();
    }

    private List<PlaceActivity> findActivities(long placeId) {
        return entityManager.createQuery("""
                select a from PlaceActivity a join fetch a.activityType
                where a.place.id = :id order by a.id
                """, PlaceActivity.class).setParameter("id", placeId).getResultList();
    }

    private List<PlaceActivitySeason> findSeasons(long placeId) {
        return entityManager.createQuery("""
                select s from PlaceActivitySeason s where s.placeActivity.place.id = :id
                order by s.placeActivity.id, s.season
                """, PlaceActivitySeason.class).setParameter("id", placeId).getResultList();
    }

    public List<PlaceSourceResponse> findVerifiedSources(long placeId) {
        return findSources(placeId, true);
    }

    /** 운영자 상세에서 검수할 수 있도록 아직 확인일이 없는 출처도 조회합니다. */
    public List<PlaceSourceResponse> findSources(long placeId) {
        return findSources(placeId, false);
    }

    private List<PlaceSourceResponse> findSources(long placeId, boolean verifiedOnly) {
        return entityManager.createQuery("""
                select new com.outpick.domain.place.dto.PlaceSourceResponse(
                    s.id, s.type, s.url, s.verifiedFields, s.originUpdatedAt, s.verifiedAt)
                from SourceRecord s where s.place.id = :id and (:verifiedOnly = false or s.verifiedAt is not null)
                order by s.verifiedAt desc, s.id desc
                """, PlaceSourceResponse.class).setParameter("id", placeId)
                .setParameter("verifiedOnly", verifiedOnly).getResultList();
    }

    public List<PlaceNoticeResponse> findActiveNotices(long placeId, LocalDateTime now) {
        return entityManager.createQuery("""
                select new com.outpick.domain.place.dto.PlaceNoticeResponse(
                    n.id, n.title, n.content, n.validFrom, n.validUntil, s.id)
                from PlaceNotice n join n.sourceRecord s
                where n.place.id = :id and n.publishStatus = :published and s.verifiedAt is not null
                    and (n.validFrom is null or n.validFrom <= :now)
                    and (n.validUntil is null or n.validUntil > :now)
                order by n.validFrom desc, n.id desc
                """, PlaceNoticeResponse.class).setParameter("id", placeId)
                .setParameter("published", PlacePublishStatus.PUBLISHED).setParameter("now", now).getResultList();
    }
}
