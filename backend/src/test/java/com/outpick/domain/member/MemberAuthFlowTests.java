package com.outpick.domain.member;

import com.outpick.domain.member.repository.MemberRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.mysql.MySQLContainer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = "spring.jpa.hibernate.ddl-auto=validate")
@AutoConfigureMockMvc
@Testcontainers
class MemberAuthFlowTests {
    @Container @ServiceConnection static MySQLContainer mysql = new MySQLContainer("mysql:8.4.13");
    @Autowired MockMvc mvc;
    @Autowired MemberRepository members;
    @Autowired JdbcTemplate jdbc;

    @Test
    @DisplayName("회원가입 검증과 JWT 쿠키 로그인·로그아웃을 확인한다")
    void signupLoginAndLogoutUseCookies() throws Exception {
        var client = AuthTestClient.create(mvc);
        mvc.perform(client.csrf(post("/api/v1/auth/signup")).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"short@example.com\",\"password\":\"short\",\"nickname\":\"테스트\"}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
        assertThat(members.findByEmail("short@example.com")).isEmpty();
        client.signup("TEST@EXAMPLE.COM", "password123", "영빈");
        assertThat(jdbc.queryForObject("SELECT role FROM `user` WHERE email = ?", String.class, "test@example.com"))
                .isEqualTo("USER");
        mvc.perform(client.csrf(post("/api/v1/auth/signup")).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"test@example.com\",\"password\":\"password123\",\"nickname\":\"중복\"}"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("EMAIL_ALREADY_EXISTS"));
        assertThat(members.findByEmail("test@example.com")).get()
                .extracting(member -> member.getPasswordHash()).isNotEqualTo("password123");
        mvc.perform(get("/api/v1/members/me")).andExpect(status().isUnauthorized());
        mvc.perform(client.csrf(post("/api/v1/auth/login")).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"test@example.com\",\"password\":\"wrong-password\"}"))
                .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"));
        client.login("TEST@EXAMPLE.COM", "password123");
        mvc.perform(client.auth(get("/api/v1/members/me"))).andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("test@example.com"))
                .andExpect(jsonPath("$.nickname").value("영빈"))
                .andExpect(jsonPath("$.passwordHash").doesNotExist());
        mvc.perform(client.auth(post("/api/v1/auth/logout"))).andExpect(status().isForbidden());
        mvc.perform(client.auth(get("/api/v1/members/me"))).andExpect(status().isOk());
        mvc.perform(client.csrf(post("/api/v1/auth/logout"))).andExpect(status().isNoContent());
        mvc.perform(client.auth(get("/api/v1/members/me"))).andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("같은 이메일과 접속 주소의 로그인 실패가 다섯 번 쌓이면 추가 시도를 제한한다")
    void repeatedFailedLoginsAreLimitedWithoutBlockingOtherAccounts() throws Exception {
        var client = AuthTestClient.create(mvc);
        client.signup("limit@example.com", "password123", "테스트");
        for (int attempt = 0; attempt < 5; attempt++) {
            mvc.perform(client.csrf(post("/api/v1/auth/login")).contentType(MediaType.APPLICATION_JSON)
                            .content("{\"email\":\"LIMIT@example.com\",\"password\":\"wrong-password\"}"))
                    .andExpect(status().isUnauthorized());
        }
        mvc.perform(client.csrf(post("/api/v1/auth/login")).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"limit@example.com\",\"password\":\"password123\"}"))
                .andExpect(status().isTooManyRequests()).andExpect(jsonPath("$.code").value("LOGIN_RATE_LIMITED"));
        mvc.perform(client.csrf(post("/api/v1/auth/login")).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"another@example.com\",\"password\":\"wrong-password\"}"))
                .andExpect(status().isUnauthorized());
    }
}
