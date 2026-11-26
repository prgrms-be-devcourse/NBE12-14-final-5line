package com.outpick.domain.place.dto;

import com.outpick.domain.place.entity.PlaceNotice;
import com.outpick.domain.place.entity.PlacePublishStatus;

import java.time.LocalDateTime;

public record AdminPlaceNoticeResponse(
        Long placeNoticeId, String title, String content, LocalDateTime validFrom, LocalDateTime validUntil,
        PlacePublishStatus publishStatus, PlaceSourceResponse source
) {
    public static AdminPlaceNoticeResponse from(PlaceNotice notice) {
        var source = notice.getSourceRecord();
        return new AdminPlaceNoticeResponse(notice.getId(), notice.getTitle(), notice.getContent(),
                notice.getValidFrom(), notice.getValidUntil(), notice.getPublishStatus(),
                new PlaceSourceResponse(source.getId(), source.getType(), source.getUrl(), source.getVerifiedFields(),
                        source.getOriginUpdatedAt(), source.getVerifiedAt()));
    }
}
