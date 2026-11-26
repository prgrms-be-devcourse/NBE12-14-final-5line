package com.outpick.domain.place.exception;

import com.outpick.global.error.ErrorCode;
import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
public enum PlaceErrorCode {
    PLACE_NOT_FOUND(HttpStatus.NOT_FOUND, "장소를 찾을 수 없습니다."),
    PLACE_NOTICE_NOT_FOUND(HttpStatus.NOT_FOUND, "해당 장소에서 공지를 찾을 수 없습니다."),
    PLACE_NOTICE_NOT_DRAFT(HttpStatus.CONFLICT, "초안 상태의 공지만 수정할 수 있습니다."),
    PLACE_NOTICE_INVALID_SOURCE(HttpStatus.BAD_REQUEST, "해당 장소에서 공지에 연결할 출처를 찾을 수 없습니다."),
    PLACE_NOTICE_UNVERIFIED_SOURCE(HttpStatus.BAD_REQUEST, "공지를 공개하려면 확인일이 있는 출처를 연결해 주세요."),
    PLACE_NOTICE_INCOMPLETE(HttpStatus.BAD_REQUEST, "공지를 공개하려면 제목, 본문과 유효기간을 확인해 주세요."),
    PLACE_NOTICE_PLACE_NOT_PUBLISHED(HttpStatus.CONFLICT, "공개된 장소에만 공지를 공개할 수 있습니다."),
    PLACE_NOTICE_DRAFT_CANNOT_BE_HIDDEN(HttpStatus.CONFLICT, "초안은 숨길 수 없습니다."),
    PLACE_NOTICE_PUBLICATION_REQUEST_REQUIRED(HttpStatus.BAD_REQUEST, "작성과 동시에 공개하려면 공개 요청 ID가 필요합니다.");

    private final ErrorCode errorCode;

    PlaceErrorCode(HttpStatus status, String message) {
        errorCode = new ErrorCode(status, name(), message);
    }
}
