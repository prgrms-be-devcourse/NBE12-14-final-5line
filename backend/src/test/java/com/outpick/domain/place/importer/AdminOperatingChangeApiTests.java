package com.outpick.domain.place.importer;

import com.jayway.jsonpath.JsonPath;
import com.outpick.domain.member.entity.Member;
import com.outpick.domain.member.repository.MemberRepository;
import com.outpick.domain.member.security.MemberSessionPrincipal;
import com.outpick.domain.place.entity.PlacePublishStatus;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextImpl;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.mysql.MySQLContainer;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.web.context.HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "spring.jpa.hibernate.ddl-auto=validate",
        "OUTPICK_TOUR_OPERATING_ENABLED=true",
        "OUTPICK_TOUR_OPERATING_CRON=0 0 0 1 1 *",
        "TOUR_API_KEY=test-key"
})
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
    @Autowired TourOperatingChangeReader changeReader;
    @Autowired TourOperatingCandidateStore candidateStore;
    @Autowired JdbcTemplate jdbc;
    @Autowired EntityManager em;

    @Test
    @DisplayName("비로그인 사용자와 일반 회원은 운영 정보 검수 후보를 조회할 수 없다")
    void rejectsGuestAndNonAdminMember() throws Exception {
        mvc.perform(get(PATH)).andExpect(status().isUnauthorized());
        var member = members.saveAndFlush(new Member("operation-review-user@example.com", "일반 회원", "hash"));
        mvc.perform(get(PATH).session(session(member))).andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("페이지 크기가 1이어도 같은 공개 장소의 미처리 후보를 한 묶음에 모두 반환한다")
    void returnsPendingPublicCandidatesWithValuesAndSources() throws Exception {
        var admin = members.saveAndFlush(Member.createAdmin("operation-review-admin@example.com", "운영자", "hash"));
        long first = place("401", PlacePublishStatus.PUBLISHED);
        long second = place("402", PlacePublishStatus.PUBLISHED);
        long hidden = place("403", PlacePublishStatus.HIDDEN);
        addCandidate(first, "10:00", false);
        addCandidate(second, "usageFee", null, "65,000원", false);
        addCandidate(second, "11:00", false);
        addCandidate(second, "12:00", true);
        addCandidate(hidden, "13:00", false);

        mvc.perform(get(PATH).param("size", "1").session(session(admin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(2))
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(1))
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].placeId").value(second))
                .andExpect(jsonPath("$.items[0].placeName").value("테스트 시설"))
                .andExpect(jsonPath("$.items[0].candidates.length()").value(2))
                .andExpect(jsonPath("$.items[0].candidates[0].candidateId").isNumber())
                .andExpect(jsonPath("$.items[0].candidates[0].field").value("operatingHours"))
                .andExpect(jsonPath("$.items[0].candidates[0].previousValue").value("09:00"))
                .andExpect(jsonPath("$.items[0].candidates[0].newValue").value("11:00"))
                .andExpect(jsonPath("$.items[0].candidates[0].previousSourceUrl").value("https://example.com/previous"))
                .andExpect(jsonPath("$.items[0].candidates[0].newSourceUrl").value("https://www.data.go.kr/data/15101578/openapi.do"))
                .andExpect(jsonPath("$.items[0].candidates[0].detectedAt").exists())
                .andExpect(jsonPath("$.items[0].candidates[1].field").value("usageFee"));
        mvc.perform(get(PATH).param("page", "1").param("size", "1").session(session(admin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].placeId").value(first));
        mvc.perform(get(PATH).param("size", "51").session(session(admin))).andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("운영자가 승인하면 장소 상세에 새 값이 보이고 같은 후보의 재처리는 거절한다")
    void appliesApprovedValueOnceAndRecordsReviewer() throws Exception {
        var admin = members.saveAndFlush(Member.createAdmin("approve@example.com", "운영자", "hash"));
        long placeId = place("410", PlacePublishStatus.PUBLISHED);
        addCandidate(placeId, "10:00", false);
        long candidateId = candidateId(placeId);
        var adminSession = session(admin);
        String body = """
                {"decision":"APPROVED","reason":"공식 홈페이지 확인"}
                """;
        mvc.perform(patch(PATH + "/{id}/resolution", candidateId).session(adminSession)
                .contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isForbidden());
        resolve(adminSession, candidateId, body).andExpect(status().isNoContent());
        em.clear();
        mvc.perform(get("/api/v1/places/{id}", placeId)).andExpect(status().isOk())
                .andExpect(jsonPath("$.operatingInfo.operatingHours").value("10:00"));
        mvc.perform(get(PATH).session(adminSession)).andExpect(jsonPath("$.totalElements").value(0));
        assertThat(jdbc.queryForMap("""
                SELECT decision, resolved_by, resolution_reason FROM place_operating_change_candidate WHERE candidate_id = ?
                """, candidateId)).containsEntry("decision", "APPROVED")
                .containsEntry("resolved_by", admin.getId()).containsEntry("resolution_reason", "공식 홈페이지 확인");
        resolve(adminSession, candidateId, body).andExpect(status().isConflict());
    }

    @Test
    @DisplayName("일반 회원은 처리할 수 없고 오래된 후보는 승인 대신 사유를 남겨 반려할 수 있다")
    void preventsStaleApprovalAndRequiresReasonForRejection() throws Exception {
        var admin = members.saveAndFlush(Member.createAdmin("reject@example.com", "운영자", "hash"));
        var member = members.saveAndFlush(new Member("reject-member@example.com", "회원", "hash"));
        long placeId = place("411", PlacePublishStatus.PUBLISHED);
        addCandidate(placeId, "10:00", false);
        long candidateId = candidateId(placeId);
        jdbc.update("UPDATE place_operating_info SET operating_hours = '11:00' WHERE place_id = ?", placeId);

        String approval = """
                {"decision":"APPROVED"}
                """;
        resolve(session(member), candidateId, approval).andExpect(status().isForbidden());
        var adminSession = session(admin);
        resolve(adminSession, candidateId, approval).andExpect(status().isConflict());
        resolve(adminSession, candidateId, """
                {"decision":"REJECTED","reason":" "}
                """).andExpect(status().isBadRequest());
        resolve(adminSession, candidateId, """
                {"decision":"REJECTED","reason":"공식 안내와 불일치"}
                """).andExpect(status().isNoContent());

        assertThat(jdbc.queryForMap("""
                SELECT decision, resolution_reason FROM place_operating_change_candidate WHERE candidate_id = ?
                """, candidateId)).containsEntry("decision", "REJECTED").containsEntry("resolution_reason", "공식 안내와 불일치");
        assertThat(jdbc.queryForObject("SELECT operating_hours FROM place_operating_info WHERE place_id = ?",
                String.class, placeId)).isEqualTo("11:00");
    }

    @Test
    @DisplayName("정기 비교는 변경 후보를 검수 목록에 등록하고 새 실행 객체도 DB 위치부터 재개한다")
    void resumesFromTheStoredCursorAndMakesChangesVisibleToAdmins() throws Exception {
        var admin = members.saveAndFlush(Member.createAdmin("scheduler@example.com", "운영자", "hash"));
        long first = place("420", PlacePublishStatus.PUBLISHED);
        place("421", PlacePublishStatus.PUBLISHED);
        var client = mock(TourApiOperatingClient.class);
        when(client.getOperatingInfo("420")).thenReturn(new TourOperatingSnapshot("420", "10:00", null, null));
        when(client.getOperatingInfo("421")).thenReturn(new TourOperatingSnapshot("421", "09:00", null, null));

        new TourOperatingComparisonScheduler(changeReader, candidateStore, client, 1).compareAndSave();
        assertThat(changeReader.findScheduledCursor()).isEqualTo(first);
        verify(client).getOperatingInfo("420");
        verifyNoMoreInteractions(client);

        new TourOperatingComparisonScheduler(changeReader, candidateStore, client, 1).compareAndSave();
        verify(client).getOperatingInfo("421");
        assertThat(changeReader.findScheduledCursor()).isZero();
        mvc.perform(get(PATH).session(session(admin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.items[0].placeId").value(first))
                .andExpect(jsonPath("$.items[0].candidates[0].newValue").value("10:00"));
        assertThat(jdbc.queryForObject("SELECT operating_hours FROM place_operating_info WHERE place_id = ?", String.class,
                first)).isEqualTo("09:00");
    }

    private long candidateId(long placeId) {
        return jdbc.queryForObject("SELECT candidate_id FROM place_operating_change_candidate WHERE place_id = ?",
                Long.class, placeId);
    }

    private ResultActions resolve(MockHttpSession session, long candidateId, String input) throws Exception {
        var response = mvc.perform(get("/api/v1/auth/csrf").session(session)).andExpect(status().isOk()).andReturn();
        var csrf = JsonPath.parse(response.getResponse().getContentAsString());
        return mvc.perform(patch(PATH + "/{id}/resolution", candidateId).session(session)
                .header(csrf.<String>read("$.headerName"), csrf.<String>read("$.token"))
                .contentType(MediaType.APPLICATION_JSON).content(input));
    }

    private long place(String contentId, PlacePublishStatus status) {
        long id = placeWriter.save(new TourApiNormalizer()
                .normalize(TourApiTests.target(contentId), TourApiTests.place(contentId, "126.1", ""))).placeId();
        jdbc.update("UPDATE place SET publish_status = ? WHERE place_id = ?", status.getCode(), id);
        operatingWriter.saveInitial(new TourOperatingSnapshot(contentId, "09:00", null, null));
        return id;
    }

    private void addCandidate(long placeId, String value, boolean resolved) {
        addCandidate(placeId, "operatingHours", "09:00", value, resolved);
    }

    private void addCandidate(long placeId, String field, String previousValue, String value, boolean resolved) {
        jdbc.update("""
                INSERT INTO place_operating_change_candidate (place_id, field_name, previous_value, new_value,
                    previous_source_url, new_source_url, values_hash, detected_at, resolved_at)
                VALUES (?, ?, ?, ?, 'https://example.com/previous',
                    'https://www.data.go.kr/data/15101578/openapi.do', UNHEX(SHA2(?, 256)),
                    '2026-11-26 09:00:00', CASE WHEN ? THEN CURRENT_TIMESTAMP ELSE NULL END)
                """, placeId, field, previousValue, value, value, resolved);
    }

    private MockHttpSession session(Member member) {
        var principal = new MemberSessionPrincipal(member.getId(), member.getAuthVersion());
        var context = new SecurityContextImpl(UsernamePasswordAuthenticationToken.authenticated(principal, null, List.of()));
        var session = new MockHttpSession();
        session.setAttribute(SPRING_SECURITY_CONTEXT_KEY, context);
        return session;
    }
}
