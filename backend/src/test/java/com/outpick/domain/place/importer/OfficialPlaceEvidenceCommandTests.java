package com.outpick.domain.place.importer;

import com.outpick.domain.place.entity.Place;
import com.outpick.domain.place.entity.Region;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceUtils;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.mysql.MySQLContainer;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = "spring.jpa.hibernate.ddl-auto=validate")
@Testcontainers
@Transactional
class OfficialPlaceEvidenceCommandTests {
    @Container @ServiceConnection static MySQLContainer mysql=new MySQLContainer("mysql:8.4.13");
    @Autowired EntityManager em;
    @Autowired JdbcTemplate jdbc;
    @Autowired DataSource source;
    private static final JsonMapper JSON=JsonMapper.builder().build();

    @Test @DisplayName("구조화된 차량·시간권 가격과 기존 연령별 가격을 검수 대기로 저장하고 재실행 중복을 막는다")
    void importsStructuredAndLegacyPricesWithoutDuplicates() throws Exception {
        Region region=Region.builder().name("가격 지역").build();em.persist(region);
        Place place=Place.builder().region(region).name("가격 대상").build();em.persist(place);em.flush();
        JsonNode patch=JSON.readTree("""
                {"sourceType":"OFFICIAL_WEBSITE","sourceUrl":"https://example.com/prices","checkedAt":"2026-11-27"}
                """);
        JsonNode structured=JSON.readTree("""
                [{"priceKind":"RAILBIKE","ticketName":"2인승","priceType":"RIDE",
                  "amountKrw":30000,"priceUnit":"VEHICLE","passengerCount":2,
                  "rawPriceText":"2인승 30,000원","validFrom":"2026-11-01","validUntil":"2026-12-31"},
                 {"priceKind":"RAILBIKE","ticketName":"4인승","priceType":"RIDE",
                  "amountKrw":40000,"priceUnit":"VEHICLE","passengerCount":4}]
                """);
        JsonNode legacy=JSON.readTree("""
                {"adult":5000,"child":3000,"dayType":"WEEKDAY"}
                """);
        Connection db=DataSourceUtils.getConnection(source);
        try(PreparedStatement ps=db.prepareStatement("""
            INSERT IGNORE INTO place_price
            (place_id,price_kind,price_basis,ticket_name,price_type,age_band,day_type,season_label,
             valid_from,valid_until,amount_krw,free_of_charge,price_unit,duration_minutes,use_count,
             passenger_count,equipment_type,equipment_included,mandatory_extra_cost,residency_condition,
             raw_price_text,additional_conditions,source_kind,source_url,source_content_id,checked_at,
             verification_status,evidence_key)
            VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,'NEEDS_REVIEW',?)
            """)) {
            assertThat(OfficialPlaceEvidenceCommand.insertPrices(ps,place.getId(),"structuredPrices",structured,patch,"")).isEqualTo(2);
            assertThat(OfficialPlaceEvidenceCommand.insertPrices(ps,place.getId(),"price",legacy,patch,"")).isEqualTo(2);
            assertThat(OfficialPlaceEvidenceCommand.insertPrices(ps,place.getId(),"structuredPrices",structured,patch,"")).isZero();
        } finally { DataSourceUtils.releaseConnection(db,source); }
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM place_price WHERE place_id=?",Integer.class,place.getId())).isEqualTo(4);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM place_price WHERE place_id=? AND price_unit='VEHICLE'",Integer.class,place.getId())).isEqualTo(2);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM place_price WHERE place_id=? AND verification_status='VERIFIED'",Integer.class,place.getId())).isZero();
    }
}
