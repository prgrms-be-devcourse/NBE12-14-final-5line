package com.outpick.domain.place.importer;

import com.outpick.domain.member.entity.Member;
import com.outpick.domain.member.repository.MemberRepository;
import com.outpick.domain.member.security.MemberSessionPrincipal;
import com.outpick.domain.place.entity.PlacePublishStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextImpl;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.mysql.MySQLContainer;

import java.util.List;

import static org.springframework.security.web.context.HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = "spring.jpa.hibernate.ddl-auto=validate")
@AutoConfigureMockMvc
@Testcontainers
@Transactional
class AdminOperatingChangeApiTests {
    private static final String PATH = "/api/v1/admin/place-operating-changes";

    @Container @ServiceConnection
    static MySQLContainer mysql = new MySQLContainer("mysql:8.4.13");

    @Autowired MockMvc mvc;
    @Autowired MemberRepository members;
    @Autowired TourImportWriter placeWriter;
    @Autowired TourOperatingInfoWriter operatingWriter;
    @Autowired JdbcTemplate jdbc;

    @Test
    @DisplayName("비로그인 사용자와 일반 회원은 운영 정보 검수 후보를 조회할 수 없다")
    void rejectsGuestAndNonAdminMember() throws Exception {
        mvc.perform(get(PATH)).andExpect(status().isUnauthorized());
        var member = members.saveAndFlush(new Member("operation-review-user@example.com", "일반 회원", "hash"));
        mvc.perform(get(PATH).session(session(member))).andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("운영자는 공개 장소의 미처리 후보만 최신순으로 나눠 보고 이전 값과 출처를 확인할 수 있다")
    void returnsPendingPublicCandidatesWithValuesAndSources() throws Exception {
        var admin = members.saveAndFlush(Member.createAdmin("operation-review-admin@example.com", "운영자", "hash"));
        long first = place("401", PlacePublishStatus.PUBLISHED);
        long second = place("402", PlacePublishStatus.PUBLISHED);
        long hidden = place("403", PlacePublishStatus.HIDDEN);
        addCandidate(first, "10:00", false);
        addCandidate(second, "11:00", false);
        addCandidate(second, "12:00", true);
        addCandidate(hidden, "13:00", false);

        mvc.perform(get(PATH).param("size", "1").session(session(admin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(2))
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(1))
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].candidateId").isNumber())
                .andExpect(jsonPath("$.items[0].placeId").value(second))
                .andExpect(jsonPath("$.items[0].placeName").value("테스트 시설"))
                .andExpect(jsonPath("$.items[0].field").value("operatingHours"))
                .andExpect(jsonPath("$.items[0].previousValue").value("09:00"))
                .andExpect(jsonPath("$.items[0].newValue").value("11:00"))
                .andExpect(jsonPath("$.items[0].previousSourceUrl").value("https://example.com/previous"))
                .andExpect(jsonPath("$.items[0].newSourceUrl").value("https://www.data.go.kr/data/15101578/openapi.do"))
                .andExpect(jsonPath("$.items[0].detectedAt").exists());
        mvc.perform(get(PATH).param("page", "1").param("size", "1").session(session(admin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].placeId").value(first));
        mvc.perform(get(PATH).param("size", "51").session(session(admin))).andExpect(status().isBadRequest());
    }

    private long place(String contentId, PlacePublishStatus status) {
        long id = placeWriter.save(new TourApiNormalizer()
                .normalize(TourApiTests.target(contentId), TourApiTests.place(contentId, "126.1", ""))).placeId();
        jdbc.update("UPDATE place SET publish_status = ? WHERE place_id = ?", status.getCode(), id);
        operatingWriter.saveInitial(new TourOperatingSnapshot(contentId, "09:00", null, null));
        return id;
    }

    private void addCandidate(long placeId, String value, boolean resolved) {
        jdbc.update("""
                INSERT INTO place_operating_change_candidate (place_id, field_name, previous_value, new_value,
                    previous_source_url, new_source_url, values_hash, detected_at, resolved_at)
                VALUES (?, 'operatingHours', '09:00', ?, 'https://example.com/previous',
                    'https://www.data.go.kr/data/15101578/openapi.do', UNHEX(SHA2(?, 256)),
                    '2026-11-26 09:00:00', CASE WHEN ? THEN CURRENT_TIMESTAMP ELSE NULL END)
                """, placeId, value, value, resolved);
    }

    private MockHttpSession session(Member member) {
        var principal = new MemberSessionPrincipal(member.getId(), member.getAuthVersion());
        var context = new SecurityContextImpl(UsernamePasswordAuthenticationToken.authenticated(principal, null, List.of()));
        var session = new MockHttpSession();
        session.setAttribute(SPRING_SECURITY_CONTEXT_KEY, context);
        return session;
    }
}
