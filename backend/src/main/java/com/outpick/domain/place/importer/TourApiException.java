package com.outpick.domain.place.importer;

/** 원본 응답·요청 URI·원인 예외에는 인증키가 포함될 수 있어 보관하지 않는다. */
public class TourApiException extends RuntimeException {
    public enum Reason { REQUEST_FAILED, INVALID_RESPONSE, INVALID_DATA, NO_DATA, RATE_LIMIT }
    private final Reason reason;

    public TourApiException(Reason reason) {
        super("TourAPI 처리 실패: " + reason.name());
        this.reason = reason;
    }

    public Reason getReason() { return reason; }
}
