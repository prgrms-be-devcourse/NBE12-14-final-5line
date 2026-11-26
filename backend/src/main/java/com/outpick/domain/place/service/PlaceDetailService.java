package com.outpick.domain.place.service;

import com.outpick.domain.place.dto.PlaceDetailResponse;
import com.outpick.domain.place.dto.PlaceNoticeResponse;
import com.outpick.domain.place.dto.PlaceOperatingInfoResponse;
import com.outpick.domain.place.entity.SourceRecord;
import com.outpick.domain.place.exception.PlaceErrorCode;
import com.outpick.domain.place.repository.PlaceDetailRepository;
import com.outpick.global.error.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PlaceDetailService {
    private final PlaceDetailRepository repository;
    private final Clock placeClock;

    public PlaceDetailResponse getDetail(long placeId) {
        var place = repository.findPublishedPlace(placeId)
                .orElseThrow(() -> new BusinessException(PlaceErrorCode.PLACE_NOT_FOUND.getErrorCode()));
        var activities = repository.findActivityResponses(placeId);
        var image = place.getRepresentativeImage();
        var info = place.getOperatingInfo();
        var operatingInfo = info == null ? null : new PlaceOperatingInfoResponse(info.getOperatingHours(), info.getRestDays(),
                info.getContactPhone(), info.getSourceUrl(), info.getCollectedAt(), info.getVerificationStatus().name(),
                info.getVerifiedAt(), info.getReviewNote(), info.getOfficialSourceUrl(), info.getComparedAt());
        var notices = repository.findActiveNotices(placeId, LocalDateTime.now(placeClock));
        var activeNoticeSourceIds = notices.stream().map(PlaceNoticeResponse::sourceRecordId).collect(Collectors.toSet());
        var sources = repository.findVerifiedSources(placeId).stream()
                .filter(source -> !SourceRecord.VERIFIED_FIELD_NOTICE.equals(source.verifiedFields())
                        || activeNoticeSourceIds.contains(source.sourceRecordId()))
                .toList();
        return new PlaceDetailResponse(place.getId(), place.getName(), place.getNameAlias(), place.getIntroduce(),
                place.getAddress(), place.getLatitude(), place.getLongitude(), place.getHomepage(), place.getOperationStatus(),
                place.getReviewedAt(), image == null ? null : image.getUrl(),
                image == null ? null : image.getAttribution(), image == null ? null : image.getSourceUrl(),
                image == null ? null : image.getLicenseType(), activities,
                notices, sources, operatingInfo);
    }
}
