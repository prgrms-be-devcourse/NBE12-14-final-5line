package com.outpick.global.error;

import org.springframework.http.HttpStatus;

/**
 * 도메인 enum의 각 요소가 보관하고 BusinessException에 전달하는 공통 오류 정보입니다.
 *
 * @param status 오류 응답에 사용할 HTTP 상태
 * @param code 프론트가 오류를 구분하는 코드로, 도메인 enum의 요소 이름을 사용합니다.
 * @param message 오류 응답의 detail에 담을 사용자 안내 문구
 */
public record ErrorCode(HttpStatus status, String code, String message) {
}
