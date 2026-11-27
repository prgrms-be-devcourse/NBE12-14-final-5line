package com.outpick.domain.place.service;

import com.outpick.domain.member.entity.MemberRole;
import com.outpick.domain.member.service.MemberService;
import com.outpick.domain.place.dto.AdminPlaceDetailResponse;
import com.outpick.domain.place.dto.AdminPlaceCandidateResponse;
import com.outpick.domain.place.dto.AdminPlaceUpdateRequest;
import com.outpick.domain.place.dto.AdminPlaceNoticePageResponse;
import com.outpick.domain.place.dto.AdminPlaceNoticeRequest;
import com.outpick.domain.place.dto.AdminPlaceNoticeResponse;
import com.outpick.domain.place.dto.AdminPlacePageResponse;
import com.outpick.domain.place.dto.AdminPlaceRequest;
import com.outpick.domain.place.dto.AdminPlaceResponse;
import com.outpick.domain.place.entity.Place;
import com.outpick.domain.place.entity.PlaceChangeEvent;
import com.outpick.domain.place.entity.PlaceNotice;
import com.outpick.domain.place.entity.PlacePublishStatus;
import com.outpick.domain.place.entity.SourceRecord;
import com.outpick.domain.place.exception.PlaceErrorCode;
import com.outpick.domain.place.repository.PlaceChangeEventRepository;
import com.outpick.domain.place.repository.PlaceDetailRepository;
import com.outpick.domain.place.repository.PlaceNoticeRepository;
import com.outpick.domain.place.repository.PlaceRepository;
import com.outpick.domain.place.repository.RegionRepository;
import com.outpick.domain.place.repository.SourceRecordRepository;
import com.outpick.domain.review.repository.ReviewReportRepository;
import com.outpick.global.error.BusinessException;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AdminPlaceService {
    private final MemberService members;
    private final PlaceRepository places;
    private final RegionRepository regions;
    private final ReviewReportRepository reports;
    private final PlaceNoticeRepository notices;
    private final SourceRecordRepository sources;
    private final PlaceChangeEventRepository changeEvents;
    private final Clock placeClock;
    private final PlaceDetailRepository details;
    private final JdbcTemplate jdbc;

    public Map<String, Long> summary(long memberId) {
        requireAdmin(memberId);
        return Map.of("pendingReports", reports.countByStatus("PENDING"),
                "draftPlaces", places.countByPublishStatus(PlacePublishStatus.DRAFT),
                "publishedPlaces", places.countByPublishStatus(PlacePublishStatus.PUBLISHED));
    }

    public List<AdminPlaceResponse> recentPlaces(long memberId) {
        requireAdmin(memberId);
        return places.findAllByOrderByIdDesc(PageRequest.of(0, 20)).stream()
                .map(AdminPlaceResponse::from).toList();
    }

    public AdminPlacePageResponse places(long memberId, String query, PlacePublishStatus status, int page, int size) {
        requireAdmin(memberId);
        var result = places.findForAdmin(query == null ? "" : query.strip(), status, PageRequest.of(page, size));
        return new AdminPlacePageResponse(result.map(AdminPlaceResponse::from).getContent(), page, size,
                result.getTotalElements());
    }

    public AdminPlaceCandidateResponse snorkelingCandidates(long memberId, int page, int size) {
        requireAdmin(memberId);
        long total = jdbc.queryForObject("SELECT COUNT(*) FROM place_import_candidate WHERE category_name='스노클링'", Long.class);
        var items = jdbc.query("""
                SELECT candidate_id, facility_name, review_status, recommendable, place_id
                FROM place_import_candidate WHERE category_name='스노클링'
                ORDER BY facility_name, candidate_id LIMIT ? OFFSET ?
                """, (rs, row) -> new AdminPlaceCandidateResponse.Item(
                rs.getString("candidate_id"), rs.getString("facility_name"), rs.getString("review_status"),
                rs.getBoolean("recommendable"), (Long) rs.getObject("place_id")), size, Math.multiplyExact(page, size));
        return new AdminPlaceCandidateResponse(items, page, size, total);
    }

    public AdminPlaceDetailResponse place(long memberId, long placeId) {
        requireAdmin(memberId);
        var place = places.findById(placeId)
                .orElseThrow(() -> new BusinessException(PlaceErrorCode.PLACE_NOT_FOUND.getErrorCode()));
        return AdminPlaceDetailResponse.from(place, details.findAdminActivityResponses(placeId), details.findSources(placeId));
    }

    @Transactional
    public AdminPlaceResponse createDraft(long memberId, AdminPlaceRequest request) {
        requireAdmin(memberId);
        var region = regions.findById(request.regionId()).orElseThrow(() ->
                new ResponseStatusException(HttpStatus.BAD_REQUEST, "존재하지 않는 지역입니다."));
        var place = Place.builder().region(region).name(request.name().trim())
                .address(request.address().trim()).introduce(request.description() == null ? null : request.description().trim())
                .latitude(request.latitude()).longitude(request.longitude())
                .publishStatus(PlacePublishStatus.DRAFT).build();
        return AdminPlaceResponse.from(places.save(place));
    }

    @Transactional
    public AdminPlaceResponse updatePlace(long memberId, long placeId, AdminPlaceUpdateRequest request) {
        requireAdmin(memberId);
        var place = places.findById(placeId).orElseThrow(() ->
                new BusinessException(PlaceErrorCode.PLACE_NOT_FOUND.getErrorCode()));
        place.updateBasicInfo(request.name().strip(), request.description() == null ? null : request.description().strip(),
                request.address().strip());
        return AdminPlaceResponse.from(place);
    }

    @Transactional
    public AdminPlaceResponse changePlaceVisibility(long memberId, long placeId, PlacePublishStatus status) {
        requireAdmin(memberId);
        var place = places.findById(placeId).orElseThrow(() ->
                new BusinessException(PlaceErrorCode.PLACE_NOT_FOUND.getErrorCode()));
        if (status == PlacePublishStatus.PUBLISHED) {
            boolean hasVerifiedSource = details.findSources(placeId).stream()
                    .anyMatch(source -> source.verifiedAt() != null);
            boolean hasPublicActivity = !details.findActivityResponses(placeId).isEmpty();
            if (!hasVerifiedSource || !hasPublicActivity) throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "검증된 출처와 공개 종목이 있어야 장소를 공개할 수 있습니다.");
        }
        place.changePublishStatus(status);
        return AdminPlaceResponse.from(place);
    }

    public AdminPlaceNoticePageResponse notices(long memberId, long placeId, int page, int size) {
        requireAdmin(memberId);
        if (!places.existsById(placeId)) {
            throw new BusinessException(PlaceErrorCode.PLACE_NOT_FOUND.getErrorCode());
        }
        var result = notices.findAllByPlaceIdOrderByIdDesc(placeId, PageRequest.of(page, size));
        return new AdminPlaceNoticePageResponse(result.map(AdminPlaceNoticeResponse::from).getContent(),
                page, size, result.getTotalElements());
    }

    /**
     * 같은 장소의 공개 요청을 순서대로 처리하고, 잠금 대기 중 완료된 요청도 현재 조회에서 재사용합니다.
     */
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public AdminPlaceNoticeResponse createNotice(long memberId, long placeId, AdminPlaceNoticeRequest request,
                                                 boolean publish, UUID publicationRequestId) {
        requireAdmin(memberId);
        if (publish && publicationRequestId == null) {
            throw new BusinessException(PlaceErrorCode.PLACE_NOTICE_PUBLICATION_REQUEST_REQUIRED.getErrorCode());
        }
        var search = publish ? places.findLockedById(placeId) : places.findById(placeId);
        var place = search
                .orElseThrow(() -> new BusinessException(PlaceErrorCode.PLACE_NOT_FOUND.getErrorCode()));
        var requestId = publish ? publicationRequestId.toString() : null;
        if (publish) {
            var existing = notices.findByPlaceIdAndPublicationRequestId(placeId, requestId);
            if (existing.isPresent()) {
                return AdminPlaceNoticeResponse.from(existing.get());
            }
        }
        var source = resolveNoticeSource(memberId, place, request);
        var notice = PlaceNotice.builder().place(place).sourceRecord(source)
                .title(request.title()).content(request.content())
                .validFrom(request.validFrom()).validUntil(request.validUntil())
                .publicationRequestId(requestId).build();
        notices.save(notice);
        if (publish) {
            publishNotice(notice);
        }
        return AdminPlaceNoticeResponse.from(notice);
    }

    @Transactional
    public AdminPlaceNoticeResponse updateNotice(long memberId, long placeId, long noticeId,
                                                AdminPlaceNoticeRequest request, boolean publish) {
        requireAdmin(memberId);
        var notice = findNoticeForWrite(placeId, noticeId);
        if (publish && notice.getPublishStatus() != PlacePublishStatus.DRAFT) {
            if (notice.getPublishStatus() == PlacePublishStatus.PUBLISHED) {
                return AdminPlaceNoticeResponse.from(notice);
            }
            throw new BusinessException(PlaceErrorCode.PLACE_NOTICE_NOT_DRAFT.getErrorCode());
        }
        var source = resolveNoticeSource(memberId, notice.getPlace(), request);
        if (notice.getPublishStatus() == PlacePublishStatus.PUBLISHED) {
            requireVerifiedSource(source);
        }
        notice.revise(source, request.title(), request.content(), request.validFrom(), request.validUntil());
        if (publish) {
            publishNotice(notice);
        }
        return AdminPlaceNoticeResponse.from(notice);
    }

    @Transactional
    public AdminPlaceNoticeResponse changeNoticeVisibility(long memberId, long placeId, long noticeId, boolean visible) {
        requireAdmin(memberId);
        var notice = findNoticeForWrite(placeId, noticeId);
        if (visible) {
            publishNotice(notice);
        } else if (notice.getPublishStatus() == PlacePublishStatus.DRAFT) {
            throw new BusinessException(PlaceErrorCode.PLACE_NOTICE_DRAFT_CANNOT_BE_HIDDEN.getErrorCode());
        } else {
            notice.hide();
        }
        return AdminPlaceNoticeResponse.from(notice);
    }

    private PlaceNotice findNoticeForWrite(long placeId, long noticeId) {
        return notices.findByIdAndPlaceId(noticeId, placeId)
                .orElseThrow(() -> new BusinessException(PlaceErrorCode.PLACE_NOTICE_NOT_FOUND.getErrorCode()));
    }

    /**
     * 공지의 공개 상태와 최초 공개 이벤트를 호출자의 트랜잭션에서 함께 저장합니다.
     * 숨김 상태는 공개 이력이 있는 공지에만 쓰므로 재공개에서는 이벤트를 추가하지 않습니다.
     */
    private void publishNotice(PlaceNotice notice) {
        if (notice.getPublishStatus() == PlacePublishStatus.PUBLISHED) {
            return;
        }
        if (notice.getPlace().getPublishStatus() != PlacePublishStatus.PUBLISHED) {
            throw new BusinessException(PlaceErrorCode.PLACE_NOTICE_PLACE_NOT_PUBLISHED.getErrorCode());
        }
        requireVerifiedSource(notice.getSourceRecord());
        var validFrom = notice.getValidFrom();
        if (notice.getTitle() == null || notice.getTitle().isBlank()
                || notice.getContent() == null || notice.getContent().isBlank()
                || (validFrom != null && notice.getValidUntil() != null
                    && !validFrom.isBefore(notice.getValidUntil()))) {
            throw new BusinessException(PlaceErrorCode.PLACE_NOTICE_INCOMPLETE.getErrorCode());
        }
        var firstPublication = notice.getPublishStatus() == PlacePublishStatus.DRAFT;
        notice.publish();
        if (firstPublication) {
            var publishedAt = LocalDateTime.now(placeClock);
            var eligibleAt = validFrom != null && validFrom.isAfter(publishedAt) ? validFrom : publishedAt;
            changeEvents.save(new PlaceChangeEvent(notice, eligibleAt));
        }
    }

    private void requireVerifiedSource(SourceRecord source) {
        if (source.getVerifiedAt() == null) {
            throw new BusinessException(PlaceErrorCode.PLACE_NOTICE_UNVERIFIED_SOURCE.getErrorCode());
        }
    }

    /**
     * 기존 출처는 같은 장소에서만 찾습니다. 새 출처를 확인했다면 공지와 함께 저장하고 공유 출처는 그대로 둡니다.
     */
    private SourceRecord resolveNoticeSource(long memberId, Place place, AdminPlaceNoticeRequest request) {
        if (request.sourceRecordId() != null) {
            return sources.findByIdAndPlaceId(request.sourceRecordId(), place.getId())
                    .orElseThrow(() -> new BusinessException(PlaceErrorCode.PLACE_NOTICE_INVALID_SOURCE.getErrorCode()));
        }
        var source = request.source();
        return sources.save(SourceRecord.builder().place(place).type("OFFICIAL").url(source.url())
                .verifiedFields(SourceRecord.VERIFIED_FIELD_NOTICE).verifiedAt(source.verifiedAt())
                .verifierId(memberId).build());
    }

    private void requireAdmin(long memberId) {
        if (members.findActiveMember(memberId).getRole() != MemberRole.ADMIN) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "운영자만 이용할 수 있습니다.");
        }
    }
}
