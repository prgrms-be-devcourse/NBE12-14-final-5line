package com.outpick.domain.place.importer;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.mysql.MySQLContainer;
import tools.jackson.databind.json.JsonMapper;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import static org.assertj.core.api.Assertions.*;

@SpringBootTest(properties = "spring.jpa.hibernate.ddl-auto=validate")
@Testcontainers
class TourOperatingBatchPersistenceTests {
    @Container @ServiceConnection
    static MySQLContainer mysql = new MySQLContainer("mysql:8.4.13");
    @Autowired TourImportWriter placeWriter;
    @Autowired TourOperatingInfoWriter writer;
    @Autowired JdbcTemplate jdbc;

    @Test
    @DisplayName("DB 대상의 실패와 미제공을 격리하고 재실행으로 실패 장소만 다시 수집하며 검수 정보는 보존한다")
    void collectsFromDatabaseAndRetriesOnlyMissingInfoWithoutOverwritingReview() {
        for (String id : List.of("210", "211", "212", "213")) {
            placeWriter.save(new TourApiNormalizer().normalize(TourApiTests.target(id), TourApiTests.place(id, "126.1", "")));
        }
        writer.saveInitial(new TourOperatingSnapshot("210", "기존 시간", null, "123"));
        jdbc.update("UPDATE place_operating_info SET verification_status='NEEDS_REVIEW',review_note='공식 안내와 시간 다름' WHERE source_content_id='210'");
        var existing = jdbc.queryForMap("SELECT * FROM place_operating_info WHERE source_content_id='210'");
        assertThat(writer.findContentIdsAfter("", 2)).containsExactly("210", "211");
        assertThat(writer.findContentIdsAfter("211", 2)).containsExactly("212", "213");
        var calls = new ArrayList<String>();
        var client = new TourApiOperatingClient(id -> {
            assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
            calls.add(id);
            return response(id.equals("211") ? "999" : id, id.equals("212") ? Map.of() : Map.of("infocenterleports", "02-123-4567"));
        });
        var results = new ArrayList<TourOperatingImportService.Result>();
        var summary = new TourOperatingImportService(client, writer).importAll(results::add);
        assertThat(summary).isEqualTo(new TourOperatingImportService.Summary(1, 1, 1, 1, false));
        assertThat(calls).containsExactly("211", "212", "213");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM place_operating_info", Integer.class)).isEqualTo(2);
        assertThat(jdbc.queryForMap("SELECT verification_status, verified_at FROM place_operating_info WHERE source_content_id='213'"))
                .containsEntry("verification_status", "UNVERIFIED").containsEntry("verified_at", null);

        calls.clear();
        var retryClient = new TourApiOperatingClient(id -> {
            calls.add(id);
            return response(id, id.equals("212") ? Map.of() : Map.of("usetimeleports", "재수집 시간"));
        });
        var retry = new TourOperatingImportService(retryClient, writer).importAll(result -> { });
        assertThat(retry).isEqualTo(new TourOperatingImportService.Summary(1, 2, 1, 0, false));
        assertThat(calls).containsExactly("211", "212");
        assertThat(jdbc.queryForMap("SELECT * FROM place_operating_info WHERE source_content_id='210'")).isEqualTo(existing);
    }

    private String response(String id, Map<String, String> fields) {
        var item = new java.util.HashMap<String, String>(fields);
        item.put("contentid", id); item.put("contenttypeid", "28");
        return JsonMapper.builder().build().writeValueAsString(Map.of("response", Map.of(
                "header", Map.of("resultCode", "0000"), "body", Map.of("items", Map.of("item", List.of(item))))));
    }
}
