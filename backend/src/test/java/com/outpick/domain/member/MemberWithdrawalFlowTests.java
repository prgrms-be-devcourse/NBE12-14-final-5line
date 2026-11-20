package com.outpick.domain.member;

import com.outpick.domain.member.repository.MemberRepository;
import com.outpick.domain.member.repository.PasswordResetTokenRepository;
import com.outpick.domain.member.service.PasswordResetService;
import com.outpick.domain.place.entity.Place;
import com.outpick.domain.place.entity.PlacePublishStatus;
import com.outpick.domain.place.entity.Region;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.mysql.MySQLContainer;
import org.springframework.transaction.support.TransactionTemplate;

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
    @Autowired EntityManager entityManager;
    @Autowired TransactionTemplate transactions;
    @Autowired JdbcTemplate jdbc;

    @Test
    @DisplayName("비밀번호와 최종 확인을 거친 탈퇴가 계정·토큰·기존 세션을 정리하는지 검증한다")
    void withdrawalRequiresConfirmationAndPasswordThenRemovesAccountTokensAndSessions() throws Exception {
        var first = AuthTestClient.create(mvc);
        first.signup("leave@example.com", "old-pass", "tester");
        first.login("leave@example.com", "old-pass");
        long placeId = transactions.execute(transaction -> {
            var region = Region.builder().name("탈퇴 테스트 지역").build();
            entityManager.persist(region);
            var place = Place.builder().region(region).name("탈퇴 테스트 장소")
                    .publishStatus(PlacePublishStatus.PUBLISHED).build();
            entityManager.persist(place);
            return place.getId();
        });
        mvc.perform(first.csrf(post("/api/v1/members/me/favorites"))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"placeId\":" + placeId + "}"))
                .andExpect(status().isCreated());
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM favorite WHERE place_id = ?", Integer.class, placeId)).isEqualTo(1);
        var second = AuthTestClient.create(mvc);
        second.login("leave@example.com", "old-pass");
        String token = resetService.request("leave@example.com").orElseThrow().token();
        assertThat(tokens.count()).isEqualTo(1);

        mvc.perform(first.csrf(delete("/api/v1/members/me"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"password\":\"old-pass\",\"confirmed\":false}"))
                .andExpect(status().isBadRequest());
        mvc.perform(first.csrf(delete("/api/v1/members/me"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"password\":\"wrong\",\"confirmed\":true}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("WITHDRAWAL_PASSWORD_MISMATCH"));
        assertThat(members.findByEmail("leave@example.com")).isPresent();
        assertThat(resetService.isValid(token)).isTrue();

        mvc.perform(first.csrf(delete("/api/v1/members/me"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"password\":\"old-pass\",\"confirmed\":true}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("회원 탈퇴가 완료되었습니다."));

        assertThat(members.findByEmail("leave@example.com")).isEmpty();
        assertThat(tokens.count()).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM favorite WHERE place_id = ?", Integer.class, placeId)).isZero();
        assertThat(resetService.isValid(token)).isFalse();
        mvc.perform(second.auth(get("/api/v1/members/me")))
                .andExpect(status().isUnauthorized());
        var fresh = AuthTestClient.create(mvc);
        mvc.perform(fresh.csrf(post("/api/v1/auth/login"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"leave@example.com\",\"password\":\"old-pass\"}"))
                .andExpect(status().isUnauthorized());
    }

}
