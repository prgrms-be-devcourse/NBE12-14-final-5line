package com.outpick.global.web;

import java.io.IOException;
import java.util.UUID;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.jspecify.annotations.NonNull;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * 요청 ID를 생성해 응답 헤더와 MDC에 저장합니다.
 * 로그 설정은 MDC의 ID를 읽어 현재 스레드의 로그에 표시합니다.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class RequestIdFilter extends OncePerRequestFilter {

    public static final String REQUEST_ID = "requestId";
    public static final String HEADER_NAME = "X-Request-Id";

    /**
     * 비동기 작업이 끝난 뒤 응답 처리를 이어갈 때도 필터를 실행합니다.
     * 이때 응답 처리 스레드의 MDC에 기존 요청 ID를 저장합니다.
     */
    @Override
    protected boolean shouldNotFilterAsyncDispatch() {
        return false;
    }

    /**
     * 최초 요청에서 ID를 생성하고 요청 속성에 보관해 재디스패치에서도 재사용합니다.
     * 같은 ID를 응답 헤더와 MDC에 넣어 클라이언트 응답과 현재 스레드의 로그를 연결합니다.
     * 이 필터 실행이 끝나면 MDC를 이전 상태로 복원해 다음 요청에 ID가 남지 않도록 합니다.
     */
    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain
    ) throws ServletException, IOException {
        String requestId = (String) request.getAttribute(REQUEST_ID);

        if (requestId == null) {
            requestId = UUID.randomUUID().toString();
            request.setAttribute(REQUEST_ID, requestId);
        }

        response.setHeader(HEADER_NAME, requestId);
        String previousId = MDC.get(REQUEST_ID);
        MDC.put(REQUEST_ID, requestId);

        try {
            filterChain.doFilter(request, response);
        } finally {
            if (previousId == null) {
                MDC.remove(REQUEST_ID);
            } else {
                MDC.put(REQUEST_ID, previousId);
            }
        }
    }
}
