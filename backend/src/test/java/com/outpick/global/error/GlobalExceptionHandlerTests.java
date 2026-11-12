package com.outpick.global.error;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;

import com.outpick.global.web.RequestIdFilter;
import lombok.Getter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;

@WebMvcTest(GlobalExceptionHandlerTests.ExampleController.class)
@Import({GlobalExceptionHandler.class, RequestIdFilter.class, GlobalExceptionHandlerTests.ExampleController.class})
@ExtendWith(OutputCaptureExtension.class)
class GlobalExceptionHandlerTests {

    @Autowired
    MockMvc mockMvc;

    @Test
    @DisplayName("장소 없음 예외를 404 상태와 오류 코드·안내 문구를 담은 공통 오류 응답으로 변환한다")
    void domainErrorReturnsProblemDetail() throws Exception {
        ResultActions result = mockMvc.perform(get("/test/places/42").accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.title").value("Not Found"))
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.detail").value("장소를 찾을 수 없습니다."))
                .andExpect(jsonPath("$.instance").value("/test/places/42"))
                .andExpect(jsonPath("$.code").value("PLACE_NOT_FOUND"));
        assertRequestIdMatchesHeader(result);
    }

    @Test
    @DisplayName("같은 도메인의 장소 중복 오류는 별도의 409 상태, 오류 코드, 안내 문구를 반환한다")
    void duplicatePlaceReturnsOwnErrorInformation() throws Exception {
        ResultActions result = mockMvc.perform(get("/test/places/duplicate"))
                .andExpect(status().isConflict())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.code").value("PLACE_ALREADY_EXISTS"))
                .andExpect(jsonPath("$.detail").value("이미 등록된 장소입니다."));
        assertRequestIdMatchesHeader(result);
    }

    @Test
    @DisplayName("요청 본문의 JSON 문법이 틀리면 400 상태, 공통 오류 코드, 안내 문구를 반환한다")
    void malformedJsonReturnsBadRequest() throws Exception {
        ResultActions result = mockMvc.perform(post("/test/places")
                        .contentType(MediaType.APPLICATION_JSON).accept(MediaType.APPLICATION_JSON).content("{invalid"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("BAD_REQUEST"))
                .andExpect(jsonPath("$.detail").value("요청을 처리할 수 없습니다."));
        assertRequestIdMatchesHeader(result);
    }

    @Test
    @DisplayName("등록되지 않은 URL로 요청하면 404 상태와 NOT_FOUND 오류 코드를 반환한다")
    void unknownPathReturnsNotFound() throws Exception {
        ResultActions result = mockMvc.perform(get("/test/unknown"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));
        assertRequestIdMatchesHeader(result);
    }

    @Test
    @DisplayName("GET 전용 URL에 POST로 요청하면 405 상태와 GET을 명시한 Allow 헤더를 반환한다")
    void unsupportedMethodPreservesAllowHeader() throws Exception {
        mockMvc.perform(post("/test/places/42"))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(header().string("Allow", "GET"))
                .andExpect(jsonPath("$.code").value("METHOD_NOT_ALLOWED"));
    }

    @Test
    @DisplayName("비동기 처리 후 오류 응답에도 최초 요청 ID를 사용하고 응답 처리 스레드의 MDC를 비운다")
    void asyncErrorKeepsOriginalRequestId() throws Exception {
        var initial = mockMvc.perform(get("/test/async"))
                .andExpect(request().asyncStarted()).andReturn();
        String initialId = initial.getResponse().getHeader("X-Request-Id");
        ResultActions result = mockMvc.perform(asyncDispatch(initial))
                .andExpect(status().isNotFound())
                .andExpect(header().string("X-Request-Id", initialId))
                .andExpect(jsonPath("$.code").value("PLACE_NOT_FOUND"));
        assertRequestIdMatchesHeader(result);
        assertThat(MDC.get(RequestIdFilter.REQUEST_ID)).isNull();
    }

    @Test
    @DisplayName("예상치 못한 예외는 500과 공통 안내 문구를 반환하고 원인과 요청 ID를 로그에 남긴다")
    void unexpectedErrorHidesInternalDetailsAndLogsRequestId(CapturedOutput output) throws Exception {
        ResultActions result = mockMvc.perform(get("/test/unexpected"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.status").value(500))
                .andExpect(jsonPath("$.code").value("INTERNAL_SERVER_ERROR"))
                .andExpect(jsonPath("$.detail").value("서버 오류가 발생했습니다. 잠시 후 다시 시도해 주세요."));
        String requestId = assertRequestIdMatchesHeader(result);
        assertThat(result.andReturn().getResponse().getContentAsString()).doesNotContain("internal diagnostic");
        assertThat(output.getAll()).contains("requestId=" + requestId, "internal diagnostic");
        assertThat(MDC.get(RequestIdFilter.REQUEST_ID)).isNull();
    }

    @Test
    @DisplayName("성공 본문에 data를 씌우지 않고 클라이언트가 보낸 ID 대신 요청마다 새 ID를 생성한다")
    void successfulRequestsReturnDtoAndHaveIndependentServerGeneratedIds() throws Exception {
        ResultActions first = mockMvc.perform(get("/test/success").header("X-Request-Id", "client-value"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(42))
                .andExpect(jsonPath("$.data").doesNotExist());
        String firstId = assertRequestIdMatchesHeader(first);
        ResultActions second = mockMvc.perform(get("/test/success"));
        String secondId = assertRequestIdMatchesHeader(second);
        assertThat(firstId).isNotEqualTo(secondId).isNotEqualTo("client-value");
        assertThat(MDC.get(RequestIdFilter.REQUEST_ID)).isNull();
    }

    private String assertRequestIdMatchesHeader(ResultActions result) throws Exception {
        String requestId = result.andReturn().getResponse().getHeader("X-Request-Id");
        assertThat(requestId).isNotNull();
        assertThat(UUID.fromString(requestId).toString()).isEqualTo(requestId);
        result.andExpect(jsonPath("$.requestId").value(requestId));
        return requestId;
    }

    // 각 도메인은 enum으로 오류 목록을 관리하고, 요소마다 공통 record를 보관합니다.
    @Getter
    enum PlaceErrorCode {
        PLACE_NOT_FOUND(HttpStatus.NOT_FOUND, "장소를 찾을 수 없습니다."),
        PLACE_ALREADY_EXISTS(HttpStatus.CONFLICT, "이미 등록된 장소입니다.");

        private final ErrorCode errorCode;

        /** 요소별 상태와 문구를 보관하고 enum 이름을 오류 코드로 사용합니다. */
        PlaceErrorCode(HttpStatus status, String message) {
            this.errorCode = new ErrorCode(status, name(), message);
        }
    }

    // 테스트 전용 경로로 오류 상황을 재현하며, 실제 애플리케이션에는 등록되지 않습니다.
    @RestController
    static class ExampleController {

        @GetMapping("/test/places/42")
        public void getPlace() {
            throw new BusinessException(PlaceErrorCode.PLACE_NOT_FOUND.getErrorCode());
        }

        @GetMapping("/test/places/duplicate")
        public void duplicatePlace() {
            throw new BusinessException(PlaceErrorCode.PLACE_ALREADY_EXISTS.getErrorCode());
        }

        @PostMapping("/test/places")
        public PlaceRequest createPlace(@RequestBody PlaceRequest request) {
            return request;
        }

        @GetMapping("/test/unexpected")
        public void unexpected() {
            throw new IllegalStateException("internal diagnostic");
        }

        @GetMapping("/test/async")
        public Callable<Void> async() {
            return () -> {
                throw new BusinessException(PlaceErrorCode.PLACE_NOT_FOUND.getErrorCode());
            };
        }

        @GetMapping("/test/success")
        public Map<String, Object> success() {
            return Map.of(
                    "id", 42,
                    "requestId", MDC.get(RequestIdFilter.REQUEST_ID));
        }
    }

    record PlaceRequest(String name) {
    }
}
