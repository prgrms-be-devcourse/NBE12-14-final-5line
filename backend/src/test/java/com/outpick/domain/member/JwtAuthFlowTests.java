package com.outpick.domain.member;

import com.outpick.domain.member.repository.MemberRepository;
import com.outpick.domain.member.repository.RefreshTokenRepository;
import jakarta.servlet.http.Cookie;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.mysql.MySQLContainer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = "spring.jpa.hibernate.ddl-auto=validate")
@AutoConfigureMockMvc
@Testcontainers
class JwtAuthFlowTests {
    @Container @ServiceConnection static MySQLContainer mysql = new MySQLContainer("mysql:8.4.13");
    @Autowired MockMvc mvc;
    @Autowired MemberRepository members;
    @Autowired RefreshTokenRepository refreshTokens;

    @Test
    @DisplayName("로그인 쿠키의 액세스 토큰으로 인증하고 리프레시 토큰은 한 번만 회전한다")
    void refreshRotatesAndRejectsReplay() throws Exception {
        mvc.perform(get("/api/v1/members/me").cookie(new Cookie("outpick_access", "invalid")))
                .andExpect(status().isUnauthorized());
        var client = AuthTestClient.create(mvc);
        client.signup("jwt@example.com", "password123", "테스터");
        client.login("jwt@example.com", "password123");
        assertThat(client.accessCookie().isHttpOnly()).isTrue();
        assertThat(client.refreshCookie().isHttpOnly()).isTrue();
        assertThat(refreshTokens.findAll()).isNotEmpty()
                .allSatisfy(token -> assertThat(token.getTokenHash()).isNotEqualTo(client.refreshCookie().getValue()));
        mvc.perform(client.auth(get("/api/v1/members/me"))).andExpect(status().isOk());

        mvc.perform(client.auth(post("/api/v1/auth/refresh"))).andExpect(status().isForbidden());

        var refreshed = mvc.perform(client.csrf(post("/api/v1/auth/refresh")))
                .andExpect(status().isNoContent()).andReturn();
        assertThat(refreshed.getResponse().getCookie("outpick_access")).isNotNull();
        assertThat(refreshed.getResponse().getCookie("outpick_refresh")).isNotNull();
        mvc.perform(client.csrf(post("/api/v1/auth/refresh"))).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/v1/auth/refresh")
                        .cookie(client.csrfCookie(), refreshed.getResponse().getCookie("outpick_refresh"))
                        .header(client.header(), client.token()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("로그아웃하면 기존 액세스·리프레시 토큰을 사용할 수 없다")
    void logoutRevokesBothTokens() throws Exception {
        var client = AuthTestClient.create(mvc);
        client.signup("logout-jwt@example.com", "password123", "테스터");
        client.login("logout-jwt@example.com", "password123");
        mvc.perform(client.csrf(post("/api/v1/auth/logout"))).andExpect(status().isNoContent());
        mvc.perform(client.auth(get("/api/v1/members/me"))).andExpect(status().isUnauthorized());
        mvc.perform(client.csrf(post("/api/v1/auth/refresh"))).andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("같은 리프레시 토큰의 동시 갱신은 한 요청만 성공한다")
    void concurrentRefreshOnlySucceedsOnce() throws Exception {
        var client = AuthTestClient.create(mvc);
        client.signup("concurrent-jwt@example.com", "password123", "테스터");
        client.login("concurrent-jwt@example.com", "password123");
        var ready = new CountDownLatch(2);
        var start = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            java.util.concurrent.Callable<Integer> refresh = () -> {
                ready.countDown();
                if (!start.await(5, TimeUnit.SECONDS)) throw new AssertionError("refresh did not start");
                return mvc.perform(client.csrf(post("/api/v1/auth/refresh")))
                        .andReturn().getResponse().getStatus();
            };
            var first = executor.submit(refresh);
            var second = executor.submit(refresh);
            assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            assertThat(java.util.List.of(first.get(10, TimeUnit.SECONDS), second.get(10, TimeUnit.SECONDS)))
                    .containsExactlyInAnyOrder(204, 401);
        }
    }
}
