package com.outpick.global.config;

import com.outpick.domain.example.controller.ExampleController;
import com.outpick.domain.example.service.ExampleService;
import com.outpick.global.error.GlobalExceptionHandler;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** 실제 MVC 경로 매핑과 CORS 처리를 사용해 로컬 Frontend 출처의 요청 정책을 검증합니다. */
@WebMvcTest(ExampleController.class)
@Import({ExampleService.class, GlobalExceptionHandler.class, WebConfig.class})
@ActiveProfiles("local")
class WebConfigTests {

    private static final String ORIGIN = "http://localhost:3000";

    @Autowired
    MockMvc mockMvc;

    @Test
    @DisplayName("허용한 로컬 출처의 GET 요청은 v1 API의 DTO와 출처·자격 증명 허용 헤더를 반환한다")
    void allowedOriginReceivesSuccess() throws Exception {
        mockMvc.perform(get("/api/v1/examples/1").header(HttpHeaders.ORIGIN, ORIGIN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("예시 액티비티"))
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, ORIGIN))
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_CREDENTIALS, "true"));
    }

    @Test
    @DisplayName("허용한 출처의 업무 오류도 CORS 헤더와 404 ProblemDetail을 함께 반환한다")
    void allowedOriginReceivesBusinessError() throws Exception {
        mockMvc.perform(get("/api/v1/examples/2").header(HttpHeaders.ORIGIN, ORIGIN))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("EXAMPLE_NOT_FOUND"))
                .andExpect(jsonPath("$.instance").value("/api/v1/examples/2"))
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, ORIGIN))
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_CREDENTIALS, "true"));
    }

    @Test
    @DisplayName("허용한 출처의 입력 검증 오류도 CORS 헤더와 필드 오류를 반환한다")
    void allowedOriginReceivesValidationError() throws Exception {
        mockMvc.perform(post("/api/v1/examples/preview")
                        .header(HttpHeaders.ORIGIN, ORIGIN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"\",\"participantCount\":0}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.fieldErrors").isNotEmpty())
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, ORIGIN));
    }

    @Test
    @DisplayName("POST 사전 요청은 허용 출처·메서드·요청 헤더·자격 증명 정보를 반환한다")
    void preflightAllowsConfiguredMethodAndHeaders() throws Exception {
        mockMvc.perform(options("/api/v1/examples/preview")
                        .header(HttpHeaders.ORIGIN, ORIGIN)
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "POST")
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_HEADERS, "content-type,authorization"))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, ORIGIN))
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_CREDENTIALS, "true"))
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_METHODS, containsString("POST")))
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_HEADERS, containsString("content-type")))
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_HEADERS, containsString("authorization")));
    }

    @Test
    @DisplayName("등록하지 않은 출처의 교차 출처 요청은 403이며 출처 허용 헤더를 반환하지 않는다")
    void rejectsOtherOrigin() throws Exception {
        mockMvc.perform(get("/api/v1/examples/1").header(HttpHeaders.ORIGIN, "http://localhost:3001"))
                .andExpect(status().isForbidden())
                .andExpect(header().doesNotExist(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN));
    }

    @Test
    @DisplayName("허용하지 않은 요청 헤더를 요구하는 사전 요청은 403을 반환한다")
    void rejectsOtherHeader() throws Exception {
        mockMvc.perform(options("/api/v1/examples/1")
                        .header(HttpHeaders.ORIGIN, ORIGIN)
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "GET")
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_HEADERS, "X-Unapproved"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("v1 접두사가 없는 기존 API 경로와 리소스 경로에는 컨트롤러가 연결되지 않는다")
    void legacyPathsAreNotMapped() throws Exception {
        mockMvc.perform(get("/api/examples/1")).andExpect(status().isNotFound());
        mockMvc.perform(get("/examples/1")).andExpect(status().isNotFound());
    }
}
