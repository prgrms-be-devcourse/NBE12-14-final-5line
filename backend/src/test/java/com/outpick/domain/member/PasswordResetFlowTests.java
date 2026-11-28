package com.outpick.domain.member;

import com.outpick.domain.member.repository.MemberRepository;
import com.outpick.domain.member.repository.PasswordResetTokenRepository;
import com.outpick.domain.member.service.MemberService;
import com.outpick.global.error.BusinessException;
import java.util.regex.Pattern;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.mysql.MySQLContainer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {"spring.jpa.hibernate.ddl-auto=validate", "management.health.mail.enabled=false"})
@AutoConfigureMockMvc
@Testcontainers
class PasswordResetFlowTests {
    @Container
    @ServiceConnection
    static MySQLContainer mysql = new MySQLContainer("mysql:8.4.13");

    @Autowired MockMvc mvc;
    @Autowired MemberRepository members;
    @Autowired MemberService memberService;
    @Autowired PasswordResetTokenRepository tokens;
    @MockitoBean JavaMailSender sender;

    @Test
    @DisplayName("비밀번호 재설정 요청의 동일 응답, 일회용 토큰, 이전 세션 차단을 검증한다")
    void resetIsSingleUseAndRevokesOldSessionWithoutRevealingUnknownEmail() throws Exception {
        var client = AuthTestClient.create(mvc);
        client.signup("reset@example.com", "old-pass", "tester");
        client.login("reset@example.com", "old-pass");

        String unknownResponse = mvc.perform(client.csrf(post("/api/v1/auth/password-reset/request"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"unknown@example.com\"}"))
                .andExpect(status().isAccepted()).andReturn().getResponse().getContentAsString();
        String knownResponse = mvc.perform(client.csrf(post("/api/v1/auth/password-reset/request"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"reset@example.com\"}"))
                .andExpect(status().isAccepted()).andReturn().getResponse().getContentAsString();
        assertThat(knownResponse).isEqualTo(unknownResponse);

        var captor = org.mockito.ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(sender, times(1)).send(captor.capture());
        String body = captor.getValue().getText();
        var matcher = Pattern.compile("/reset-password/#token=([A-Za-z0-9_-]{43})").matcher(body);
        assertThat(matcher.find()).isTrue();
        String rawToken = matcher.group(1);
        assertThat(tokens.findAll()).hasSize(1)
                .allSatisfy(token -> assertThat(token.getTokenHash()).isNotEqualTo(rawToken));

        mvc.perform(get("/api/v1/auth/password-reset/validate").param("token", rawToken))
                .andExpect(status().isMethodNotAllowed());
        mvc.perform(client.auth(post("/api/v1/auth/password-reset/validate"))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"token\":\"" + rawToken + "\"}"))
                .andExpect(status().isForbidden());
        mvc.perform(client.csrf(post("/api/v1/auth/password-reset/validate"))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"token\":\"" + rawToken + "\"}"))
                .andExpect(status().isNoContent())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.header().string("Cache-Control", "no-store"));
        mvc.perform(client.csrf(post("/api/v1/auth/password-reset/confirm"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"token\":\"" + rawToken
                                + "\",\"newPassword\":\"short\",\"newPasswordConfirmation\":\"short\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
        mvc.perform(client.csrf(post("/api/v1/auth/password-reset/validate"))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"token\":\"" + rawToken + "\"}"))
                .andExpect(status().isNoContent());
        mvc.perform(client.csrf(post("/api/v1/auth/password-reset/confirm"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"token\":\"" + rawToken
                                + "\",\"newPassword\":\"new-pass\",\"newPasswordConfirmation\":\"new-pass\"}"))
                .andExpect(status().isOk());

        mvc.perform(client.auth(get("/api/v1/members/me"))).andExpect(status().isUnauthorized());
        mvc.perform(client.csrf(post("/api/v1/auth/refresh"))).andExpect(status().isUnauthorized());
        mvc.perform(client.csrf(post("/api/v1/auth/password-reset/validate"))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"token\":\"" + rawToken + "\"}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("RESET_LINK_INVALID"));
        assertThatThrownBy(() -> memberService.authenticate("reset@example.com", "old-pass"))
                .isInstanceOf(BusinessException.class);
        assertThat(memberService.authenticate("reset@example.com", "new-pass").getId())
                .isEqualTo(members.findByEmail("reset@example.com").orElseThrow().getId());
    }
}
