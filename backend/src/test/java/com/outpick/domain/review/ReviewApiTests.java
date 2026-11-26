package com.outpick.domain.review;

import com.outpick.domain.member.AuthTestClient;
import com.jayway.jsonpath.JsonPath;
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
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.AbstractMockHttpServletRequestBuilder;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.mysql.MySQLContainer;
import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = "spring.jpa.hibernate.ddl-auto=validate")
@AutoConfigureMockMvc
@Testcontainers
class ReviewApiTests {
    @Container @ServiceConnection
    static MySQLContainer mysql = new MySQLContainer("mysql:8.4.13");

    @Autowired MockMvc mvc;
    @Autowired EntityManager entityManager;
    @Autowired TransactionTemplate transactions;
    @Autowired JdbcTemplate jdbc;

    @Test
    @DisplayName("후기는 장소별로 조회하고 작성자만 수정·삭제할 수 있다")
    void reviewsStayWithTheirPlaceAndOnlyAuthorCanEdit() throws Exception {
        var author = login("review-author@example.com");
        var other = login("review-other@example.com");
        long firstPlace = createPlace(PlacePublishStatus.PUBLISHED);
        long secondPlace = createPlace(PlacePublishStatus.PUBLISHED);

        var created = mvc.perform(author.withCsrf(post("/api/v1/places/{placeId}/reviews", firstPlace)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"body\":\"첫 장소 후기\"}")))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.placeId").value(firstPlace))
                .andExpect(jsonPath("$.body").value("첫 장소 후기")).andReturn();
        int reviewId = JsonPath.read(created.getResponse().getContentAsString(), "$.id");

