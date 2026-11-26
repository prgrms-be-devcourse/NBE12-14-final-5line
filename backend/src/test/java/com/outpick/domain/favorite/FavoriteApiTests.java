package com.outpick.domain.favorite;

import com.jayway.jsonpath.JsonPath;
import com.outpick.domain.member.AuthTestClient;
import com.outpick.domain.place.entity.Place;
import com.outpick.domain.place.entity.PlacePublishStatus;
import com.outpick.domain.place.entity.PlaceRepresentativeImage;
import com.outpick.domain.place.entity.Region;
import com.outpick.domain.place.repository.PlaceRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.json.JsonCompareMode;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.mysql.MySQLContainer;

import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = "spring.jpa.hibernate.ddl-auto=validate")
@AutoConfigureMockMvc
@Testcontainers
class FavoriteApiTests {
    private static final String FAVORITES = "/api/v1/members/me/favorites";
    private static final String NOTIFICATION_SETTINGS = FAVORITES + "/{placeId}/notification-settings";

    @Container
    @ServiceConnection
    static MySQLContainer mysql = new MySQLContainer("mysql:8.4.13");

    @Autowired
    MockMvc mvc;
    @Autowired
    JdbcTemplate jdbc;
    @Autowired
    PlaceRepository places;
    @Autowired
    EntityManager entityManager;
    @Autowired
    TransactionTemplate transactions;

    @Test
    @DisplayName("관심 장소는 중복 저장되지 않고 회원별로 조회·해제된다")
    void storesOneFavoritePerMemberAndRemovesOnlyTheCurrentMembersFavorite() throws Exception {
        var first = login("first-favorite@example.com");
        var second = login("second-favorite@example.com");
        long placeId = createPlace();

        mvc.perform(first.withCsrf(createFavorite(placeId)))
                .andExpect(status().isCreated())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.placeId").value(placeId))
                .andExpect(jsonPath("$.message").value("관심 장소에 저장했습니다."));
        var saved = jdbc.queryForMap("SELECT favorite_id, created_at FROM favorite WHERE place_id = ?", placeId);
        mvc.perform(first.withCsrf(createFavorite(placeId)))
                .andExpect(status().isConflict())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.code").value("FAVORITE_ALREADY_EXISTS"))
                .andExpect(jsonPath("$.detail").value("이미 관심 장소에 저장된 장소입니다."));
        assertThat(jdbc.queryForMap("SELECT favorite_id, created_at FROM favorite WHERE place_id = ?", placeId))
                .isEqualTo(saved);
        mvc.perform(get(FAVORITES).cookie(first.cookies()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].id").value(placeId));
        mvc.perform(get(FAVORITES).cookie(second.cookies()))
                .andExpect(status().isOk()).andExpect(jsonPath("$").isEmpty());

        mvc.perform(second.withCsrf(createFavorite(placeId))).andExpect(status().isCreated());
        mvc.perform(first.withCsrf(delete(FAVORITES + "/{placeId}", placeId)))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.placeId").value(placeId))
                .andExpect(jsonPath("$.message").value("관심 장소에서 해제했습니다."));
        mvc.perform(first.withCsrf(delete(FAVORITES + "/{placeId}", placeId))).andExpect(status().isOk());
        mvc.perform(get(FAVORITES).cookie(first.cookies()))
                .andExpect(status().isOk()).andExpect(jsonPath("$").isEmpty());
        mvc.perform(get(FAVORITES).cookie(second.cookies()))
                .andExpect(status().isOk()).andExpect(jsonPath("$[0].id").value(placeId));
        mvc.perform(first.withCsrf(createFavorite(placeId))).andExpect(status().isCreated());
    }

    @Test
    @DisplayName("관심 장소는 로그인과 CSRF를 검증하고 공개 장소는 비회원도 조회한다")
    void requiresSessionAndCsrfAndKeepsPublicPlaceAccess() throws Exception {
        var member = login("protected-favorite@example.com");
        long placeId = createPlace();

        mvc.perform(get(FAVORITES)).andExpect(status().isUnauthorized());
        mvc.perform(createFavorite(placeId).cookie(member.cookies()))
                .andExpect(status().isForbidden());
        mvc.perform(member.withCsrf(createFavorite(placeId))).andExpect(status().isCreated());
        mvc.perform(delete(FAVORITES + "/{placeId}", placeId).cookie(member.cookies()))
                .andExpect(status().isForbidden());
        mvc.perform(get(FAVORITES).cookie(member.cookies()))
                .andExpect(status().isOk()).andExpect(jsonPath("$[0].id").value(placeId));
        mvc.perform(get("/api/v1/places/{placeId}", placeId))
                .andExpect(status().isOk()).andExpect(jsonPath("$.isNotified").doesNotHaveJsonPath());
    }

    @Test
    @DisplayName("비공개 장소는 본인 관심 목록에 최소 정보만 표시하고 해제를 허용한다")
    void showsOnlyBasicFieldsForUnavailableFavoritesAndAllowsRemoval() throws Exception {
        var member = login("hidden-favorite@example.com");
        var otherMember = login("other-hidden-favorite@example.com");
        long placeId = createPlace();
        String imageUrl = "https://tong.visitkorea.or.kr/cms/resource/1/123_image2_1.jpg";
        String attribution = "한국관광공사 TourAPI 제공 · 테스트 사진";
        String sourceUrl = "https://www.data.go.kr/data/15101578/openapi.do";
        transactions.executeWithoutResult(transaction -> {
            var place = entityManager.find(Place.class, placeId);
            entityManager.persist(new PlaceRepresentativeImage(
                    place, imageUrl, attribution, sourceUrl, "Type3", LocalDateTime.of(2026, 11, 19, 12, 0)));
        });
        mvc.perform(member.withCsrf(createFavorite(placeId))).andExpect(status().isCreated());
        mvc.perform(member.withCsrf(notificationSetting(placeId, true))).andExpect(status().isOk());
        var enabledAt = notificationEnabledAt(placeId);

        var place = places.findById(placeId).orElseThrow();
        place.changePublishStatus(PlacePublishStatus.HIDDEN);
        places.save(place);
        mvc.perform(member.withCsrf(createFavorite(placeId)))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("PLACE_NOT_FOUND"));
        mvc.perform(member.withCsrf(createFavorite(Long.MAX_VALUE)))
                .andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/places/{placeId}", placeId)).andExpect(status().isNotFound());
        mvc.perform(get(FAVORITES).cookie(member.cookies()))
                .andExpect(status().isOk())
                .andExpect(content().json("""
                        [{"id":%d,"name":"관심 장소","publishStatus":"HIDDEN"}]
                        """.formatted(placeId), JsonCompareMode.STRICT));
        mvc.perform(get(FAVORITES).cookie(otherMember.cookies()))
                .andExpect(status().isOk()).andExpect(jsonPath("$").isEmpty());
        assertThat(favoriteCount(placeId)).isEqualTo(1);
        mvc.perform(member.withCsrf(notificationSetting(placeId, false)))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("FAVORITE_NOT_FOUND"));
        assertThat(notificationEnabledAt(placeId)).isEqualTo(enabledAt);

