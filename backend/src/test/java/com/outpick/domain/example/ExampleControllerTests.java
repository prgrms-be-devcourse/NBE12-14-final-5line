package com.outpick.domain.example;

import java.util.Map;

import com.outpick.domain.example.controller.ExampleController;
import com.outpick.domain.example.service.ExampleService;
import com.outpick.global.error.GlobalExceptionHandler;
import com.outpick.global.web.RequestIdFilter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 실제 컨트롤러와 서비스를 연결해 공통 기능을 사용하는 도메인 API의 동작을 검증합니다.
 */
@WebMvcTest(ExampleController.class)
@Import({ExampleService.class, GlobalExceptionHandler.class, RequestIdFilter.class})
class ExampleControllerTests {

    @Autowired
    MockMvc mockMvc;

    @Test
    @DisplayName("ID 1을 조회하면 서비스의 고정 예시를 data 래퍼 없이 200으로 반환한다")
    void getExampleReturnsDto() throws Exception {
        mockMvc.perform(get("/api/v1/examples/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("예시 액티비티"))
                .andExpect(jsonPath("$.participantCount").value(3))
                .andExpect(jsonPath("$.data").doesNotExist());
    }

    @Test
    @DisplayName("ID 2를 조회하면 서비스의 도메인 예외를 404와 예시 없음 오류로 변환한다")
    void missingExampleUsesDomainErrorCode() throws Exception {
        ResultActions result = mockMvc.perform(get("/api/v1/examples/2"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.code").value("EXAMPLE_NOT_FOUND"))
                .andExpect(jsonPath("$.detail").value("예시를 찾을 수 없습니다."))
                .andExpect(jsonPath("$.fieldErrors").doesNotExist());
        assertProblemRequestId(result);
    }

    @ParameterizedTest(name = "인원수 {0}명")
    @ValueSource(ints = {1, 10})
    @DisplayName("이름이 30자이고 인원수가 허용 범위의 경계이면 입력한 값으로 미리보기를 반환한다")
    void previewAcceptsBoundaryValues(int count) throws Exception {
        String name = "가".repeat(30);
        mockMvc.perform(post("/api/v1/examples/preview")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"%s","participantCount":%d}
                                """.formatted(name, count)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value(name))
                .andExpect(jsonPath("$.participantCount").value(count))
                .andExpect(jsonPath("$.data").doesNotExist());
    }

    @Test
    @DisplayName("이름이 공백이고 인원수가 누락되면 두 필드의 필수값 오류를 반환한다")
    void previewRequiresNameAndParticipantCount() throws Exception {
        ResultActions result = mockMvc.perform(post("/api/v1/examples/preview")
                .contentType(MediaType.APPLICATION_JSON).content("""
                        {"name":" "}
                        """));
        assertValidationProblem(result);
        result.andExpect(jsonPath("$.fieldErrors").value(containsInAnyOrder(
                Map.of("field", "name", "message", "이름을 입력해 주세요."),
                Map.of("field", "participantCount", "message", "인원수를 입력해 주세요."))));
    }

    @Test
    @DisplayName("이름이 31자이면 최대 길이를 설명하는 name 필드 오류를 반환한다")
    void previewRejectsLongName() throws Exception {
        ResultActions result = mockMvc.perform(post("/api/v1/examples/preview")
                .contentType(MediaType.APPLICATION_JSON).content("""
                        {"name":"%s","participantCount":3}
                        """.formatted("가".repeat(31))));
        assertValidationProblem(result);
        result.andExpect(jsonPath("$.fieldErrors").value(containsInAnyOrder(
                Map.of("field", "name", "message", "이름은 30자 이하여야 합니다."))));
    }

    @ParameterizedTest(name = "허용 범위 밖의 인원수 {0}명")
    @ValueSource(ints = {0, 11})
    @DisplayName("인원수가 1명 미만 또는 10명 초과이면 해당 범위 제약의 문구를 반환한다")
    void previewRejectsOutOfRangeCount(int count) throws Exception {
        ResultActions result = mockMvc.perform(post("/api/v1/examples/preview")
                .contentType(MediaType.APPLICATION_JSON).content("""
                        {"name":"예시","participantCount":%d}
                        """.formatted(count)));
        assertValidationProblem(result);
        String message = count == 0 ? "인원수는 1명 이상이어야 합니다." : "인원수는 10명 이하여야 합니다.";
        result.andExpect(jsonPath("$.fieldErrors").value(containsInAnyOrder(
                Map.of("field", "participantCount", "message", message))));
    }

    @Test
    @DisplayName("ID 0을 조회하면 도메인 조회 전에 id 필드의 양수 제약 오류를 반환한다")
    void getExampleRequiresPositiveId() throws Exception {
        ResultActions result = mockMvc.perform(get("/api/v1/examples/0"));
        assertValidationProblem(result);
        result.andExpect(jsonPath("$.fieldErrors").value(containsInAnyOrder(
                Map.of("field", "id", "message", "예시 ID는 1 이상이어야 합니다."))));
    }

    /**
     * 검증 실패의 상태와 공통 오류 코드를 확인합니다.<br />
     * 필드별 내용은 각 테스트에서 확인합니다.
     */
    private void assertValidationProblem(ResultActions result) throws Exception {
        result.andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
        assertProblemRequestId(result);
    }

    /**
     * 도메인 API의 오류가 공통 형식이며 응답 헤더와 같은 요청 ID를 갖는지 확인합니다.
     */
    private void assertProblemRequestId(ResultActions result) throws Exception {
        String requestId = result.andReturn().getResponse().getHeader("X-Request-Id");
        result.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.requestId").isNotEmpty())
                .andExpect(jsonPath("$.requestId").value(requestId));
    }
}