        mvc.perform(get("/api/v1/places/{placeId}/reviews", firstPlace))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].id").value(reviewId));
        mvc.perform(get("/api/v1/places/{placeId}/reviews", secondPlace))
                .andExpect(status().isOk()).andExpect(jsonPath("$").isEmpty());
        mvc.perform(other.withCsrf(put("/api/v1/reviews/{id}", reviewId)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"body\":\"남의 수정\"}")))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("REVIEW_FORBIDDEN"));
        mvc.perform(other.withCsrf(delete("/api/v1/reviews/{id}", reviewId)))
                .andExpect(status().isForbidden());
        mvc.perform(author.withCsrf(put("/api/v1/reviews/{id}", reviewId)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"body\":\"수정 후기\"}")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.body").value("수정 후기"));
        mvc.perform(author.withCsrf(delete("/api/v1/reviews/{id}", reviewId)))
                .andExpect(status().isNoContent());
        mvc.perform(get("/api/v1/places/{placeId}/reviews", firstPlace))
                .andExpect(status().isOk()).andExpect(jsonPath("$").isEmpty());
    }

    @Test
    @DisplayName("작성은 로그인·CSRF·본문 검증을 거치며 비공개 장소에는 등록할 수 없다")
    void createRequiresSessionCsrfValidBodyAndPublishedPlace() throws Exception {
        var member = login("review-guard@example.com");
        long published = createPlace(PlacePublishStatus.PUBLISHED);
        long hidden = createPlace(PlacePublishStatus.HIDDEN);
        var guest = AuthTestClient.create(mvc);
        mvc.perform(guest.withCsrf(post("/api/v1/places/{placeId}/reviews", published))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"body\":\"후기\"}"))
                .andExpect(status().isUnauthorized());
        mvc.perform(post("/api/v1/places/{placeId}/reviews", published).cookie(member.cookies())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"body\":\"후기\"}"))
                .andExpect(status().isForbidden());
        mvc.perform(member.withCsrf(post("/api/v1/places/{placeId}/reviews", published)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"body\":\"   \"}")))
                .andExpect(status().isBadRequest());
        mvc.perform(member.withCsrf(post("/api/v1/places/{placeId}/reviews", hidden)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"body\":\"후기\"}")))
                .andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/places/{placeId}/reviews", hidden)).andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("신고는 중복 접수를 막고 운영자만 숨김·반려를 처리한다")
    void reportsAreModeratedOnlyByAnAdmin() throws Exception {
        var author = login("reported-author@example.com");
        var reporter = login("review-reporter@example.com");
        var admin = login("review-admin@example.com");
        jdbc.update("UPDATE `user` SET role = 'ADMIN' WHERE email = ?", "review-admin@example.com");
        long placeId = createPlace(PlacePublishStatus.PUBLISHED);
        var created = mvc.perform(author.withCsrf(post("/api/v1/places/{placeId}/reviews", placeId)
                .contentType(MediaType.APPLICATION_JSON).content("{\"body\":\"검토할 후기\"}")))
                .andExpect(status().isCreated()).andReturn();
        int reviewId = JsonPath.read(created.getResponse().getContentAsString(), "$.id");

        var report = mvc.perform(reporter.withCsrf(post("/api/v1/reviews/{id}/reports", reviewId)
                .contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"장소와 무관한 내용\"}")))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.status").value("PENDING")).andReturn();
        int reportId = JsonPath.read(report.getResponse().getContentAsString(), "$.id");
        mvc.perform(reporter.withCsrf(post("/api/v1/reviews/{id}/reports", reviewId)
                .contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"중복 신고\"}")))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("REPORT_ALREADY_EXISTS"));
        mvc.perform(get("/api/v1/admin/review-reports").cookie(reporter.cookies()))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/admin/review-reports").cookie(admin.cookies()))
                .andExpect(status().isOk()).andExpect(jsonPath("$[0].id").value(reportId));
        mvc.perform(reporter.withCsrf(patch("/api/v1/admin/reviews/{id}/hide", reviewId)))
                .andExpect(status().isForbidden());
        mvc.perform(admin.withCsrf(patch("/api/v1/admin/review-reports/{id}/dismiss", reportId)))
                .andExpect(status().isNoContent());
        mvc.perform(get("/api/v1/places/{placeId}/reviews", placeId))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(1));

        var second = login("second-reporter@example.com");
        mvc.perform(second.withCsrf(post("/api/v1/reviews/{id}/reports", reviewId)
                .contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"다시 검토 요청\"}")))
                .andExpect(status().isCreated());
        mvc.perform(admin.withCsrf(patch("/api/v1/admin/reviews/{id}/hide", reviewId)))
                .andExpect(status().isNoContent());
        mvc.perform(get("/api/v1/places/{placeId}/reviews", placeId))
                .andExpect(status().isOk()).andExpect(jsonPath("$").isEmpty());
        mvc.perform(get("/api/v1/admin/review-reports").cookie(admin.cookies()))
                .andExpect(status().isOk()).andExpect(jsonPath("$").isEmpty());
    }

    @Test
    @DisplayName("사진은 작성자만 세 장까지 등록하며 해당 공개 후기에서만 읽고 삭제할 수 있다")
    void photosStayWithTheirReviewAndAreValidated() throws Exception {
        var author = login("photo-author@example.com");
        var other = login("photo-other@example.com");
        long placeId = createPlace(PlacePublishStatus.PUBLISHED);
        var created = mvc.perform(author.withCsrf(post("/api/v1/places/{placeId}/reviews", placeId)
                .contentType(MediaType.APPLICATION_JSON).content("{\"body\":\"사진 후기\"}")))
                .andExpect(status().isCreated()).andReturn();
        int reviewId = JsonPath.read(created.getResponse().getContentAsString(), "$.id");
        byte[] png = pngBytes();

        mvc.perform(other.withCsrf(multipart("/api/v1/reviews/{id}/photos", reviewId)
                .file(new MockMultipartFile("file", "visit.png", "image/png", png))))
                .andExpect(status().isForbidden());
        mvc.perform(author.withCsrf(multipart("/api/v1/reviews/{id}/photos", reviewId)
                .file(new MockMultipartFile("file", "fake.png", "image/png", "not an image".getBytes()))))
                .andExpect(status().isBadRequest());

        long firstAsset = -1;
        for (int index = 0; index < 3; index++) {
            var response = mvc.perform(author.withCsrf(multipart("/api/v1/reviews/{id}/photos", reviewId)
                    .file(new MockMultipartFile("file", "visit.png", "image/png", png))))
                    .andExpect(status().isCreated()).andExpect(jsonPath("$.width").value(2)).andReturn();
            if (index == 0) firstAsset = ((Number) JsonPath.read(response.getResponse().getContentAsString(), "$.id")).longValue();
        }
        mvc.perform(author.withCsrf(multipart("/api/v1/reviews/{id}/photos", reviewId)
                .file(new MockMultipartFile("file", "visit.png", "image/png", png))))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("PHOTO_LIMIT"));
        mvc.perform(get("/api/v1/places/{placeId}/reviews", placeId))
                .andExpect(status().isOk()).andExpect(jsonPath("$[0].photos.length()").value(3));
        mvc.perform(get("/api/v1/review-photos/{id}", firstAsset))
                .andExpect(status().isOk()).andExpect(content().contentType("image/png"))
                .andExpect(content().bytes(png));
        mvc.perform(other.withCsrf(delete("/api/v1/reviews/{reviewId}/photos/{assetId}", reviewId, firstAsset)))
                .andExpect(status().isForbidden());
        mvc.perform(author.withCsrf(delete("/api/v1/reviews/{reviewId}/photos/{assetId}", reviewId, firstAsset)))
                .andExpect(status().isNoContent());
        mvc.perform(get("/api/v1/review-photos/{id}", firstAsset)).andExpect(status().isNotFound());
    }

    private byte[] pngBytes() throws Exception {
        var output = new ByteArrayOutputStream();
        ImageIO.write(new BufferedImage(2, 2, BufferedImage.TYPE_INT_RGB), "png", output);
        return output.toByteArray();
    }

    private long createPlace(PlacePublishStatus status) {
        return transactions.execute(transaction -> {
            var region = Region.builder().name("후기 테스트 지역").build();
            entityManager.persist(region);
            var place = Place.builder().region(region).name("후기 테스트 장소").publishStatus(status).build();
            entityManager.persist(place);
            return place.getId();
        });
    }

    private AuthTestClient login(String email) throws Exception {
        var member = AuthTestClient.create(mvc);
        member.signup(email, "password123", "후기 회원");
        member.login(email, "password123");
        return member;
    }
}