        place.changePublishStatus(PlacePublishStatus.PUBLISHED);
        places.save(place);
        mvc.perform(get(FAVORITES).cookie(member.cookies()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].publishStatus").value("PUBLISHED"))
                .andExpect(jsonPath("$[0].isNotified").value(true))
                .andExpect(jsonPath("$[0].address").hasJsonPath())
                .andExpect(jsonPath("$[0].representativeImage").value(imageUrl))
                .andExpect(jsonPath("$[0].imageAttribution").value(attribution))
                .andExpect(jsonPath("$[0].imageSourceUrl").value(sourceUrl))
                .andExpect(jsonPath("$[0].imageLicense").value("Type3"));
        place.changePublishStatus(PlacePublishStatus.HIDDEN);
        places.save(place);

        mvc.perform(member.withCsrf(delete(FAVORITES + "/{placeId}", placeId))).andExpect(status().isOk());
        assertThat(favoriteCount(placeId)).isZero();
        mvc.perform(get(FAVORITES).cookie(member.cookies()))
                .andExpect(status().isOk()).andExpect(jsonPath("$").isEmpty());
    }

    @Test
    @DisplayName("검수 전으로 돌아간 장소는 관심 기록을 보존하고 목록에서 제외한다")
    void excludesDraftPlacesFromFavoriteList() throws Exception {
        var member = login("draft-favorite@example.com");
        long placeId = createPlace();
        mvc.perform(member.withCsrf(createFavorite(placeId))).andExpect(status().isCreated());

        var place = places.findById(placeId).orElseThrow();
        place.changePublishStatus(PlacePublishStatus.DRAFT);
        places.save(place);

        mvc.perform(get(FAVORITES).cookie(member.cookies()))
                .andExpect(status().isOk()).andExpect(content().json("[]", JsonCompareMode.STRICT));
        assertThat(favoriteCount(placeId)).isEqualTo(1);
    }

