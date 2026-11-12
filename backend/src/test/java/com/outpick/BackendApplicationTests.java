package com.outpick;

import javax.sql.DataSource;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.mysql.MySQLContainer;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class BackendApplicationTests {

    @Container
    @ServiceConnection
    static MySQLContainer mysql = new MySQLContainer("mysql:8.4.13");

    @Autowired
    MockMvc mockMvc;

    @Autowired
    DataSource dataSource;

    @Autowired
    Flyway flyway;

    @Test
    @DisplayName("앱이 MySQL에 연결해 쿼리를 실행하고 Flyway 이력 테이블을 생성한다")
    void connectsToMySqlAndInitializesFlyway() throws Exception {
        try (var connection = dataSource.getConnection();
             var statement = connection.createStatement()
        ) {
            assertThat(connection.getMetaData().getDatabaseProductName()).isEqualTo("MySQL");

            try (var result = statement.executeQuery("SELECT 1")) {
                assertThat(result.next()).isTrue();
                assertThat(result.getInt(1)).isEqualTo(1);
            }

            try (var tables = connection.getMetaData().getTables(
                    connection.getCatalog(),
                    null,
                    "flyway_schema_history",
                    new String[]{"TABLE"})
            ) {
                assertThat(tables.next()).isTrue();
            }
        }

        // DB에 기록된 SQL 실행 이력과 프로젝트의 마이그레이션 SQL 파일이 일치하는지 확인합니다.
        // 현재는 SQL 파일이 없으므로 비교할 실행 이력도 없는 상태입니다.
        assertThat(flyway.validateWithResult().validationSuccessful).isTrue();

        // 프로젝트의 마이그레이션 SQL 중 DB에 아직 실행하지 않은 파일이 없는지 확인합니다.
        assertThat(flyway.info().pending()).isEmpty();
    }

    @Test
    @DisplayName("OpenAPI 문서는 기존 경로에서 제공하고 도메인 API를 v1 경로로 안내한다")
    void openApiDocumentsPrefixedDomainPaths() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paths['/api/v1/examples/{id}'].get").exists())
                .andExpect(jsonPath("$.paths['/api/v1/examples/preview'].post").exists())
                .andExpect(jsonPath("$.paths['/api/examples/{id}']").doesNotExist());
    }

    @Test
    @DisplayName("Swagger UI와 Actuator는 도메인 API 접두사 없이 기존 경로에서 제공한다")
    void frameworkEndpointsKeepTheirPaths() throws Exception {
        mockMvc.perform(get("/swagger-ui.html")).andExpect(status().is3xxRedirection());
        mockMvc.perform(get("/actuator/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"));
        mockMvc.perform(get("/api/v1/v3/api-docs")).andExpect(status().isNotFound());
        mockMvc.perform(get("/api/v1/actuator/health")).andExpect(status().isNotFound());
    }

}
