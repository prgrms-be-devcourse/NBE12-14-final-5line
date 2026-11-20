package com.outpick.domain.favorite;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.mysql.MySQLContainer;

import java.sql.DriverManager;
import java.sql.SQLException;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Testcontainers
class FavoriteNotificationMigrationTests {
    @Container
    static MySQLContainer mysql = new MySQLContainer("mysql:8.4.13");

    @Test
    @DisplayName("V14는 V13의 관심 장소와 소유권 제약을 유지하며 기본값을 OFF로 적용한다")
    void upgradesExistingFavoriteWithNotificationsOff() throws SQLException {
        Flyway.configure().dataSource(mysql.getJdbcUrl(), mysql.getUsername(), mysql.getPassword())
                .target("13").load().migrate();

        try (var connection = DriverManager.getConnection(mysql.getJdbcUrl(), mysql.getUsername(), mysql.getPassword());
             var statement = connection.createStatement()) {
            statement.executeUpdate("INSERT INTO region (region_id, name) VALUES (1, '테스트 지역')");
            statement.executeUpdate("INSERT INTO place (place_id, region_id, name) VALUES (1, 1, '테스트 장소')");
            statement.executeUpdate("""
                    INSERT INTO `user` (user_id, email, nickname, password_hash, created_at, updated_at)
                    VALUES (1, 'migration-favorite@example.com', '회원', 'hash', CURRENT_TIMESTAMP(6), CURRENT_TIMESTAMP(6))
                    """);
            statement.executeUpdate("""
                    INSERT INTO favorite (favorite_id, user_id, place_id, created_at)
                    VALUES (17, 1, 1, '2026-11-19 10:00:00.123456')
                    """);
        }

        var flyway = Flyway.configure().dataSource(mysql.getJdbcUrl(), mysql.getUsername(), mysql.getPassword())
                .target("14").load();
        assertThat(flyway.migrate().migrationsExecuted).isEqualTo(1);
        assertThat(flyway.validateWithResult().validationSuccessful).isTrue();
        assertThat(flyway.info().current().getVersion().getVersion()).isEqualTo("14");

        try (var connection = DriverManager.getConnection(mysql.getJdbcUrl(), mysql.getUsername(), mysql.getPassword());
             var statement = connection.createStatement()) {
            try (var row = statement.executeQuery("SELECT * FROM favorite")) {
                assertThat(row.next()).isTrue();
                assertThat(row.getLong("favorite_id")).isEqualTo(17);
                assertThat(row.getLong("user_id")).isEqualTo(1);
                assertThat(row.getLong("place_id")).isEqualTo(1);
                assertThat(row.getObject("created_at", LocalDateTime.class))
                        .isEqualTo(LocalDateTime.parse("2026-11-19T10:00:00.123456"));
                assertThat(row.getBoolean("is_notified")).isFalse();
                assertThat(row.wasNull()).isFalse();
                assertThat(row.getObject("notified_enabled_at")).isNull();
                assertThat(row.next()).isFalse();
            }
            assertThatThrownBy(() -> statement.executeUpdate("INSERT INTO favorite (user_id, place_id) VALUES (1, 1)"))
                    .isInstanceOfSatisfying(SQLException.class, error -> assertThat(error.getErrorCode()).isEqualTo(1062));
            assertThatThrownBy(() -> statement.executeUpdate("INSERT INTO favorite (user_id, place_id) VALUES (2, 1)"))
                    .isInstanceOfSatisfying(SQLException.class, error -> assertThat(error.getErrorCode()).isEqualTo(1452));
        }
    }
}
