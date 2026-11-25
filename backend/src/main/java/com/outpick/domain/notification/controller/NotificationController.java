package com.outpick.domain.notification.controller;

import com.outpick.domain.member.security.MemberSessionPrincipal;
import com.outpick.domain.notification.dto.NotificationPageResponse;
import com.outpick.domain.notification.dto.NotificationReadResponse;
import com.outpick.domain.notification.service.NotificationInboxService;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/members/me/notifications")
@RequiredArgsConstructor
public class NotificationController {
    private final NotificationInboxService notificationInboxService;

    @GetMapping
    public NotificationPageResponse findMyNotifications(
            @RequestParam(defaultValue = "0") @Min(0) @Max(1_000_000) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(50) int size,
            @RequestParam(defaultValue = "false") boolean unreadOnly,
            Authentication authentication) {
        var principal = (MemberSessionPrincipal) authentication.getPrincipal();
        return notificationInboxService.findMyNotifications(principal.memberId(), page, size, unreadOnly);
    }

    @PatchMapping("/{notificationId}/read")
    public NotificationReadResponse markMyNotificationRead(
            @PathVariable @Positive long notificationId, Authentication authentication) {
        var principal = (MemberSessionPrincipal) authentication.getPrincipal();
        return notificationInboxService.markMyNotificationRead(principal.memberId(), notificationId);
    }
}
