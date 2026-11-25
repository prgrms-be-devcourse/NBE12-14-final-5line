package com.outpick.domain.place;

import com.outpick.domain.place.entity.ActivityCategory;
import com.outpick.domain.place.entity.ActivityType;
import com.outpick.domain.place.entity.Place;
import com.outpick.domain.place.entity.PlaceActivity;
import com.outpick.domain.place.entity.PlaceActivitySeason;
import com.outpick.domain.place.entity.PlaceNotice;
import com.outpick.domain.place.entity.PlacePublishStatus;
import com.outpick.domain.place.entity.Region;
import com.outpick.domain.place.entity.SourceRecord;
import com.outpick.domain.place.repository.PlaceRepository;
import jakarta.persistence.EntityManager;
import org.flywaydb.core.Flyway;
import org.hibernate.Hibernate;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.mysql.MySQLContainer;

import java.math.BigDecimal;
import java.sql.SQLException;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest(properties = "spring.jpa.hibernate.ddl-auto=validate")
@Testcontainers
@Transactional
class PlacePersistenceTests {

    @Container
    @ServiceConnection
    static MySQLContainer mysql = new MySQLContainer("mysql:8.4.13");

    @Autowired EntityManager entityManager;
    @Autowired PlaceRepository places;
    @Autowired Flyway flyway;
    @Autowired JdbcTemplate jdbc;
    @Autowired PlatformTransactionManager transactionManager;

    @Test
    @DisplayName("빈 MySQL에 마이그레이션을 적용하고 Hibernate 매핑과 실행 이력을 검증한다")
    void migratesEmptyDatabase() {
        assertThat(flyway.validateWithResult().validationSuccessful).isTrue();
        assertThat(flyway.info().pending()).isEmpty();
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM flyway_schema_history WHERE version = '1' AND success = 1", Integer.class))
                .isEqualTo(1);
        assertThat(jdbc.queryForList("SHOW TABLES", String.class)).contains(
                "region", "activity_category", "activity_type", "place", "place_activity",
                "place_activity_season", "source_record", "place_notice", "favorite", "review", "review_report",
                "media_asset", "review_image", "review_image_data", "review_media", "place_change_event", "notification");
        // 두 번째 migrate는 기존 스키마와 데이터를 다시 생성하지 않는다.
        assertThat(flyway.migrate().migrationsExecuted).isZero();
    }

    @Test
    @DisplayName("장소·활동·시즌·출처·공지를 저장하고 DB에서 다시 읽는다")
    void persistsAndReadsPlaceGraph() {
        var region = region();
        var place = place(region, "샘플 장소");
        var activity = activity(place);
        var season = PlaceActivitySeason.builder().placeActivity(activity).season("WINTER").build();
        entityManager.persist(season);
        var verifiedAt = LocalDateTime.of(2026, 11, 13, 9, 0);
        var source = SourceRecord.builder().place(place).type("OFFICIAL")
                .url("https://example.com/info").verifiedFields("address")
                .verifiedAt(verifiedAt).usageTermsMemo("테스트용 출처").build();
        entityManager.persist(source);
        var notice = PlaceNotice.builder().place(place).sourceRecord(source)
                .title("테스트 공지").content("테스트 본문").validFrom(verifiedAt).build();
        entityManager.persist(notice);
        entityManager.flush();
        entityManager.clear();

        var loaded = places.findById(place.getId()).orElseThrow();
        assertThat(Hibernate.isInitialized(loaded.getRegion())).isFalse();
        assertThat(loaded.getRegion().getParent().getName()).isEqualTo("강원");
        assertThat(loaded.getLatitude()).isEqualByComparingTo("37.3700000");
        assertThat(loaded.getLongitude()).isEqualByComparingTo("128.3900000");
        assertThat(loaded.getPublishStatus()).isEqualTo(PlacePublishStatus.DRAFT);
        assertThat(loaded.getOperationStatus()).isNull();
        assertThat(loaded.getVersion()).isEqualTo("source-v1");
        var loadedSeason = entityManager.find(PlaceActivitySeason.class, season.getId());
        assertThat(loadedSeason.getCreatedAt()).isNotNull();
        assertThat(loadedSeason.getPlaceActivity().getPlace().getId()).isEqualTo(place.getId());
        assertThat(loadedSeason.getPlaceActivity().getActivityType().getCategory().getName()).isEqualTo("겨울 활동");
        var loadedNotice = entityManager.find(PlaceNotice.class, notice.getId());
        assertThat(loadedNotice.getSourceRecord().getVerifiedAt()).isEqualTo(verifiedAt);
        assertThat(loadedNotice.getPublishStatus()).isEqualTo(PlacePublishStatus.DRAFT);
    }