    @Test
    @DisplayName("동시에 같은 관심 장소를 저장하면 한 건은 성공하고 한 건은 중복 오류를 반환한다")
    void concurrentSavesReturnOneSuccessAndOneConflict() throws Exception {
        var member = login("concurrent-favorite@example.com");
        long placeId = createPlace();
        var ready = new CountDownLatch(2);
        var start = new CountDownLatch(1);

        try (var executor = Executors.newFixedThreadPool(2)) {
            var first = executor.submit(() -> saveTogether(member, placeId, ready, start));
            var second = executor.submit(() -> saveTogether(member, placeId, ready, start));
            assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            assertThat(java.util.List.of(first.get(10, TimeUnit.SECONDS), second.get(10, TimeUnit.SECONDS)))
                    .containsExactlyInAnyOrder(201, 409);
        }
        assertThat(favoriteCount(placeId)).isEqualTo(1);
    }

    @Test
    @DisplayName("알림 상태는 회원별로 바뀌고 관심 목록에는 기존 장소 정보와 함께 표시된다")
    void changesOnlyTheCurrentMembersSettingAndKeepsFavoriteListFields() throws Exception {
        var first = login("first-notification@example.com");
        var second = login("second-notification@example.com");
        long placeId = createPlace();
        long latestPlaceId = createPlace();
        mvc.perform(first.withCsrf(createFavorite(placeId))).andExpect(status().isCreated());
        mvc.perform(first.withCsrf(createFavorite(latestPlaceId))).andExpect(status().isCreated());
        mvc.perform(second.withCsrf(notificationSetting(placeId, true)))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("FAVORITE_NOT_FOUND"));
        mvc.perform(second.withCsrf(createFavorite(placeId))).andExpect(status().isCreated());

