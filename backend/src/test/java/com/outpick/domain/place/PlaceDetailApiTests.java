package com.outpick.domain.place;

import com.outpick.domain.place.entity.ActivityCategory;
import com.outpick.domain.place.entity.ActivityType;
import com.outpick.domain.place.entity.Place;
import com.outpick.domain.place.entity.PlaceActivity;
import com.outpick.domain.place.entity.PlaceActivitySeason;
import com.outpick.domain.place.entity.PlaceNotice;
import com.outpick.domain.place.entity.PlacePublishStatus;
import com.outpick.domain.place.entity.PlaceRepresentativeImage;
import com.outpick.domain.place.entity.PlaceOperatingInfo;
import com.outpick.domain.place.importer.TourApiImportRecord;
import com.outpick.domain.place.entity.Region;
import com.outpick.domain.place.entity.SourceRecord;
import jakarta.persistence.EntityManager;
import org.hibernate.SessionFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.mysql.MySQLContainer;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {"spring.jpa.hibernate.ddl-auto=validate", "spring.jpa.properties.hibernate.generate_statistics=true"})
@AutoConfigureMockMvc
@Import(PlaceDetailApiTests.TestTime.class)
@Testcontainers
@Transactional
class PlaceDetailApiTests {
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 11, 13, 12, 0);

    @TestConfiguration(proxyBeanMethods = false)
    static class TestTime {
        @Bean
        @Primary
        Clock fixedPlaceClock() {
            return Clock.fixed(Instant.parse("2026-11-13T03:00:00Z"), ZoneId.of("Asia/Seoul"));
        }
    }

    @Container
    @ServiceConnection
    static MySQLContainer mysql = new MySQLContainer("mysql:8.4.13");

    @Autowired MockMvc mvc;
    @Autowired EntityManager em;
    private Place published;
    private Place draft;
    private Place hidden;
    private Place empty;

    @BeforeEach
    void fixtures() {
        var region = persist(Region.builder().name("테스트 지역").build());
        published = persist(Place.builder().region(region).name("공개 장소").nameAlias("별칭")
                .introduce("검수한 소개").address("확인된 주소").latitude(new BigDecimal("37.37"))
                .longitude(new BigDecimal("128.39")).homepage("https://example.com")
                .reviewedAt(NOW.minusDays(1)).version("내부 버전").publishStatus(PlacePublishStatus.PUBLISHED).build());
        draft = persist(Place.builder().region(region).name("검수 전").build());
        hidden = persist(Place.builder().region(region).name("비공개").publishStatus(PlacePublishStatus.HIDDEN).build());
        empty = persist(Place.builder().region(region).name("기본 정보만").publishStatus(PlacePublishStatus.PUBLISHED).build());
        var category = persist(ActivityCategory.builder().name("테스트 분류").build());
        var ski = activity(category, "스키", "OUTDOOR");
        persist(PlaceActivitySeason.builder().placeActivity(ski).season("WINTER").build());
        persist(PlaceActivitySeason.builder().placeActivity(ski).season("SUMMER").build());
        activity(category, "보드", "OUTDOOR");
        var climb = activity(category, "클라이밍", "INDOOR");
        persist(PlaceActivitySeason.builder().placeActivity(climb).season("WINTER").build());
        var verified = persist(SourceRecord.builder().place(published).type("OFFICIAL")
                .url("https://example.com/info").verifiedFields("address")
                .verifiedAt(NOW.minusDays(1)).verifierId(999L).usageTermsMemo("내부 검토 메모").build());
        var unverified = persist(SourceRecord.builder().place(published).url("https://example.com/unverified").build());
        notice(verified, "시작 경계", PlacePublishStatus.PUBLISHED, NOW, NOW.plusDays(1));
        notice(verified, "기한 없음", PlacePublishStatus.PUBLISHED, null, null);
        notice(verified, "예정 공지", PlacePublishStatus.PUBLISHED, NOW.plusSeconds(1), null);
        notice(verified, "종료 경계", PlacePublishStatus.PUBLISHED, null, NOW);
        notice(verified, "비공개 공지", PlacePublishStatus.HIDDEN, null, null);
        notice(verified, "검수 전 공지", PlacePublishStatus.DRAFT, null, null);
        notice(unverified, "미확인 출처 공지", PlacePublishStatus.PUBLISHED, null, null);
        persist(SourceRecord.builder().place(empty).url("https://example.com/other").verifiedAt(NOW).build());
        em.flush();
        em.clear();
    }

    @Test
    void returnsPublicDetailWithoutInternalFieldsOrInventedInformation() throws Exception {
        mvc.perform(get("/api/v1/places/{id}", published.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(published.getId()))
                .andExpect(jsonPath("$.nameAlias").value("별칭"))
                .andExpect(jsonPath("$.description").value("검수한 소개"))
                .andExpect(jsonPath("$.address").value("확인된 주소"))
                .andExpect(jsonPath("$.latitude").value(37.37))
                .andExpect(jsonPath("$.homepage").value("https://example.com"))
                .andExpect(jsonPath("$.reviewedAt").value("2026-11-12T12:00:00"))
                .andExpect(jsonPath("$.operationStatus").value(nullValue()))
                .andExpect(jsonPath("$.representativeImage").value(nullValue()))
                .andExpect(jsonPath("$.version").doesNotExist())
                .andExpect(jsonPath("$.publishStatus").doesNotExist())
                .andExpect(jsonPath("$.data").doesNotExist())
                .andExpect(jsonPath("$.sources", hasSize(1)))
                .andExpect(jsonPath("$.sources[0].url").value("https://example.com/info"))
                .andExpect(jsonPath("$.sources[0].verifiedFields").value("address"))
                .andExpect(jsonPath("$.sources[0].verifiedAt").value("2026-11-12T12:00:00"))
                .andExpect(jsonPath("$.sources[0].verifierId").doesNotExist())
                .andExpect(jsonPath("$.sources[0].usageTermsMemo").doesNotExist());
    }

    @Test
    void groupsSeasonsUnderTheCorrectActivityWithoutDuplicatingActivities() throws Exception {
        mvc.perform(get("/api/v1/places/{id}", published.getId()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.activities", hasSize(3)))
                .andExpect(jsonPath("$.activities[0].activityTypeName").value("스키"))
                .andExpect(jsonPath("$.activities[0].seasons", containsInAnyOrder("SUMMER", "WINTER")))
                .andExpect(jsonPath("$.activities[1].activityTypeName").value("보드"))
                .andExpect(jsonPath("$.activities[1].seasons", hasSize(0)))
                .andExpect(jsonPath("$.activities[2].environmentType").value("INDOOR"))
                .andExpect(jsonPath("$.activities[2].seasons", containsInAnyOrder("WINTER")));
    }

    @Test
    void showsOnlyPublishedActiveNoticesWithVerifiedSources() throws Exception {
        mvc.perform(get("/api/v1/places/{id}", published.getId()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.notices", hasSize(2)))
                .andExpect(jsonPath("$.notices[*].title", containsInAnyOrder("시작 경계", "기한 없음")))
                .andExpect(jsonPath("$.notices[0].priority").doesNotHaveJsonPath())
                .andExpect(jsonPath("$.notices[1].priority").doesNotHaveJsonPath());
    }

    @Test
    @DisplayName("공지 전용 출처는 연결된 공지가 장소 상세에 표시되는 동안만 공개된다")
    void exposesNoticeOnlySourcesForVisibleNotices() throws Exception {
        var active = persist(SourceRecord.builder().place(published).url("https://example.com/active")
                .verifiedFields(SourceRecord.VERIFIED_FIELD_NOTICE).verifiedAt(NOW).build());
        var expired = persist(SourceRecord.builder().place(published).url("https://example.com/expired")
                .verifiedFields(SourceRecord.VERIFIED_FIELD_NOTICE).verifiedAt(NOW).build());
        notice(active, "유효한 공지", PlacePublishStatus.PUBLISHED, NOW, NOW.plusDays(1));
        notice(expired, "종료된 공지", PlacePublishStatus.PUBLISHED, null, NOW);
        em.flush();
        em.clear();

        mvc.perform(get("/api/v1/places/{id}", published.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.notices[*].title", containsInAnyOrder("시작 경계", "기한 없음", "유효한 공지")))
                .andExpect(jsonPath("$.sources[*].url",
                        containsInAnyOrder("https://example.com/info", "https://example.com/active")));
    }

    @Test
    void returnsEmptyCollectionsWhenOptionalDataDoesNotExist() throws Exception {
        // 이 장소에는 다른 장소와 구분되는 출처만 있고 활동/공지는 없다.
        mvc.perform(get("/api/v1/places/{id}", empty.getId()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.activities", hasSize(0)))
                .andExpect(jsonPath("$.notices", hasSize(0)))
                .andExpect(jsonPath("$.sources[0].url").value("https://example.com/other"))
                .andExpect(jsonPath("$.address").value(nullValue()));
    }

    @Test
    void placeWithoutAnyRelatedInformationStillReturnsAUsableDetail() throws Exception {
        var region = persist(Region.builder().name("추가 지역").build());
        var basic = persist(Place.builder().region(region).name("연관 정보 없음")
                .publishStatus(PlacePublishStatus.PUBLISHED).build());
        em.flush();
        em.clear();
        mvc.perform(get("/api/v1/places/{id}", basic.getId()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.name").value("연관 정보 없음"))
                .andExpect(jsonPath("$.activities", hasSize(0)))
                .andExpect(jsonPath("$.notices", hasSize(0)))
                .andExpect(jsonPath("$.sources", hasSize(0)));
    }

    @Test
    void swaggerIncludesTheDetailPathAndResponseSchema() throws Exception {
        mvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paths['/api/v1/places/{placeId}'].get").exists())
                .andExpect(jsonPath("$.components.schemas.PlaceDetailResponse.properties.activities").exists())
                .andExpect(jsonPath("$.components.schemas.PlaceSourceResponse.properties.verifierId").doesNotExist());
    }

    @Test
    @DisplayName("수집한 운영 정보를 검수된 장소 정보와 구분하고 조회 쿼리 수를 유지한다")
    void separatesCollectedOperatingInfoFromVerifiedPlaceDataAndKeepsQueryCountConstant() throws Exception {
        var source = persist(SourceRecord.builder().place(published).type("TOUR_API").originId("2623831").build());
        persist(new TourApiImportRecord("2623831", published.getId(), source.getId(), NOW.minusDays(2)));
        persist(new PlaceOperatingInfo(published, "2623831", "평日 10:30", "월요일", "1855-3946", NOW));
        em.flush();
        em.clear();
        var stats = em.getEntityManagerFactory().unwrap(SessionFactory.class).getStatistics();
        stats.clear();
        mvc.perform(get("/api/v1/places/{id}", published.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.operatingInfo.operatingHours").value("평日 10:30"))
                .andExpect(jsonPath("$.operatingInfo.restDays").value("월요일"))
                .andExpect(jsonPath("$.operatingInfo.contactPhone").value("1855-3946"))
                .andExpect(jsonPath("$.operatingInfo.collectedAt").value("2026-11-13T12:00:00"))
                .andExpect(jsonPath("$.operatingInfo.verificationStatus").value("UNVERIFIED"))
                .andExpect(jsonPath("$.operatingInfo.verifiedAt").value(nullValue()))
                .andExpect(jsonPath("$.operationStatus").value(nullValue()));
        assertThat(stats.getPrepareStatementCount()).isEqualTo(5);
        published = em.find(Place.class, published.getId());
        published.changePublishStatus(PlacePublishStatus.HIDDEN);
        em.flush();
        mvc.perform(get("/api/v1/places/{id}", published.getId())).andExpect(status().isNotFound())
                .andExpect(jsonPath("$.operatingInfo").doesNotExist());
    }

    @Test
    @DisplayName("목록과 상세에서 같은 대표 사진과 출처를 추가 쿼리 없이 조회한다")
    void returnsTheSameVerifiedPhotoAndAttributionInListAndDetailWithoutExtraQueries() throws Exception {
        String url = "https://tong.visitkorea.or.kr/cms/resource/1/123_image2_1.jpg";
        persist(new PlaceRepresentativeImage(published, url, "한국관광공사 TourAPI 제공 · 테스트 사진",
                "https://www.data.go.kr/data/15101578/openapi.do", "Type3", NOW));
        em.flush();
        em.clear();
        var stats = em.getEntityManagerFactory().unwrap(SessionFactory.class).getStatistics();
        stats.clear();
        mvc.perform(get("/api/v1/places/{id}", published.getId()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.representativeImage").value(url))
                .andExpect(jsonPath("$.imageLicense").value("Type3"))
                .andExpect(jsonPath("$.imageAttribution").value("한국관광공사 TourAPI 제공 · 테스트 사진"))
                .andExpect(jsonPath("$.imageSourceUrl").value("https://www.data.go.kr/data/15101578/openapi.do"));
        assertThat(stats.getPrepareStatementCount()).isEqualTo(5);
        em.clear();
        stats.clear();
        mvc.perform(get("/api/v1/places").param("query", "공개 장소"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.items[0].representativeImage").value(url))
                .andExpect(jsonPath("$.items[0].imageLicense").value("Type3"));
        assertThat(stats.getPrepareStatementCount()).isEqualTo(2);
    }

    @Test
    @DisplayName("이용 권한이 불명확하거나 신뢰할 수 없는 사진 URL을 거절한다")
    void rejectsPhotosWithUnknownRightsOrUntrustedHost() {
        for (String url : new String[]{"https://example.com/image.jpg", "http://tong.visitkorea.or.kr/cms/resource/1/a.jpg",
                "https://tong.visitkorea.or.kr/cms/resource/1/a.jpg?key=secret"}) {
            org.assertj.core.api.Assertions.assertThatThrownBy(() -> new PlaceRepresentativeImage(published, url,
                    "출처", "https://www.data.go.kr", "Type1", NOW)).isInstanceOf(IllegalArgumentException.class);
        }
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> new PlaceRepresentativeImage(published,
                "https://tong.visitkorea.or.kr/cms/resource/1/a.jpg", "출처", "https://www.data.go.kr", "Unknown", NOW))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void hiddenDraftAndMissingPlacesHaveTheSameNotFoundResponse() throws Exception {
        for (long id : new long[]{draft.getId(), hidden.getId(), Long.MAX_VALUE}) {
            var response = mvc.perform(get("/api/v1/places/{id}", id))
                    .andExpect(status().isNotFound())
                    .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
                    .andExpect(jsonPath("$.code").value("PLACE_NOT_FOUND"))
                    .andExpect(jsonPath("$.detail").value("장소를 찾을 수 없습니다."))
                    .andExpect(jsonPath("$.name").doesNotExist()).andReturn().getResponse();
            assertThat(response.getContentAsString()).contains(response.getHeader("X-Request-Id"));
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"0", "-1", "oops", "9223372036854775808"})
    void invalidIdsUseCommonBadRequest(String id) throws Exception {
        mvc.perform(get("/api/v1/places/" + id))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
                .andExpect(jsonPath("$.requestId").isNotEmpty());
    }

    @Test
    void numberOfQueriesDoesNotGrowWithActivities() throws Exception {
        var stats = em.getEntityManagerFactory().unwrap(SessionFactory.class).getStatistics();
        stats.clear();
        mvc.perform(get("/api/v1/places/{id}", published.getId())).andExpect(status().isOk());
        assertThat(stats.getPrepareStatementCount()).isEqualTo(5);
        var category = persist(ActivityCategory.builder().name("추가 분류").build());
        for (int i = 0; i < 20; i++) {
            var activity = activity(category, "추가 종목 " + i, "OUTDOOR");
            persist(PlaceActivitySeason.builder().placeActivity(activity).season("WINTER").build());
        }
        em.flush();
        em.clear();
        stats.clear();
        mvc.perform(get("/api/v1/places/{id}", published.getId()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.activities", hasSize(23)));
        assertThat(stats.getPrepareStatementCount()).isEqualTo(5);
    }

    private <T> T persist(T entity) {
        em.persist(entity);
        return entity;
    }

    private PlaceActivity activity(ActivityCategory category, String name, String environment) {
        var type = persist(ActivityType.builder().category(category).name(name).build());
        return persist(PlaceActivity.builder().place(published).activityType(type).environmentType(environment).build());
    }

    private void notice(SourceRecord source, String title, PlacePublishStatus status, LocalDateTime from, LocalDateTime until) {
        persist(PlaceNotice.builder().place(published).sourceRecord(source).title(title).content("공지 본문")
                .publishStatus(status).validFrom(from).validUntil(until).build());
    }
}
