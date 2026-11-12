package com.outpick.global.error;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import com.outpick.global.web.RequestIdFilter;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.NonNull;
import org.springframework.core.MethodParameter;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.validation.Errors;
import org.springframework.validation.FieldError;
import org.springframework.validation.method.ParameterErrors;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
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
     * 요청 DTO의 검증 실패를 필드 경로와 안내 문구로 변환합니다.
     */
    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(
            @NonNull MethodArgumentNotValidException exception,
            @NonNull HttpHeaders headers,
            @NonNull HttpStatusCode status,
            @NonNull WebRequest request
    ) {
        return validationResponse(
                exception,
                headers,
                request,
                fieldErrors(exception.getBindingResult()));
    }

    /**
     * 쿼리, 경로 파라미터와 메서드 수준에서 검증한 DTO의 오류를 같은 응답으로 변환합니다.<br />
     * 반환값 검증 실패는 서버 구현의 문제이므로 기존 500 오류 처리로 전달합니다.
     */
    @Override
    protected ResponseEntity<Object> handleHandlerMethodValidationException(
            HandlerMethodValidationException exception,
            @NonNull HttpHeaders headers,
            @NonNull HttpStatusCode status,
            @NonNull WebRequest request
    ) {
        if (exception.isForReturnValue()) {
            return handleExceptionInternal(exception, null, headers, status, request);
        }

        List<FieldValidationError> errors = new ArrayList<>();

        for (var result : exception.getParameterValidationResults()) {
            if (result instanceof ParameterErrors beanErrors) {
                errors.addAll(fieldErrors(beanErrors));
            } else {
                String field = parameterName(result.getMethodParameter());
                for (var error : result.getResolvableErrors()) {
                    errors.add(new FieldValidationError(field,
                            Objects.requireNonNullElse(error.getDefaultMessage(), "요청 값을 확인해 주세요.")));
                }
            }
        }
        return validationResponse(exception, headers, request, errors);
    }

    /**
     * 검증 오류에 400 상태와 fieldErrors를 지정하고, 공통 응답 생성 단계로 전달합니다.<br />
     * 부모 메서드를 호출해 검증 전용 코드와 문구를 유지하면서 요청 ID도 추가합니다.
     */
    private ResponseEntity<Object> validationResponse(Exception exception, HttpHeaders headers,
                                                      WebRequest request, List<FieldValidationError> errors) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, "요청 값을 확인해 주세요.");
        problem.setProperty("code", "VALIDATION_FAILED");
        problem.setProperty("fieldErrors", errors);

        log.warn("Request validation failed: errorCount={}", errors.size());
        return super.handleExceptionInternal(exception, problem, headers, HttpStatus.BAD_REQUEST, request);
    }

    /**
     * 중첩 필드·배열의 경로와 모든 안내 문구를 보존하고 사용자가 입력한 값은 제외합니다.
     * 객체 전체에 대한 검증 오류는 해당 객체 이름을 위치로 사용합니다.
     */
    private List<FieldValidationError> fieldErrors(Errors errors) {
        return errors.getAllErrors().stream()
                .map(error -> new FieldValidationError(
                        error instanceof FieldError fieldError ? fieldError.getField() : error.getObjectName(),
                        Objects.requireNonNullElse(error.getDefaultMessage(), "요청 값을 확인해 주세요.")))
                .toList();
    }

    /**
     * Java 변수명보다 API에 공개한 쿼리, 경로 파라미터 이름을 우선 사용합니다.
     */
    private String parameterName(MethodParameter parameter) {
        RequestParam query = parameter.getParameterAnnotation(RequestParam.class);
        if (query != null && !query.name().isEmpty()) {
            return query.name();
        }
        if (query != null && !query.value().isEmpty()) {
            return query.value();
        }
        PathVariable path = parameter.getParameterAnnotation(PathVariable.class);
        if (path != null && !path.name().isEmpty()) {
            return path.name();
        }
        if (path != null && !path.value().isEmpty()) {
            return path.value();
        }
        return Objects.requireNonNullElse(parameter.getParameterName(), "arg" + parameter.getParameterIndex());
    }

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
