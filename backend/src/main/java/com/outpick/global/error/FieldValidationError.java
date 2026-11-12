package com.outpick.global.error;

/**
 * 요청 값 검증에 실패한 위치와 사용자 안내 문구입니다.
 *
 * @param field 필드 경로 또는 쿼리·경로 파라미터 이름
 * @param message 해당 값이 만족해야 하는 조건을 설명하는 문구
 */
public record FieldValidationError(String field, String message) {
}