        mvc.perform(first.withCsrf(notificationSetting(placeId, true)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.placeId").value(placeId))
                .andExpect(jsonPath("$.isNotified").value(true));
        mvc.perform(get(FAVORITES).cookie(first.cookies()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(latestPlaceId))
                .andExpect(jsonPath("$[0].publishStatus").value("PUBLISHED"))
                .andExpect(jsonPath("$[0].isNotified").value(false))
                .andExpect(jsonPath("$[1].id").value(placeId))
                .andExpect(jsonPath("$[1].name").value("관심 장소"))
                .andExpect(jsonPath("$[1].representativeImage").value(nullValue()))
                .andExpect(jsonPath("$[1].imageAttribution").value(nullValue()))
                .andExpect(jsonPath("$[1].imageSourceUrl").value(nullValue()))
                .andExpect(jsonPath("$[1].imageLicense").value(nullValue()))
                .andExpect(jsonPath("$[1].place").doesNotHaveJsonPath())
                .andExpect(jsonPath("$[1].isNotified").value(true));
        mvc.perform(get(FAVORITES).cookie(second.cookies()))
                .andExpect(status().isOk()).andExpect(jsonPath("$[0].isNotified").value(false));
    }

    @Test
    @DisplayName("반복 활성화는 시각을 보존하고 해제 후 다시 켜면 새 시각을 기록한다")
    void tracksOnlyTheCurrentActivationAndResetsOnResave() throws Exception {
        var member = login("activation-time@example.com");
        long placeId = createPlace();
        mvc.perform(member.withCsrf(createFavorite(placeId))).andExpect(status().isCreated());
        assertThat(notificationEnabledAt(placeId)).isNull();

        mvc.perform(member.withCsrf(notificationSetting(placeId, true))).andExpect(status().isOk());
        var firstEnabledAt = notificationEnabledAt(placeId);
        assertThat(firstEnabledAt).isNotNull();
        mvc.perform(member.withCsrf(notificationSetting(placeId, true))).andExpect(status().isOk());
        assertThat(notificationEnabledAt(placeId)).isEqualTo(firstEnabledAt);

        mvc.perform(member.withCsrf(notificationSetting(placeId, false)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.isNotified").value(false));
        assertThat(notificationEnabledAt(placeId)).isNull();
        mvc.perform(member.withCsrf(notificationSetting(placeId, true))).andExpect(status().isOk());
        assertThat(notificationEnabledAt(placeId)).isAfter(firstEnabledAt);

        mvc.perform(member.withCsrf(delete(FAVORITES + "/{placeId}", placeId))).andExpect(status().isOk());
        mvc.perform(member.withCsrf(createFavorite(placeId))).andExpect(status().isCreated());
        mvc.perform(get(FAVORITES).cookie(member.cookies()))
                .andExpect(status().isOk()).andExpect(jsonPath("$[0].isNotified").value(false));
        assertThat(notificationEnabledAt(placeId)).isNull();
    }

    @Test
    @DisplayName("알림 설정 변경은 세션과 CSRF, 양수 장소 ID와 JSON 불리언을 검증한다")
    void validatesAuthenticationCsrfAndNotificationInput() throws Exception {
        var member = login("invalid-notification@example.com");
        long placeId = createPlace();
        mvc.perform(member.withCsrf(createFavorite(placeId))).andExpect(status().isCreated());
        var anonymous = AuthTestClient.create(mvc);

        mvc.perform(anonymous.withCsrf(notificationSetting(placeId, true))).andExpect(status().isUnauthorized());
        mvc.perform(notificationSetting(placeId, true).cookie(member.cookies())).andExpect(status().isForbidden());
        mvc.perform(member.withCsrf(notificationSetting(0, true))).andExpect(status().isBadRequest());
        for (String body : List.of("{}", "{\"isNotified\":null}", "{\"isNotified\":\"true\"}", "{\"isNotified\":1}")) {
            mvc.perform(member.withCsrf(patch(NOTIFICATION_SETTINGS, placeId))
                            .contentType(MediaType.APPLICATION_JSON).content(body))
                    .andExpect(status().isBadRequest());
        }
        assertThat(notificationEnabledAt(placeId)).isNull();
    }

    @Test
    @DisplayName("동시 ON·OFF 요청을 처리한 뒤에도 상태와 활성화 시각이 일치한다")
    void concurrentUpdatesKeepSettingAndActivationTimeConsistent() throws Exception {
        var member = login("concurrent-notification@example.com");
        long placeId = createPlace();
        mvc.perform(member.withCsrf(createFavorite(placeId))).andExpect(status().isCreated());
        var ready = new CountDownLatch(2);
        var start = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            var on = executor.submit(() -> updateTogether(member, placeId, true, ready, start));
            var off = executor.submit(() -> updateTogether(member, placeId, false, ready, start));
            assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            assertThat(on.get(10, TimeUnit.SECONDS)).isEqualTo(200);
            assertThat(off.get(10, TimeUnit.SECONDS)).isEqualTo(200);
        }
        var result = mvc.perform(get(FAVORITES).cookie(member.cookies())).andExpect(status().isOk()).andReturn();
        boolean enabled = JsonPath.read(result.getResponse().getContentAsString(), "$[0].isNotified");
        assertThat(notificationEnabledAt(placeId) != null).isEqualTo(enabled);
    }

    private int updateTogether(AuthTestClient member, long placeId, boolean enabled,
                               CountDownLatch ready, CountDownLatch start) throws Exception {
        ready.countDown();
        assertThat(start.await(5, TimeUnit.SECONDS)).isTrue();
        return mvc.perform(member.withCsrf(notificationSetting(placeId, enabled))).andReturn().getResponse().getStatus();
    }

    private MockHttpServletRequestBuilder notificationSetting(long placeId, boolean enabled) {
        return patch(NOTIFICATION_SETTINGS, placeId).contentType(MediaType.APPLICATION_JSON)
                .content("{\"isNotified\":" + enabled + "}");
    }

    private LocalDateTime notificationEnabledAt(long placeId) {
        return jdbc.queryForObject("SELECT notified_enabled_at FROM favorite WHERE place_id = ?", LocalDateTime.class, placeId);
    }

    private int saveTogether(AuthTestClient member, long placeId, CountDownLatch ready, CountDownLatch start)
            throws Exception {
        ready.countDown();
        assertThat(start.await(5, TimeUnit.SECONDS)).isTrue();
        return mvc.perform(member.withCsrf(createFavorite(placeId)))
                .andReturn().getResponse().getStatus();
    }

    private MockHttpServletRequestBuilder createFavorite(long placeId) {
        return post(FAVORITES).contentType(MediaType.APPLICATION_JSON)
                .content("{\"placeId\":" + placeId + "}");
    }

    @Test
    @DisplayName("관심 장소 등록은 장소 ID의 필수값과 양수 조건을 검증한다")
    void validatesPlaceIdInRequestBody() throws Exception {
        var member = login("invalid-favorite@example.com");
        for (String body : java.util.List.of("{}", "{\"placeId\":null}", "{\"placeId\":0}", "{\"placeId\":-1}")) {
            mvc.perform(member.withCsrf(post(FAVORITES)).contentType(MediaType.APPLICATION_JSON).content(body))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                    .andExpect(jsonPath("$.fieldErrors[0].field").value("placeId"));
        }
    }

    private long createPlace() {
        return transactions.execute(transaction -> {
            var region = Region.builder().name("관심 장소 테스트 지역").build();
            entityManager.persist(region);
            var place = Place.builder().region(region).name("관심 장소")
                    .publishStatus(PlacePublishStatus.PUBLISHED).build();
            entityManager.persist(place);
            return place.getId();
        });
    }

    private int favoriteCount(long placeId) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM favorite WHERE place_id = ?", Integer.class, placeId);
    }

    private AuthTestClient login(String email) throws Exception {
        var member = AuthTestClient.create(mvc);
        member.signup(email, "password123", "관심 장소 회원");
        member.login(email, "password123");
        return member;
    }
}
