package com.outpick;

import javax.sql.DataSource;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.mysql.MySQLContainer;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Testcontainers
class BackendApplicationTests {

	@Container
	@ServiceConnection
	static MySQLContainer mysql = new MySQLContainer("mysql:8.4.13");

	@Autowired
	DataSource dataSource;

	@Autowired
	Flyway flyway;

	@Test
	void connectsToMySqlAndInitializesFlyway() throws Exception {
		try (var connection = dataSource.getConnection();
			 var statement = connection.createStatement()) {
			assertThat(connection.getMetaData().getDatabaseProductName()).isEqualTo("MySQL");
			try (var result = statement.executeQuery("SELECT 1")) {
				assertThat(result.next()).isTrue();
				assertThat(result.getInt(1)).isEqualTo(1);
			}
			try (var tables = connection.getMetaData()
				.getTables(connection.getCatalog(), null, "flyway_schema_history", new String[]{"TABLE"})) {
				assertThat(tables.next()).isTrue();
			}
		}
		assertThat(flyway.validateWithResult().validationSuccessful).isTrue();
		assertThat(flyway.info().pending()).isEmpty();
	}

}
