package com.outpick.domain.example.dto;

/**
 * 조회와 미리보기에서 공통 래퍼 없이 JSON 본문으로 반환하는 예시 응답 DTO입니다.
 */
public record ExampleResponse(String name, int participantCount) {
}
