package com.outpick.domain.member;

import com.outpick.domain.member.repository.MemberRepository;
import com.outpick.domain.member.repository.PasswordResetTokenRepository;
import com.outpick.domain.member.service.PasswordResetService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.mysql.MySQLContainer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {"spring.jpa.hibernate.ddl-auto=validate", "management.health.mail.enabled=false"})
@AutoConfigureMockMvc
@Testcontainers
class MemberWithdrawalFlowTests {
    @Container
    @ServiceConnection
    static MySQLContainer mysql = new MySQLContainer("mysql:8.4.13");

    @Autowired MockMvc mvc;
    @Autowired MemberRepository members;
    @Autowired PasswordResetTokenRepository tokens;
    @Autowired PasswordResetService resetService;

    @Test
    @DisplayName("비밀번호와 최종 확인을 거친 탈퇴가 계정·토큰·기존 세션을 정리하는지 검증한다")
    void withdrawalRequiresConfirmationAndPasswordThenRemovesAccountTokensAndSessions() throws Exception {
        var first = csrfSession();
        mvc.perform(post("/api/v1/auth/signup").session(first.session()).header(first.header(), first.csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"leave@example.com\",\"password\":\"old-pass\",\"nickname\":\"tester\"}"))
                .andExpect(status().isCreated());
        login(first);
        var second = csrfSession();
        login(second);
        String token = resetService.request("leave@example.com").orElseThrow().token();
        assertThat(tokens.count()).isEqualTo(1);

        mvc.perform(delete("/api/v1/members/me").session(first.session()).header(first.header(), first.csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"password\":\"old-pass\",\"confirmed\":false}"))
                .andExpect(status().isBadRequest());
        mvc.perform(delete("/api/v1/members/me").session(first.session()).header(first.header(), first.csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"password\":\"wrong\",\"confirmed\":true}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("WITHDRAWAL_PASSWORD_MISMATCH"));
        assertThat(members.findByEmail("leave@example.com")).isPresent();
        assertThat(resetService.isValid(token)).isTrue();

        mvc.perform(delete("/api/v1/members/me").session(first.session()).header(first.header(), first.csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"password\":\"old-pass\",\"confirmed\":true}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("회원 탈퇴가 완료되었습니다."));

        assertThat(members.findByEmail("leave@example.com")).isEmpty();
        assertThat(tokens.count()).isZero();
        assertThat(resetService.isValid(token)).isFalse();
        mvc.perform(get("/api/v1/members/me").session(second.session()))
                .andExpect(status().isUnauthorized());
        var fresh = csrfSession();
        mvc.perform(post("/api/v1/auth/login").session(fresh.session()).header(fresh.header(), fresh.csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"leave@example.com\",\"password\":\"old-pass\"}"))
                .andExpect(status().isUnauthorized());
    }

    private void login(SessionCsrf session) throws Exception {
        mvc.perform(post("/api/v1/auth/login").session(session.session()).header(session.header(), session.csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"leave@example.com\",\"password\":\"old-pass\"}"))
                .andExpect(status().isOk());
    }

    private SessionCsrf csrfSession() throws Exception {
        var response = mvc.perform(get("/api/v1/auth/csrf")).andExpect(status().isOk()).andReturn();
        var session = (MockHttpSession) response.getRequest().getSession(false);
        String body = response.getResponse().getContentAsString();
        return new SessionCsrf(session,
                com.jayway.jsonpath.JsonPath.read(body, "$.headerName"),
                com.jayway.jsonpath.JsonPath.read(body, "$.token"));
    }

    private record SessionCsrf(MockHttpSession session, String header, String csrf) {
    }
}
