package com.outpick.domain.place.importer;

import com.outpick.domain.notification.service.PlaceNotificationScheduler;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.ApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.web.SecurityFilterChain;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.mysql.MySQLContainer;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE,
        properties = "spring.jpa.hibernate.ddl-auto=validate")
@Testcontainers
class TourCommandContextTests {
    @Container @ServiceConnection
    static MySQLContainer mysql = new MySQLContainer("mysql:8.4.13");

    @Autowired ApplicationContext context;
    @Autowired JdbcTemplate jdbc;

    @Test
    @DisplayName("인증 통합 후에도 웹 서버 없이 수집 명령이 시작된다")
    void startsWithoutServletSecurity() {
        assertThat(context.getBean(TourImportWriter.class)).isNotNull();
        assertThat(context.getBeansOfType(SecurityFilterChain.class)).isEmpty();
        assertThat(context.getBeansOfType(PlaceNotificationScheduler.class)).isEmpty();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM tour_api_import", Integer.class)).isZero();
    }
}
