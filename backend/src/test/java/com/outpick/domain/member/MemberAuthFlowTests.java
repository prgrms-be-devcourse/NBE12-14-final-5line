package com.outpick.domain.member;

import com.outpick.domain.member.repository.MemberRepository;
import jakarta.servlet.http.HttpSession;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
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
    @Container
    @ServiceConnection
    static MySQLContainer mysql = new MySQLContainer("mysql:8.4.13");

    @Autowired MockMvc mvc;
    @Autowired MemberRepository members;
    @Autowired JdbcTemplate jdbc;

    @Test
    @DisplayName("일반 회원의 DB 역할과 회원가입·로그인·내 정보 조회·로그아웃을 검증한다")
    void signupLoginAndCurrentMemberUseTheSameSession() throws Exception {
        var csrfResult = mvc.perform(get("/api/v1/auth/csrf"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").isString())
                .andReturn();
        HttpSession session = csrfResult.getRequest().getSession(false);
        String csrf = com.jayway.jsonpath.JsonPath.read(csrfResult.getResponse().getContentAsString(), "$.token");
        String header = com.jayway.jsonpath.JsonPath.read(csrfResult.getResponse().getContentAsString(), "$.headerName");
        assertThat(session).isNotNull();

        mvc.perform(post("/api/v1/auth/signup").session((org.springframework.mock.web.MockHttpSession) session)
                        .header(header, csrf).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"short@example.com\",\"password\":\"short\",\"nickname\":\"테스트\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
        assertThat(members.findByEmail("short@example.com")).isEmpty();

        mvc.perform(post("/api/v1/auth/signup").session((org.springframework.mock.web.MockHttpSession) session)
                        .header(header, csrf).contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"TEST@EXAMPLE.COM","password":"password123","nickname":"영빈"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.email").value("test@example.com"));

        assertThat(jdbc.queryForObject("SELECT role FROM `user` WHERE email = ?", String.class, "test@example.com"))
                .isEqualTo("USER");

        mvc.perform(post("/api/v1/auth/signup").session((org.springframework.mock.web.MockHttpSession) session)
                        .header(header, csrf).contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"test@example.com","password":"another-password","nickname":"중복"}
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("EMAIL_ALREADY_EXISTS"));

        assertThat(members.findByEmail("test@example.com")).get()
                .extracting(member -> member.getPasswordHash()).isNotEqualTo("password123");

        mvc.perform(get("/api/v1/members/me").session((org.springframework.mock.web.MockHttpSession) session))
                .andExpect(status().isUnauthorized());

        mvc.perform(post("/api/v1/auth/login").session((org.springframework.mock.web.MockHttpSession) session)
                        .header(header, csrf).contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"test@example.com","password":"wrong-password"}
                                """))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"));

        mvc.perform(post("/api/v1/auth/login").session((org.springframework.mock.web.MockHttpSession) session)
                        .header(header, csrf).contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"TEST@EXAMPLE.COM","password":"password123"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("test@example.com"));

        var securityContext = (SecurityContext) session.getAttribute(
                HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY);
        assertThat(securityContext.getAuthentication().getAuthorities())
                .extracting(GrantedAuthority::getAuthority)
                .containsExactly("ROLE_USER");

        mvc.perform(get("/api/v1/members/me").session((org.springframework.mock.web.MockHttpSession) session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("test@example.com"))
                .andExpect(jsonPath("$.nickname").value("영빈"))
                .andExpect(jsonPath("$.passwordHash").doesNotExist());

        mvc.perform(post("/api/v1/auth/logout").session((org.springframework.mock.web.MockHttpSession) session))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/members/me").session((org.springframework.mock.web.MockHttpSession) session))
                .andExpect(status().isOk());

        mvc.perform(post("/api/v1/auth/logout").session((org.springframework.mock.web.MockHttpSession) session)
                        .header(header, csrf))
                .andExpect(status().isNoContent());
        assertThat(((org.springframework.mock.web.MockHttpSession) session).isInvalid()).isTrue();
        mvc.perform(get("/api/v1/members/me"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("같은 이메일과 접속 주소의 로그인 실패가 다섯 번 쌓이면 추가 시도를 제한한다")
    void repeatedFailedLoginsAreLimitedWithoutBlockingOtherAccounts() throws Exception {
        var csrfResult = mvc.perform(get("/api/v1/auth/csrf")).andExpect(status().isOk()).andReturn();
        var session = (org.springframework.mock.web.MockHttpSession) csrfResult.getRequest().getSession(false);
        String csrf = com.jayway.jsonpath.JsonPath.read(csrfResult.getResponse().getContentAsString(), "$.token");
        String header = com.jayway.jsonpath.JsonPath.read(csrfResult.getResponse().getContentAsString(), "$.headerName");
        mvc.perform(post("/api/v1/auth/signup").session(session).header(header, csrf)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"limit@example.com\",\"password\":\"password123\",\"nickname\":\"테스트\"}"))
                .andExpect(status().isCreated());

        for (int attempt = 0; attempt < 5; attempt++) {
            mvc.perform(post("/api/v1/auth/login").session(session).header(header, csrf)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"email\":\"LIMIT@example.com\",\"password\":\"wrong-password\"}"))
                    .andExpect(status().isUnauthorized());
        }
        mvc.perform(post("/api/v1/auth/login").session(session).header(header, csrf)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"limit@example.com\",\"password\":\"password123\"}"))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code").value("LOGIN_RATE_LIMITED"));
        mvc.perform(post("/api/v1/auth/login").session(session).header(header, csrf)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"another@example.com\",\"password\":\"wrong-password\"}"))
                .andExpect(status().isUnauthorized());
    }
}
