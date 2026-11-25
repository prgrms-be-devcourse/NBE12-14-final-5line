package com.outpick.domain.notification.service;

import com.outpick.domain.member.service.MemberService;
import com.outpick.domain.notification.dto.NotificationPageResponse;
import com.outpick.domain.notification.dto.NotificationReadResponse;
import com.outpick.domain.notification.exception.NotificationErrorCode;
import com.outpick.domain.notification.repository.NotificationStore;
import com.outpick.global.error.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;

@Service
@RequiredArgsConstructor
public class NotificationInboxService {
    private final MemberService memberService;
    private final NotificationStore notificationStore;
    private final Clock placeClock;

    @Transactional(readOnly = true)
    public NotificationPageResponse findMyNotifications(long memberId, int page, int size) {
        memberService.findActiveMember(memberId);
        var items = notificationStore.findInbox(memberId, LocalDateTime.now(placeClock), size, (long) page * size);
        return new NotificationPageResponse(items, page, size, notificationStore.countByMemberId(memberId));
    }

    @Transactional
    public NotificationReadResponse markMyNotificationRead(long memberId, long notificationId) {
        memberService.findActiveMember(memberId);
        var notification = notificationStore.findForReadUpdate(memberId, notificationId).orElseThrow(() ->
                new BusinessException(NotificationErrorCode.NOTIFICATION_NOT_FOUND.getErrorCode()));
        if (notification.readAt() != null) {
            return notification;
        }

        var readAt = LocalDateTime.now(placeClock).truncatedTo(ChronoUnit.MICROS);
        notificationStore.updateReadAt(memberId, notificationId, readAt);
        return new NotificationReadResponse(notificationId, readAt);
    }
}
