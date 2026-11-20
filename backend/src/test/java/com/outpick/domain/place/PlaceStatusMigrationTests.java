package com.outpick.domain.place;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.mysql.MySQLContainer;

import java.sql.DriverManager;
import java.sql.SQLException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Testcontainers
class PlaceStatusMigrationTests {
    @Container
    static MySQLContainer mysql = new MySQLContainer("mysql:8.4.13");

    @Test
    @DisplayName("V1을 갱신해도 검수 전 장소를 공개하지 않는다")
    void upgradesV1WithoutPublishingUnreviewedPlaces() throws Exception {
        Flyway.configure().dataSource(mysql.getJdbcUrl(), mysql.getUsername(), mysql.getPassword())
                .target("1").load().migrate();
        try (var connection = DriverManager.getConnection(mysql.getJdbcUrl(), mysql.getUsername(), mysql.getPassword());
             var statement = connection.createStatement()) {
            statement.executeUpdate("INSERT INTO region(region_id, name) VALUES (1, '테스트 지역')");
            statement.executeUpdate("INSERT INTO place(region_id, name, publish_status) VALUES (1, '검수 전 데이터', NULL)");
            statement.executeUpdate("INSERT INTO place(region_id, name, publish_status) VALUES (1, '공개 데이터', 1)");
            statement.executeUpdate("INSERT INTO place(region_id, name, publish_status) VALUES (1, '비공개 데이터', 2)");
            statement.executeUpdate("INSERT INTO source_record(source_record_id, place_id) VALUES (1, 1)");
            statement.executeUpdate("INSERT INTO place_notice(place_id, source_record_id, publish_status) VALUES (1, 1, NULL), (1, 1, 1), (1, 1, 2)");
        }
        var flyway = Flyway.configure().dataSource(mysql.getJdbcUrl(), mysql.getUsername(), mysql.getPassword()).load();
        assertThat(flyway.migrate().migrationsExecuted).isEqualTo(14);
        assertThat(flyway.validateWithResult().validationSuccessful).isTrue();
        try (var connection = DriverManager.getConnection(mysql.getJdbcUrl(), mysql.getUsername(), mysql.getPassword());
             var statement = connection.createStatement()) {
            try (var rows = statement.executeQuery("SELECT publish_status FROM place ORDER BY place_id")) {
                for (int expected : new int[]{0, 1, 2}) {
                    assertThat(rows.next()).isTrue();
                    assertThat(rows.getInt(1)).isEqualTo(expected);
                    assertThat(rows.wasNull()).isFalse();
                }
                assertThat(rows.next()).isFalse();
            }
            statement.executeUpdate("INSERT INTO place(region_id, name) VALUES (1, '기본값 검증')");
            try (var rows = statement.executeQuery("SELECT publish_status FROM place_notice ORDER BY place_notice_id")) {
                for (int expected : new int[]{0, 1, 2}) {
                    assertThat(rows.next()).isTrue();
                    assertThat(rows.getInt(1)).isEqualTo(expected);
                    assertThat(rows.wasNull()).isFalse();
                }
            }
            statement.executeUpdate("INSERT INTO place_notice(place_id, source_record_id, title) VALUES (1, 1, '기본값 검증')");
            try (var rows = statement.executeQuery("SELECT publish_status FROM place_notice WHERE title = '기본값 검증'")) {
                assertThat(rows.next()).isTrue();
                assertThat(rows.getInt(1)).isZero();
                assertThat(rows.wasNull()).isFalse();
            }
            assertThatThrownBy(() -> statement.executeUpdate("INSERT INTO place_notice(place_id, source_record_id, publish_status) VALUES (1, 1, 3)"))
                    .isInstanceOfSatisfying(SQLException.class, error -> {
                        assertThat(error.getErrorCode()).isEqualTo(3819);
                        assertThat(error.getMessage()).contains("ck_place_notice_publish_status");
                    });
            assertThatThrownBy(() -> statement.executeUpdate("INSERT INTO place_notice(place_id, source_record_id, publish_status) VALUES (1, 1, NULL)"))
                    .isInstanceOf(SQLException.class);
            try (var rows = statement.executeQuery("SELECT publish_status FROM place WHERE name = '기본값 검증'")) {
                assertThat(rows.next()).isTrue();
                assertThat(rows.getInt(1)).isZero();
                assertThat(rows.wasNull()).isFalse();
            }
            assertThatThrownBy(() -> statement.executeUpdate("INSERT INTO place(region_id, publish_status) VALUES (1, 3)"))
                    .isInstanceOfSatisfying(SQLException.class, error -> {
                        assertThat(error.getErrorCode()).isEqualTo(3819);
                        assertThat(error.getMessage()).contains("ck_place_publish_status");
                    });
            assertThatThrownBy(() -> statement.executeUpdate("INSERT INTO place(region_id, publish_status) VALUES (1, NULL)"))
                    .isInstanceOf(SQLException.class);
        }
    }
}
