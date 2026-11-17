package com.outpick.domain.member;

import com.outpick.domain.member.repository.MemberRepository;
import com.outpick.domain.member.service.MemberService;
import jakarta.servlet.http.HttpSession;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
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
    @Autowired MemberService memberService;

    @Test
    @DisplayName("회원가입·로그인·내 정보 조회와 이전 세션 무효화를 검증한다")
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
                        .content("""
                                {"email":"TEST@EXAMPLE.COM","password":"password123","nickname":"영빈"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.email").value("test@example.com"));

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

        mvc.perform(get("/api/v1/members/me").session((org.springframework.mock.web.MockHttpSession) session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("test@example.com"))
                .andExpect(jsonPath("$.nickname").value("영빈"))
                .andExpect(jsonPath("$.passwordHash").doesNotExist());

        memberService.revokeAllSessions(members.findByEmail("test@example.com").orElseThrow().getId());
        mvc.perform(get("/api/v1/members/me").session((org.springframework.mock.web.MockHttpSession) session))
                .andExpect(status().isUnauthorized());
    }
}
