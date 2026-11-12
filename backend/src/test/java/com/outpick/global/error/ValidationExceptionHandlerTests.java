package com.outpick.global.error;

import java.util.List;
import java.util.Map;

import com.outpick.global.web.RequestIdFilter;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ValidationExceptionHandlerTests.ExampleController.class)
@Import({GlobalExceptionHandler.class, RequestIdFilter.class, ValidationExceptionHandlerTests.ExampleController.class})
class ValidationExceptionHandlerTests {

    @Autowired
    MockMvc mockMvc;

    @Test
    @DisplayName("요청 DTO의 여러 필드가 조건을 위반하면 필드명과 문구를 모두 반환한다")
    void invalidBodyReturnsAllFieldErrors() throws Exception {
        ResultActions result = mockMvc.perform(post("/test/validation/places")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"name":" ","size":51}
                        """));
        assertValidationResponse(result);
        result.andExpect(jsonPath("$.fieldErrors").value(containsInAnyOrder(
                Map.of("field", "name", "message", "장소 이름을 입력해 주세요."),
                Map.of("field", "name", "message", "장소 이름은 2자 이상이어야 합니다."),
                Map.of("field", "size", "message", "페이지 크기는 50 이하여야 합니다."))));
    }

    @Test
    @DisplayName("정상 요청 DTO는 컨트롤러에서 처리하고 검증 오류를 반환하지 않는다")
    void validBodySucceeds() throws Exception {
        mockMvc.perform(post("/test/validation/places")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"카약 체험장","size":20}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("카약 체험장"))
                .andExpect(jsonPath("$.fieldErrors").doesNotExist());
    }

    @Test
    @DisplayName("중첩 객체와 배열의 검증 오류는 address.city와 photos[0].url처럼 위치를 반환한다")
    void nestedFieldsKeepTheirPaths() throws Exception {
        ResultActions result = mockMvc.perform(post("/test/validation/details")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"address":{"city":""},"photos":[{"url":""}]}
                        """));
        assertValidationResponse(result);
        result.andExpect(jsonPath("$.fieldErrors[*].field")
                .value(containsInAnyOrder("address.city", "photos[0].url")));
    }

    @Test
    @DisplayName("검증에 실패한 비밀번호의 입력값은 오류 응답에 포함하지 않는다")
    void rejectedPasswordIsNotExposed() throws Exception {
        ResultActions result = mockMvc.perform(post("/test/validation/password")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"password":"secret"}
                        """));
        assertValidationResponse(result);
        result.andExpect(jsonPath("$.fieldErrors").value(containsInAnyOrder(
                        Map.of("field", "password", "message", "비밀번호는 12자 이상이어야 합니다."))))
                .andExpect(content().string(org.hamcrest.Matchers.not(
                        org.hamcrest.Matchers.containsString("secret"))));
    }

    @Test
    @DisplayName("쿼리·경로 값이 조건을 위반하면 Java 변수명 대신 API 파라미터 이름을 반환한다")
    void queryAndPathUsePublicParameterNames() throws Exception {
        ResultActions result = mockMvc.perform(get("/test/validation/places/0").param("size", "51"));
        assertValidationResponse(result);
        result.andExpect(jsonPath("$.fieldErrors").value(containsInAnyOrder(
                Map.of("field", "placeId", "message", "장소 ID는 1 이상이어야 합니다."),
                Map.of("field", "size", "message", "페이지 크기는 50 이하여야 합니다."))));
    }

    @Test
    @DisplayName("쿼리·경로 값이 허용 범위의 경계에 있으면 요청을 정상 처리한다")
    void validParameterBoundariesSucceed() throws Exception {
        mockMvc.perform(get("/test/validation/places/1").param("size", "50"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.placeId").value(1))
                .andExpect(jsonPath("$.size").value(50));
    }

    @Test
    @DisplayName("본문과 쿼리를 함께 메서드 수준에서 검증해도 필드별 오류를 같은 형식으로 반환한다")
    void methodValidationIncludesBodyAndQueryErrors() throws Exception {
        ResultActions result = mockMvc.perform(post("/test/validation/mixed").param("size", "51")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"name":" ","size":20}
                        """));
        assertValidationResponse(result);
        result.andExpect(jsonPath("$.fieldErrors[*].field")
                .value(containsInAnyOrder("name", "name", "size")));
    }

    @Test
    @DisplayName("컨트롤러 반환값의 검증 실패는 요청 오류가 아닌 서버 오류 500으로 처리한다")
    void returnValueValidationRemainsServerError() throws Exception {
        mockMvc.perform(get("/test/validation/invalid-return"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value("INTERNAL_SERVER_ERROR"))
                .andExpect(jsonPath("$.fieldErrors").doesNotExist());
    }

    @Test
    @DisplayName("숫자 파라미터에 문자를 보내면 기존 400 오류를 반환하고 fieldErrors는 추가하지 않는다")
    void typeMismatchKeepsExistingErrorFormat() throws Exception {
        mockMvc.perform(get("/test/validation/places/abc").param("size", "20"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("BAD_REQUEST"))
                .andExpect(jsonPath("$.fieldErrors").doesNotExist());
    }

    /** 모든 검증 실패에서 기존 ProblemDetail 필드와 요청 ID가 유지되는지 확인합니다. */
    private void assertValidationResponse(ResultActions result) throws Exception {
        String requestId = result.andReturn().getResponse().getHeader("X-Request-Id");
        result.andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.title").value("Bad Request"))
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.detail").value("요청 값을 확인해 주세요."))
                .andExpect(jsonPath("$.instance").isNotEmpty())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.requestId").isNotEmpty())
                .andExpect(jsonPath("$.requestId").value(requestId));
    }

    /** 테스트 전용 API로 DTO·파라미터의 제약 선언 방법을 보여 줍니다. */
    @RestController
    static class ExampleController {

        @PostMapping("/test/validation/places")
        public PlaceRequest create(@Valid @RequestBody PlaceRequest request) {
            return request;
        }

        @PostMapping("/test/validation/details")
        public DetailRequest details(@Valid @RequestBody DetailRequest request) {
            return request;
        }

        @PostMapping("/test/validation/password")
        public void password(@Valid @RequestBody PasswordRequest request) {
        }

        @GetMapping("/test/validation/places/{placeId}")
        public Map<String, Object> list(
                @PathVariable("placeId") @Min(value = 1, message = "장소 ID는 1 이상이어야 합니다.") long id,
                @RequestParam(name = "size") @Max(value = 50, message = "페이지 크기는 50 이하여야 합니다.") int pageSize
        ) {
            return Map.of("placeId", id, "size", pageSize);
        }

        @PostMapping("/test/validation/mixed")
        public PlaceRequest mixed(@Valid @RequestBody PlaceRequest request,
                @RequestParam("size") @Max(value = 50, message = "페이지 크기는 50 이하여야 합니다.") int pageSize) {
            return request;
        }

        @GetMapping("/test/validation/invalid-return")
        @NotBlank
        public String invalidReturn() {
            return "";
        }
    }

    record PlaceRequest(
            @NotBlank(message = "장소 이름을 입력해 주세요.")
            @Size(min = 2, message = "장소 이름은 2자 이상이어야 합니다.") String name,
            @Max(value = 50, message = "페이지 크기는 50 이하여야 합니다.") int size) {
    }

    record DetailRequest(@Valid Address address, @Valid List<Photo> photos) {
    }

    record Address(@NotBlank(message = "도시를 입력해 주세요.") String city) {
    }

    record Photo(@NotBlank(message = "사진 URL을 입력해 주세요.") String url) {
    }

    record PasswordRequest(@Size(min = 12, message = "비밀번호는 12자 이상이어야 합니다.") String password) {
    }
}