    @Test
    @DisplayName("장소 기본 정보 수정 결과가 다음 DB 조회에 반영된다")
    void updatesBasicInfo() {
        var place = place(region(), "수정 전");
        place.updateBasicInfo("수정 후", "변경된 소개", "변경된 주소");
        entityManager.flush();
        entityManager.clear();
        var loaded = places.findById(place.getId()).orElseThrow();
        assertThat(loaded.getName()).isEqualTo("수정 후");
        assertThat(loaded.getIntroduce()).isEqualTo("변경된 소개");
        assertThat(loaded.getAddress()).isEqualTo("변경된 주소");
    }

    @Test
    @DisplayName("게시 상태는 선언 순서가 아닌 약속한 숫자로 저장하고 복원한다")
    void persistsPublishStatusCodes() {
        var place = place(region(), "공개 상태 검증");
        for (var status : PlacePublishStatus.values()) {
            place.changePublishStatus(status);
            entityManager.flush();
            assertThat(jdbc.queryForObject("SELECT publish_status FROM place WHERE place_id = ?", Byte.class, place.getId()))
                    .isEqualTo(status.getCode());
            entityManager.clear();
            place = places.findById(place.getId()).orElseThrow();
            assertThat(place.getPublishStatus()).isEqualTo(status);
        }
    }

