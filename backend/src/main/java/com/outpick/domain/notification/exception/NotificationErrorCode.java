package com.outpick.domain.notification.exception;

import com.outpick.global.error.ErrorCode;
import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
public enum NotificationErrorCode {
    NOTIFICATION_NOT_FOUND(HttpStatus.NOT_FOUND, "알림을 찾을 수 없습니다.");

    private final ErrorCode errorCode;

    NotificationErrorCode(HttpStatus status, String message) {
        this.errorCode = new ErrorCode(status, name(), message);
    }
}
