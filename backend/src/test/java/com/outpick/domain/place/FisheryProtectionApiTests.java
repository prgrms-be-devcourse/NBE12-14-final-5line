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

import java.time.LocalDateTime;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.hamcrest.Matchers.*;

@SpringBootTest(properties="spring.jpa.hibernate.ddl-auto=validate")
@AutoConfigureMockMvc
@Testcontainers
@Transactional
class FisheryProtectionApiTests {
    @Container @ServiceConnection static MySQLContainer mysql=new MySQLContainer("mysql:8.4.13");
    @Autowired EntityManager em;
    @Autowired JdbcTemplate jdbc;
    @Autowired MockMvc mvc;

    @Test @DisplayName("검수 대기 규정은 법적 금어기나 채취 허용으로 확정하지 않는다")
    void reviewRulesNeverAuthorizeHarvest() throws Exception {
        Region region=Region.builder().name("제주특별자치도").build(); em.persist(region);
        Place place=Place.builder().region(region).name("테스트 해변")
                .address("제주특별자치도 제주시 추자면 추자도")
                .publishStatus(PlacePublishStatus.PUBLISHED).build();em.persist(place);
        ActivityCategory category=ActivityCategory.builder().name("해수욕장").build();em.persist(category);
        ActivityType activity=ActivityType.builder().category(category).name("해수욕장").build();em.persist(activity);
        em.persist(PlaceActivity.builder().place(place).activityType(activity).environmentType("OUTDOOR").build());
        em.flush();

        mvc.perform(get("/api/v1/places/{id}/fishery-protection",place.getId()).param("visitDate","2026-07-01"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.harvestPermission").value("UNKNOWN"))
                .andExpect(jsonPath("$.confirmedClosed",hasSize(0)))
                .andExpect(jsonPath("$.reviewCandidates[?(@.resourceName == '소라' && @.ruleKind == 'CLOSED_SEASON')].region",hasItem("제주 추자도")))
                .andExpect(jsonPath("$.reviewCandidates[?(@.resourceName == '소라' && @.ruleKind == 'CLOSED_SEASON')].region",not(hasItem("제주"))));

        jdbc.update("""
                UPDATE fishery_protection_rule r JOIN fishery_resource f ON f.resource_id=r.resource_id
                SET r.verification_status='VERIFIED',r.last_verified_at=?
                WHERE f.name='소라' AND r.rule_kind='CLOSED_SEASON' AND r.region_name='제주 추자도'
                """, LocalDateTime.now());
        mvc.perform(get("/api/v1/places/{id}/fishery-protection",place.getId()).param("visitDate","2026-07-01"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.confirmedClosed[0].resourceName").value("소라"))
                .andExpect(jsonPath("$.harvestPermission").value("UNKNOWN"));
        mvc.perform(get("/api/v1/places/{id}/fishery-protection",place.getId()).param("visitDate","2026-06-30"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.confirmedClosed",hasSize(0)))
                .andExpect(jsonPath("$.harvestPermission").value("UNKNOWN"));
    }
}