    @Test
    @DisplayName("새 장소와 공지의 DB 기본값은 검수 전이며 잘못된 게시 상태는 저장할 수 없다")
    void enforcesPublishStatusDefaultsAndConstraints() {
        var region = region();
        jdbc.update("INSERT INTO place(region_id, name) VALUES (?, ?)", region.getId(), "게시 상태 기본값 검증");
        var place = places.findById(jdbc.queryForObject(
                "SELECT place_id FROM place WHERE region_id = ?", Long.class, region.getId())).orElseThrow();
        assertThat(place.getPublishStatus()).isEqualTo(PlacePublishStatus.DRAFT);

        var source = SourceRecord.builder().place(place).build();
        entityManager.persist(source);
        jdbc.update("INSERT INTO place_notice(place_id, source_record_id, title) VALUES (?, ?, ?)",
                place.getId(), source.getId(), "공지 게시 상태 기본값 검증");
        assertThat(jdbc.queryForObject(
                "SELECT publish_status FROM place_notice WHERE source_record_id = ?", Byte.class, source.getId()))
                .isEqualTo(PlacePublishStatus.DRAFT.getCode());

        assertThatThrownBy(() -> jdbc.update("UPDATE place SET publish_status = 3 WHERE place_id = ?", place.getId()))
                .rootCause().isInstanceOfSatisfying(SQLException.class, error -> {
                    assertThat(error.getErrorCode()).isEqualTo(3819);
                    assertThat(error.getMessage()).contains("ck_place_publish_status");
                });
        assertThatThrownBy(() -> jdbc.update("UPDATE place SET publish_status = NULL WHERE place_id = ?", place.getId()))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbc.update(
                "UPDATE place_notice SET publish_status = 3 WHERE source_record_id = ?", source.getId()))
                .rootCause().isInstanceOfSatisfying(SQLException.class, error -> {
                    assertThat(error.getErrorCode()).isEqualTo(3819);
                    assertThat(error.getMessage()).contains("ck_place_notice_publish_status");
                });
        assertThatThrownBy(() -> jdbc.update(
                "UPDATE place_notice SET publish_status = NULL WHERE source_record_id = ?", source.getId()))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("같은 장소에 같은 종목을 중복 연결할 수 없다")
    void rejectsDuplicateActivity() {
        var activity = activity(place(region(), "중복 종목"));
        assertThatThrownBy(() -> jdbc.update(
                "INSERT INTO place_activity(place_id, activity_type_id) VALUES (?, ?)",
                activity.getPlace().getId(), activity.getActivityType().getId()))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("같은 장소별 종목에 동일한 시즌을 중복 저장할 수 없다")
    void rejectsDuplicateSeason() {
        var activity = activity(place(region(), "중복 시즌"));
        jdbc.update("INSERT INTO place_activity_season(place_activity_id, season) VALUES (?, ?)", activity.getId(), "WINTER");
        assertThatThrownBy(() -> jdbc.update(
                "INSERT INTO place_activity_season(place_activity_id, season) VALUES (?, ?)", activity.getId(), "WINTER"))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("다른 장소의 출처를 공지에 연결할 수 없다")
    void rejectsNoticeWithAnotherPlacesSource() {
        var region = region();
        var sourcePlace = place(region, "출처 장소");
        var otherPlace = place(region, "다른 장소");
        var source = SourceRecord.builder().place(sourcePlace).build();
        entityManager.persist(source);
        assertThatThrownBy(() -> jdbc.update(
                "INSERT INTO place_notice(place_id, source_record_id, title) VALUES (?, ?, ?)",
                otherPlace.getId(), source.getId(), "잘못된 출처"))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("없는 지역을 참조하거나 유효 범위를 벗어난 좌표를 저장할 수 없다")
    void rejectsInvalidReferencesAndCoordinates() {
        var region = region();
        assertThatThrownBy(() -> jdbc.update("INSERT INTO place(region_id, name) VALUES (-1, '없는 지역')"))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbc.update("INSERT INTO place(region_id, latitude) VALUES (?, 91)", region.getId()))
                .rootCause().isInstanceOfSatisfying(SQLException.class, error -> {
                    assertThat(error.getErrorCode()).isEqualTo(3819);
                    assertThat(error.getMessage()).contains("ck_place_latitude");
                });
        assertThatThrownBy(() -> jdbc.update("INSERT INTO place(region_id, longitude) VALUES (?, 181)", region.getId()))
                .rootCause().isInstanceOfSatisfying(SQLException.class, error -> {
                    assertThat(error.getErrorCode()).isEqualTo(3819);
                    assertThat(error.getMessage()).contains("ck_place_longitude");
                });
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    @DisplayName("장소 저장 도중 실패하면 지역·장소·활동 저장 전체가 롤백된다")
    void rollsBackFailedSave() {
        long beforePlaces = places.count();
        int beforeRegions = jdbc.queryForObject("SELECT COUNT(*) FROM region", Integer.class);
        int beforeActivities = jdbc.queryForObject("SELECT COUNT(*) FROM place_activity", Integer.class);
        var transaction = new TransactionTemplate(transactionManager);
        assertThatThrownBy(() -> transaction.executeWithoutResult(status -> {
            activity(place(region(), "롤백 대상"));
            entityManager.flush();
            throw new IllegalStateException("저장 실패 테스트");
        })).isInstanceOf(IllegalStateException.class);
        assertThat(places.count()).isEqualTo(beforePlaces);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM region", Integer.class)).isEqualTo(beforeRegions);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM place_activity", Integer.class)).isEqualTo(beforeActivities);
    }

    private Region region() {
        var parent = Region.builder().code("TEST-GW").name("강원").build();
        entityManager.persist(parent);
        var child = Region.builder().parent(parent).code("TEST-PC").name("평창").build();
        entityManager.persist(child);
        return child;
    }

    private Place place(Region region, String name) {
        return places.saveAndFlush(Place.builder().region(region).name(name)
                .latitude(new BigDecimal("37.3700000")).longitude(new BigDecimal("128.3900000"))
                .version("source-v1").build());
    }

    private PlaceActivity activity(Place place) {
        var category = ActivityCategory.builder().name("겨울 활동").build();
        entityManager.persist(category);
        var type = ActivityType.builder().category(category).name("스키").build();
        entityManager.persist(type);
        var activity = PlaceActivity.builder().place(place).activityType(type).environmentType("OUTDOOR").build();
        entityManager.persist(activity);
        return activity;
    }
}
