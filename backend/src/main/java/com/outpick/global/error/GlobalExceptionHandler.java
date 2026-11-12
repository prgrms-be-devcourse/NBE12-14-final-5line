package com.outpick.global.error;

import java.net.URI;

import com.outpick.global.web.RequestIdFilter;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.NonNull;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.ServletWebRequest;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

/**
 * Spring MVC 기본 예외와 도메인 예외를 공통 오류 응답으로 변환합니다.
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    private static final String INTERNAL_ERROR_MESSAGE = "서버 오류가 발생했습니다. 잠시 후 다시 시도해 주세요.";

    /**
     * BusinessException => ProblemDetail로 만들어서 응답합니다.
     */
    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<Object> handleBusinessException(
            BusinessException exception,
            WebRequest request
    ) {
        ErrorCode error = exception.getErrorCode();

        // 비즈니스 예외 => 오류 코드를 Log에 기록
        log.warn("Business request failure: code={}", error.code());

        ProblemDetail problem = ProblemDetail.forStatusAndDetail(error.status(), error.message());
        problem.setProperty("code", error.code());

        return super.handleExceptionInternal(
                exception,
                problem,
                new HttpHeaders(),
                error.status(), request);
    }

    /**
     * 기타 Exception => ProblemDetail로 만들어서 응답합니다.<br />
     * 별도 처리 규칙이 없는 예외를 500 공통 처리로 전달합니다.
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<Object> handleUnexpectedException(
            Exception exception,
            WebRequest request
    ) {
        return handleExceptionInternal(
                exception,
                null,
                new HttpHeaders(),
                HttpStatus.INTERNAL_SERVER_ERROR, request);
    }

    /**
     * Spring MVC 기본 예외와 예상치 못한 예외를 공개용 오류 응답으로 변환합니다.<br />
     * 5xx 오류의 원인은 로그에 남기고, 응답에는 공통 안내 문구를 사용합니다.<br />
     * Spring이 정한 HTTP 상태와 Allow 등의 응답 헤더는 유지합니다.
     */
    @Override
    protected ResponseEntity<Object> handleExceptionInternal(
            @NonNull Exception exception,
            Object body,
            @NonNull HttpHeaders headers,
            HttpStatusCode status,
            @NonNull WebRequest request
    ) {
        HttpStatus httpStatus = HttpStatus.resolve(status.value());

        if (status.is5xxServerError()) {
            // 5xx 에러 => Exception(StackTrace)를 Log에 기록
            log.error("Server request failure: status={}", status.value(), exception);
        } else {
            // 그 외 에러 => status만 경고로 Log에 기록
            log.warn("Rejected request: status={}", status.value());
        }

        String detail = status.is5xxServerError() ? INTERNAL_ERROR_MESSAGE : "요청을 처리할 수 없습니다.";

        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setProperty("code", httpStatus == null ? "REQUEST_FAILED" : httpStatus.name());

        return super.handleExceptionInternal(exception, problem, headers, status, request);
    }

    /**
     * 최종 오류 응답에 요청 경로 {@code instance}와 로그 검색용 {@code requestId}를 추가합니다.<br />
     * 도메인 오류와 Spring MVC 오류 모두 이 메서드를 거쳐 ResponseEntity가 됩니다.
     */
    @Override
    protected ResponseEntity<Object> createResponseEntity(
            Object body,
            @NonNull HttpHeaders headers,
            @NonNull HttpStatusCode status,
            @NonNull WebRequest request
    ) {
        if (body instanceof ProblemDetail problem
                && request instanceof ServletWebRequest servletRequest
        ) {
            problem.setInstance(URI.create(servletRequest.getRequest().getRequestURI()));
            problem.setProperty("requestId", servletRequest.getRequest().getAttribute(RequestIdFilter.REQUEST_ID));
        }

        return super.createResponseEntity(body, headers, status, request);
    }
}
