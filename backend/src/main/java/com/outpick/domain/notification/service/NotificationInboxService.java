package com.outpick.domain.notification.service;

import com.outpick.domain.member.service.MemberService;
import com.outpick.domain.notification.dto.NotificationPageResponse;
import com.outpick.domain.notification.repository.NotificationStore;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;

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
}
