package com.outpick.domain.example.exception;

import com.outpick.global.error.ErrorCode;
import lombok.Getter;
import org.springframework.http.HttpStatus;

/**
 * 예시 도메인의 오류 목록입니다.<br />
 * 요소별 오류 정보는 공통 ErrorCode record로 보관합니다.
 */
@Getter
public enum ExampleErrorCode {

    EXAMPLE_NOT_FOUND(HttpStatus.NOT_FOUND, "예시를 찾을 수 없습니다."),
    EXAMPLE_CONFLICT(HttpStatus.CONFLICT, "충돌이 발생하였습니다.");

    private final ErrorCode errorCode;

    /**
     * 요소의 이름을 프론트 분기용 코드로 사용하고 HTTP 상태와 안내 문구를 함께 보관합니다.
     */
    ExampleErrorCode(HttpStatus status, String message) {
        this.errorCode = new ErrorCode(status, name(), message);
    }
}
