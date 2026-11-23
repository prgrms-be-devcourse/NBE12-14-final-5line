package com.outpick.domain.place.importer;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.web.context.WebApplicationContext;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.mysql.MySQLContainer;
import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE,
        properties = {"spring.jpa.hibernate.ddl-auto=validate", "spring.mail.host=localhost",
                "app.password-reset.from=outpick@example.com",
                "app.password-reset.frontend-base-url=http://localhost:3000"})
@ActiveProfiles("prod")
@Testcontainers
class TourImportProductionContextTests {
    @Container @ServiceConnection
    static MySQLContainer mysql = new MySQLContainer("mysql:8.4.13");
    @Autowired ConfigurableApplicationContext context;
    @Autowired JdbcTemplate jdbc;
    @Autowired TourOperatingInfoWriter writer;

    @Test
    @DisplayName("운영 프로필의 수집 환경은 웹 서버와 로컬 관리자 생성 없이 DB를 조회한다")
    void startsNonWebProductionContextWithoutLocalAdmin() {
        assertThat(context).isNotInstanceOf(WebApplicationContext.class);
        assertThat(context.getEnvironment().getActiveProfiles()).containsExactly("prod");
        assertThat(context.containsBean("localAdminInitializer")).isFalse();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM `user`", Integer.class)).isZero();
        assertThat(writer.findContentIdsAfter("", 100)).isEmpty();
    }
}
