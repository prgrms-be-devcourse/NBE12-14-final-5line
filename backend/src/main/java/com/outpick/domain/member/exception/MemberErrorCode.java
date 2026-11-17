package com.outpick.domain.member.exception;

import com.outpick.global.error.ErrorCode;
import org.springframework.http.HttpStatus;

public enum MemberErrorCode {

    EMAIL_ALREADY_EXISTS(HttpStatus.CONFLICT, "이미 가입된 이메일입니다."),
    INVALID_CREDENTIALS(HttpStatus.UNAUTHORIZED, "이메일 또는 비밀번호가 올바르지 않습니다."),
    MEMBER_NOT_FOUND(HttpStatus.UNAUTHORIZED, "회원 정보를 확인할 수 없습니다."),
    RESET_LINK_INVALID(HttpStatus.BAD_REQUEST, "재설정 링크가 유효하지 않습니다. 새 링크를 요청해 주세요."),
    PASSWORD_CONFIRMATION_MISMATCH(HttpStatus.BAD_REQUEST, "새 비밀번호와 확인 값이 일치하지 않습니다."),
    WITHDRAWAL_PASSWORD_MISMATCH(HttpStatus.BAD_REQUEST, "현재 비밀번호가 올바르지 않습니다.");

    private final ErrorCode errorCode;

    MemberErrorCode(HttpStatus status, String message) {
        this.errorCode = new ErrorCode(status, name(), message);
    }

    public ErrorCode getErrorCode() {
        return errorCode;
    }
}
