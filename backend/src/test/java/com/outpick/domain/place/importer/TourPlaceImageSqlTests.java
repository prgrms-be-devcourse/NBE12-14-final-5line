package com.outpick.domain.place.importer;

import com.outpick.domain.place.entity.PlacePublishStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.support.EncodedResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.mysql.MySQLContainer;

import javax.sql.DataSource;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = "spring.jpa.hibernate.ddl-auto=validate")
@Testcontainers
class TourPlaceImageSqlTests {
    @Container
    @ServiceConnection
    static MySQLContainer mysql = new MySQLContainer("mysql:8.4.13");

    @Autowired TourImportWriter writer;
    @Autowired JdbcTemplate jdbc;
    @Autowired DataSource dataSource;

    @Test
    @DisplayName("대표사진 SQL은 공개 장소의 빈 사진만 채우고 재실행해도 기존 사진을 보존한다")
    void appliesReviewedImagesOnlyToPublishedPlacesWithoutReplacingExistingImages() throws Exception {
        long published = importPlace("131139", "강남청소년센터", PlacePublishStatus.PUBLISHED);
        long draft = importPlace("131297", "롯데월드 아이스링크", PlacePublishStatus.DRAFT);
        long hidden = importPlace("663785", "서울광장 스케이트장", PlacePublishStatus.HIDDEN);
        long existing = importPlace("2361081", "가평 레일바이크", PlacePublishStatus.PUBLISHED);
        jdbc.update("""
                INSERT INTO place_representative_image
                    (place_id, url, attribution, source_url, license_type, verified_at)
                VALUES (?, ?, ?, ?, ?, '2026-11-01 00:00:00')
                """, existing, "https://tong.visitkorea.or.kr/cms/resource/01/1.jpg",
                "기존 대표사진 출처", "https://www.data.go.kr/data/15101578/openapi.do", "Type3");
        var existingPhoto = jdbc.queryForMap("SELECT * FROM place_representative_image WHERE place_id = ?", existing);

        applyReviewSql();

        var addedPhoto = jdbc.queryForMap("SELECT * FROM place_representative_image WHERE place_id = ?", published);
        assertThat(addedPhoto)
                .containsEntry("url", "https://tong.visitkorea.or.kr/cms/resource/49/3495049_image2_1.jpg")
                .containsEntry("attribution", "한국관광공사 TourAPI 제공 · 강남청소년센터 대표 사진")
                .containsEntry("source_url", "https://www.data.go.kr/data/15101578/openapi.do")
                .containsEntry("license_type", "Type3");
        assertThat(addedPhoto.get("verified_at")).isNotNull();
        assertThat(jdbc.queryForList(
                "SELECT place_id FROM place_representative_image WHERE place_id IN (?, ?)", Long.class, draft, hidden))
                .isEmpty();
        assertThat(jdbc.queryForMap("SELECT * FROM place_representative_image WHERE place_id = ?", existing))
                .isEqualTo(existingPhoto);

        var afterFirstRun = jdbc.queryForList("SELECT * FROM place_representative_image ORDER BY place_id");
        applyReviewSql();
        assertThat(jdbc.queryForList("SELECT * FROM place_representative_image ORDER BY place_id"))
                .isEqualTo(afterFirstRun);
    }

    private long importPlace(String contentId, String name, PlacePublishStatus status) {
        var target = new TourImportTarget(contentId, name, null, java.util.List.of());
        var raw = new TourApiPlace(contentId, "28", name, "서울특별시 강남구 테스트로 1", "",
                "127.03", "37.5", "", "", "", "11", "680", "");
        long placeId = writer.save(new TourApiNormalizer().normalize(target, raw)).placeId();
        jdbc.update("UPDATE place SET publish_status = ? WHERE place_id = ?", status.getCode(), placeId);
        return placeId;
    }

    private void applyReviewSql() throws Exception {
        var script = Path.of("scripts", "review-place-images-20261125.sql");
        if (!Files.isRegularFile(script)) {
            script = Path.of("backend").resolve(script);
        }
        var resource = new EncodedResource(new FileSystemResource(script), StandardCharsets.UTF_8);
        try (var connection = dataSource.getConnection()) {
            ScriptUtils.executeSqlScript(connection, resource);
        }
    }
}
