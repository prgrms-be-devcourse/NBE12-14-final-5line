package com.outpick.domain.notification;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.mysql.MySQLContainer;

import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Testcontainers
class PlaceNotificationMigrationTests {
    @Container
    static MySQLContainer mysql = new MySQLContainer("mysql:8.4.13");

    @Test
    @DisplayName("V18은 기존 공지와 이벤트를 보존하고 개인 알림의 중복 및 참조를 검증한다")
    void upgradesV17AndEnforcesNotificationConstraints() throws SQLException {
        var config = Flyway.configure().dataSource(mysql.getJdbcUrl(), mysql.getUsername(), mysql.getPassword());
        config.target("17").load().migrate();

        try (var connection = DriverManager.getConnection(mysql.getJdbcUrl(), mysql.getUsername(), mysql.getPassword());
             var statement = connection.createStatement()) {
            statement.executeUpdate("INSERT INTO region (region_id, name) VALUES (1, '지역')");
            statement.executeUpdate("INSERT INTO place (place_id, region_id, name) VALUES (1, 1, '장소')");
            statement.executeUpdate("INSERT INTO source_record (source_record_id, place_id) VALUES (1, 1)");
            statement.executeUpdate("""
                    INSERT INTO place_notice (place_notice_id, place_id, source_record_id, title, publish_status)
                    VALUES (1, 1, 1, '기존 공지', 1), (2, 1, 1, '새 공지', 1)
                    """);
            statement.executeUpdate("""
                    INSERT INTO place_change_event (place_change_event_id, place_notice_id) VALUES (1, 1)
                    """);

            config.target("18").load().migrate();
            try (var rows = statement.executeQuery("""
                    SELECT n.title, e.task_status, e.eligible_at
                    FROM place_change_event e JOIN place_notice n ON n.place_notice_id = e.place_notice_id
                    WHERE e.place_change_event_id = 1
                    """)) {
                assertThat(rows.next()).isTrue();
                assertThat(rows.getString("title")).isEqualTo("기존 공지");
                assertThat(rows.getString("task_status")).isEqualTo("PENDING");
                assertThat(rows.getObject("eligible_at")).isNull();
            }

            statement.executeUpdate("""
                    INSERT INTO place_change_event (place_change_event_id, place_notice_id, eligible_at)
                    VALUES (2, 2, '2026-11-24 11:00:00.123456')
                    """);
            try (var rows = statement.executeQuery("""
                    SELECT eligible_at FROM place_change_event WHERE place_change_event_id = 2
                    """)) {
                assertThat(rows.next()).isTrue();
                assertThat(rows.getObject("eligible_at", LocalDateTime.class))
                        .isEqualTo(LocalDateTime.parse("2026-11-24T11:00:00.123456"));
            }

            statement.executeUpdate("""
                    INSERT INTO `user` (user_id, email, nickname, password_hash, created_at, updated_at)
                    VALUES (1, 'notification-migration@example.com', '회원', 'hash', NOW(6), NOW(6))
                    """);
            String insert = "INSERT INTO notification (place_change_event_id, user_id, created_at) VALUES ";
            statement.executeUpdate(insert + "(2, 1, '2026-11-24 11:00:01')");
            assertSqlError(statement, insert + "(2, 1, '2026-11-24 11:00:02')", 1062);
            assertSqlError(statement, insert + "(999, 1, '2026-11-24 11:00:01')", 1452);
            assertSqlError(statement, insert + "(2, 999, '2026-11-24 11:00:01')", 1452);
            try (var rows = statement.executeQuery("SELECT read_at FROM notification WHERE user_id = 1")) {
                assertThat(rows.next()).isTrue();
                assertThat(rows.getObject("read_at")).isNull();
            }
            statement.executeUpdate("DELETE FROM `user` WHERE user_id = 1");
            try (var rows = statement.executeQuery("SELECT user_id FROM notification WHERE user_id = 1")) {
                assertThat(rows.next()).isFalse();
            }
        }
    }

    private static void assertSqlError(Statement statement, String sql, int errorCode) {
        assertThatThrownBy(() -> statement.executeUpdate(sql))
                .isInstanceOfSatisfying(SQLException.class, error -> assertThat(error.getErrorCode()).isEqualTo(errorCode));
    }
}
