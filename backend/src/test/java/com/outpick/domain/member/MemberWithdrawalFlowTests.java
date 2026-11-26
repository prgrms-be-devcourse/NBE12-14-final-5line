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
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.mysql.MySQLContainer;
import org.springframework.transaction.support.TransactionTemplate;
import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
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
        var reviewResponse = mvc.perform(first.csrf(post("/api/v1/places/{placeId}/reviews", placeId))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"body\":\"탈퇴 전 후기\"}"))
                .andExpect(status().isCreated()).andReturn();
        long reviewId = ((Number) com.jayway.jsonpath.JsonPath.read(
                reviewResponse.getResponse().getContentAsString(), "$.id")).longValue();
        var image = new ByteArrayOutputStream();
        ImageIO.write(new BufferedImage(2, 2, BufferedImage.TYPE_INT_RGB), "png", image);
        mvc.perform(first.csrf(multipart("/api/v1/reviews/{id}/photos", reviewId)
                        .file(new MockMultipartFile("file", "visit.png", "image/png", image.toByteArray()))))
                .andExpect(status().isCreated());
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM review WHERE review_id = ?", Integer.class, reviewId)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM review_media WHERE review_id = ?", Integer.class, reviewId)).isEqualTo(1);
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
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM review WHERE review_id = ?", Integer.class, reviewId)).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM review_media WHERE review_id = ?", Integer.class, reviewId)).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM media_asset", Integer.class)).isZero();
        mvc.perform(get("/api/v1/places/{placeId}/reviews", placeId))
                .andExpect(status().isOk()).andExpect(jsonPath("$").isEmpty());
        assertThat(resetService.isValid(token)).isFalse();
        mvc.perform(second.auth(get("/api/v1/members/me")))
                .andExpect(status().isUnauthorized());
        var fresh = AuthTestClient.create(mvc);
        mvc.perform(fresh.csrf(post("/api/v1/auth/login"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"leave@example.com\",\"password\":\"old-pass\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("후기 신고자가 탈퇴해도 신고 기록은 남고 신고자 연결은 제거된다")
    void withdrawalAnonymizesReporterWithoutDeletingReport() throws Exception {
        var author = AuthTestClient.create(mvc);
        author.signup("review-owner@example.com", "old-pass", "tester");
        author.login("review-owner@example.com", "old-pass");
        var reporter = AuthTestClient.create(mvc);
        reporter.signup("review-reporter@example.com", "old-pass", "tester");
        reporter.login("review-reporter@example.com", "old-pass");
        long placeId = transactions.execute(transaction -> {
            var region = Region.builder().name("신고 테스트 지역").build();
            entityManager.persist(region);
            var place = Place.builder().region(region).name("신고 테스트 장소")
                    .publishStatus(PlacePublishStatus.PUBLISHED).build();
            entityManager.persist(place);
            return place.getId();
        });
        var created = mvc.perform(author.csrf(post("/api/v1/places/{placeId}/reviews", placeId))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"body\":\"신고 대상 후기\"}"))
                .andExpect(status().isCreated()).andReturn();
        long reviewId = ((Number) com.jayway.jsonpath.JsonPath.read(
                created.getResponse().getContentAsString(), "$.id")).longValue();
        mvc.perform(reporter.csrf(post("/api/v1/reviews/{id}/reports", reviewId))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"신고 사유\"}"))
                .andExpect(status().isCreated());
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM review_report WHERE review_id = ?", Integer.class, reviewId)).isEqualTo(1);

        mvc.perform(reporter.csrf(delete("/api/v1/members/me")).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"password\":\"old-pass\",\"confirmed\":true}"))
                .andExpect(status().isOk());

        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM review WHERE review_id = ?", Integer.class, reviewId)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM review_report WHERE review_id = ? AND reporter_id IS NULL", Integer.class, reviewId)).isEqualTo(1);
    }

}
