package com.outpick.domain.place;

import com.outpick.domain.place.entity.*;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.mysql.MySQLContainer;

import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.hamcrest.Matchers.hasSize;

@SpringBootTest(properties = "spring.jpa.hibernate.ddl-auto=validate")
@AutoConfigureMockMvc
@Testcontainers
@Transactional
class SnorkelingSafetyApiTests {
    @Container @ServiceConnection static MySQLContainer mysql=new MySQLContainer("mysql:8.4.13");
    @Autowired EntityManager em;
    @Autowired JdbcTemplate jdbc;
    @Autowired MockMvc mvc;

    @Test @DisplayName("입수 미확인·허용·금지 상태에 따라 공개 검색과 안전정보를 구분한다")
    void preventsUnverifiedAndProhibitedSnorkelingRecommendations() throws Exception {
        Region region=Region.builder().name("해안 지역").build();em.persist(region);
        Place place=Place.builder().region(region).name("검수 해변")
                .publishStatus(PlacePublishStatus.PUBLISHED).build();em.persist(place);
        ActivityCategory beach=ActivityCategory.builder().name("해수욕장").build();em.persist(beach);
        ActivityType beachType=ActivityType.builder().category(beach).name("해수욕장").build();em.persist(beachType);
        em.persist(PlaceActivity.builder().place(place).activityType(beachType).environmentType("OUTDOOR").build());
        ActivityCategory snorkel=em.createQuery("select c from ActivityCategory c where c.name='스노클링'",ActivityCategory.class)
                .getSingleResult();
        ActivityType snorkelType=em.createQuery("select a from ActivityType a where a.category=:category and a.name='스노클링'",ActivityType.class)
                .setParameter("category",snorkel).getSingleResult();
        em.persist(PlaceActivity.builder().place(place).activityType(snorkelType).environmentType("OUTDOOR").build());
        em.flush();
        mvc.perform(get("/api/v1/places/{id}/snorkeling-safety",place.getId()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.entryStatus").value("UNKNOWN"))
                .andExpect(jsonPath("$.recommendable").value(false))
                .andExpect(jsonPath("$.verifiedSafetyDetails").doesNotExist());
        mvc.perform(get("/api/v1/places").param("activityTypeIds",snorkelType.getId().toString()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(1));
        mvc.perform(get("/api/v1/places/{id}",place.getId()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.activities",hasSize(2)));

        LocalDate today=LocalDate.now();LocalDateTime now=LocalDateTime.now();
        jdbc.update("""
            INSERT INTO place_detail_fact(place_id,field_key,value_json,source_kind,source_url,
              checked_at,verified_at,verification_status,valid_from,valid_until)
            VALUES(?,'snorkelingSiteType','"BREAKWATER"','OFFICIAL_WEBSITE',?, ?,?,'VERIFIED',?,?)
            """,place.getId(),"https://example.com/type",now,now,today.minusDays(1),today.plusDays(1));
        jdbc.update("""
            INSERT INTO place_detail_fact(place_id,field_key,value_json,source_kind,source_url,
              checked_at,verified_at,verification_status,valid_from,valid_until)
            VALUES(?,'snorkelingSafety','{"entryStatus":"ALLOWED","lifeguardStatus":"UNKNOWN"}',
              'OFFICIAL_WEBSITE',?, ?,?,'VERIFIED',?,?)
            """,place.getId(),"https://example.com/entry",now,now,today.minusDays(1),today.plusDays(1));
        mvc.perform(get("/api/v1/places").param("activityTypeIds",snorkelType.getId().toString()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(1));
        mvc.perform(get("/api/v1/places/{id}/snorkeling-safety",place.getId()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.siteType").value("BREAKWATER"))
                .andExpect(jsonPath("$.entryStatus").value("ALLOWED"))
                .andExpect(jsonPath("$.recommendable").value(true))
                .andExpect(jsonPath("$.verifiedSafetyDetails.lifeguardStatus").value("UNKNOWN"))
                .andExpect(jsonPath("$.commonPrecautions",hasSize(10)));
        jdbc.update("""
            INSERT INTO place_detail_fact(place_id,field_key,value_json,source_kind,source_url,
              checked_at,verified_at,verification_status)
            VALUES(?,'snorkelingSafety','{"entryStatus":"PROHIBITED"}',
              'OFFICIAL_PUBLIC_DATA',?, ?,?,'VERIFIED')
            """,place.getId(),"https://example.com/prohibition",now,now);
        mvc.perform(get("/api/v1/places").param("activityTypeIds",snorkelType.getId().toString()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(1));
        mvc.perform(get("/api/v1/places/{id}/snorkeling-safety",place.getId()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.entryStatus").value("PROHIBITED"))
                .andExpect(jsonPath("$.recommendable").value(false));
    }
}
