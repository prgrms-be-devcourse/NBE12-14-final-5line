package com.outpick.global.config;

import com.outpick.domain.example.controller.ExampleController;
import com.outpick.domain.example.service.ExampleService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** local 프로필의 허용 출처가 별도 프로필에 자동 적용되지 않는지 검증합니다. */
@WebMvcTest(ExampleController.class)
@Import({ExampleService.class, WebConfig.class})
@ActiveProfiles("test")
class WebConfigWithoutOriginsTests {

    @Autowired
    MockMvc mockMvc;

    @Test
    @DisplayName("허용 출처를 설정하지 않은 환경은 로컬 Frontend의 교차 출처 요청도 거부한다")
    void rejectsCrossOriginWithoutConfiguration() throws Exception {
        mockMvc.perform(get("/api/v1/examples/1").header(HttpHeaders.ORIGIN, "http://localhost:3000"))
                .andExpect(status().isForbidden())
                .andExpect(header().doesNotExist(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN));
    }

    @Test
    @DisplayName("허용 출처가 없어도 Origin 헤더 없는 일반 API 요청에는 v1 경로가 적용된다")
    void sameServerApiDoesNotRequireCors() throws Exception {
        mockMvc.perform(get("/api/v1/examples/1")).andExpect(status().isOk());
    }
}
