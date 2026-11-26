package com.outpick.domain.place.importer;

import com.outpick.domain.place.entity.PlacePublishStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.mysql.MySQLContainer;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = "spring.jpa.hibernate.ddl-auto=validate")
@Testcontainers
class TourOperatingCandidateStoreTests {
    @Container @ServiceConnection
    static MySQLContainer mysql = new MySQLContainer("mysql:8.4.13");
    @Autowired TourImportWriter placeWriter;
    @Autowired TourOperatingInfoWriter operatingWriter;
    @Autowired JdbcTemplate jdbc;
    @Autowired PlatformTransactionManager transactionManager;

    @Test
    @DisplayName("동시에 감지한 동일 변경은 한 건만 저장하고 공개 운영 정보를 유지한다")
    void storesIdenticalPendingChangesOnceUnderConcurrentRuns() throws Exception {
        long placeId = publishedPlace("301");
        var store = new TourOperatingCandidateStore(jdbc, transactionManager);
        var before = jdbc.queryForMap("SELECT * FROM place_operating_info WHERE place_id = ?", placeId);
        var first = candidate(placeId, "301", "09:00", "10:00\n~18:00");
        var equivalent = candidate(placeId, "301", "09:00", "10:00 ~18:00");
        var ready = new CountDownLatch(1);
        try (var pool = Executors.newFixedThreadPool(2)) {
            var task1 = pool.submit(() -> { ready.await(); return store.save(List.of(first)); });
            var task2 = pool.submit(() -> { ready.await(); return store.save(List.of(equivalent)); });
            ready.countDown();
            assertThat(List.of(task1.get(15, TimeUnit.SECONDS), task2.get(15, TimeUnit.SECONDS)))
                    .containsExactlyInAnyOrder(new TourOperatingCandidateStore.Summary(1, 0, 0),
                            new TourOperatingCandidateStore.Summary(0, 1, 0));
        }
        assertThat(jdbc.queryForMap("""
                SELECT previous_value, new_value, previous_source_url, new_source_url, resolved_at
                FROM place_operating_change_candidate WHERE place_id = ?
                """, placeId))
                .containsEntry("previous_value", "09:00")
                .containsEntry("previous_source_url", first.previousSourceUrl())
                .containsEntry("new_source_url", first.newSourceUrl())
                .containsEntry("resolved_at", null)
                .hasEntrySatisfying("new_value", value -> assertThat(value)
                        .isIn("10:00\n~18:00", "10:00 ~18:00"));
        assertThat(jdbc.queryForMap("SELECT * FROM place_operating_info WHERE place_id = ?", placeId))
                .isEqualTo(before);
    }

    @Test
    @DisplayName("비교 이후 운영 정보가 바뀌거나 비공개가 된 장소는 후보 저장에서 제외한다")
    void skipsCandidatesWithStaleBaselineOrHiddenPlace() {
        long changed = publishedPlace("302");
        long hidden = publishedPlace("303");
        jdbc.update("UPDATE place_operating_info SET operating_hours = '08:00' WHERE place_id = ?", changed);
        jdbc.update("UPDATE place SET publish_status = ? WHERE place_id = ?", PlacePublishStatus.HIDDEN.getCode(), hidden);
        var store = new TourOperatingCandidateStore(jdbc, transactionManager);

        assertThat(store.save(List.of(candidate(changed, "302", "09:00", "10:00"),
                candidate(hidden, "303", "09:00", "10:00"))))
                .isEqualTo(new TourOperatingCandidateStore.Summary(0, 0, 2));
        assertThat(jdbc.queryForList("""
                SELECT candidate_id FROM place_operating_change_candidate WHERE place_id IN (?, ?)
                """, Long.class, changed, hidden)).isEmpty();
    }

    @Test
    @DisplayName("수집된 개장 기간과 이용요금의 변경 후보를 저장해도 공개 값은 유지한다")
    void storesOpeningPeriodAndUsageFeeCandidatesWithoutApplyingThem() {
        long placeId = placeWriter.save(new TourApiNormalizer()
                .normalize(TourApiTests.target("304"), TourApiTests.place("304", "126.1", ""))).placeId();
        jdbc.update("UPDATE place SET publish_status = ? WHERE place_id = ?", PlacePublishStatus.PUBLISHED.getCode(), placeId);
        operatingWriter.saveInitial(new TourOperatingSnapshot("304", null, null, null, "12월~2월", "60,000원"));
        var candidate = new TourOperatingChangeReport.Candidate(placeId, "테스트 시설", "304",
                "https://example.com/previous", "https://www.data.go.kr/data/15101578/openapi.do",
                List.of(new TourOperatingChangeReport.FieldChange("openingPeriod", "12월~2월", "12월~3월"),
                        new TourOperatingChangeReport.FieldChange("usageFee", "60,000원", "65,000원")));

        var store = new TourOperatingCandidateStore(jdbc, transactionManager);
        assertThat(store.save(List.of(candidate))).isEqualTo(new TourOperatingCandidateStore.Summary(2, 0, 0));
        assertThat(jdbc.queryForList("SELECT field_name FROM place_operating_change_candidate WHERE place_id = ?",
                String.class, placeId)).containsExactlyInAnyOrder("openingPeriod", "usageFee");
        assertThat(jdbc.queryForMap("SELECT opening_period, usage_fee FROM place_operating_info WHERE place_id = ?",
                placeId)).containsEntry("opening_period", "12월~2월").containsEntry("usage_fee", "60,000원");
    }

    private long publishedPlace(String contentId) {
        long id = placeWriter.save(new TourApiNormalizer()
                .normalize(TourApiTests.target(contentId), TourApiTests.place(contentId, "126.1", ""))).placeId();
        jdbc.update("UPDATE place SET publish_status = ? WHERE place_id = ?", PlacePublishStatus.PUBLISHED.getCode(), id);
        operatingWriter.saveInitial(new TourOperatingSnapshot(contentId, "09:00", null, null));
        return id;
    }

    private TourOperatingChangeReport.Candidate candidate(long id, String contentId, String previous, String next) {
        return new TourOperatingChangeReport.Candidate(id, "테스트 시설", contentId,
                "https://example.com/previous", "https://www.data.go.kr/data/15101578/openapi.do",
                List.of(new TourOperatingChangeReport.FieldChange("operatingHours", previous, next)));
    }
}
