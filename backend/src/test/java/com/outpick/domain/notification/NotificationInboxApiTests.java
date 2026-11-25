package com.outpick.domain.notification;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.transaction.TestTransaction;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.mysql.MySQLContainer;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = "spring.jpa.hibernate.ddl-auto=validate")
@AutoConfigureMockMvc
@Import(NotificationInboxApiTests.TestTime.class)
@Testcontainers
@Transactional
class NotificationInboxApiTests {
    private static final String INBOX = "/api/v1/members/me/notifications";
    private static final LocalDateTime NOW = LocalDateTime.parse("2026-11-25T11:00:05");

    @Container @ServiceConnection
    static MySQLContainer mysql = new MySQLContainer("mysql:8.4.13");

    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired PasswordEncoder passwordEncoder;

    @TestConfiguration(proxyBeanMethods = false)
    static class TestTime {
        @Bean @Primary
        Clock fixedPlaceClock() {
            var seoul = ZoneId.of("Asia/Seoul");
            return Clock.fixed(NOW.atZone(seoul).toInstant(), seoul);
        }
    }

    @Test
    @DisplayName("회원별 알림을 생성 시각과 ID 내림차순으로 페이지 조회한다")
    void pagesOnlyTheSignedInMembersNotifications() throws Exception {
        var owner = signIn(1001);
        var other = signIn(1002);
        var createdAt = NOW.minusMinutes(2);
        createNotice(51, "휴무 안내", "https://example.com/holiday", NOW.minusDays(1));
        createNotice(52, "운영 시간 변경", null, NOW.minusDays(1));
        createNotice(53, "지난 안내", "ftp://example.com/notice", NOW.minusDays(1));
        addNotification(101, 51, 1001, createdAt, null);
        addNotification(102, 52, 1001, createdAt, NOW.minusMinutes(1));
        addNotification(103, 51, 1002, NOW, null);
        addNotification(104, 53, 1001, createdAt.minusDays(1), null);

        mvc.perform(get(INBOX).session(owner).param("size", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(1))
                .andExpect(jsonPath("$.totalElements").value(3))
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].notificationId").value(102))
                .andExpect(jsonPath("$.items[0].eventId").value(52))
                .andExpect(jsonPath("$.items[0].placeId").value(52))
                .andExpect(jsonPath("$.items[0].placeNoticeId").value(52))
                .andExpect(jsonPath("$.items[0].title").value("운영 시간 변경"))
                .andExpect(jsonPath("$.items[0].createdAt").value(createdAt.toString()))
                .andExpect(jsonPath("$.items[0].readAt").value(NOW.minusMinutes(1).toString()))
                .andExpect(jsonPath("$.items[0].sourceUrl").value(nullValue()))
                .andExpect(jsonPath("$.items[0].noticeAvailable").value(true));

        mvc.perform(get(INBOX).session(owner).param("page", "1").param("size", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].notificationId").value(101))
                .andExpect(jsonPath("$.items[0].readAt").value(nullValue()))
                .andExpect(jsonPath("$.items[0].sourceUrl").value("https://example.com/holiday"));

        mvc.perform(get(INBOX).session(owner).param("page", "2").param("size", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].notificationId").value(104))
                .andExpect(jsonPath("$.items[0].noticeAvailable").value(true))
                .andExpect(jsonPath("$.items[0].sourceUrl").value(nullValue()));

        mvc.perform(get(INBOX).session(owner).param("page", "3").param("size", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(3))
                .andExpect(jsonPath("$.items").isEmpty());

        mvc.perform(get(INBOX).session(other))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.size").value(20))
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.items[0].notificationId").value(103));

        mvc.perform(get(INBOX).session(owner).param("unreadOnly", "true").param("size", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(2))
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].notificationId").value(101));

        mvc.perform(get(INBOX).session(owner).param("unreadOnly", "true").param("page", "1").param("size", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(2))
                .andExpect(jsonPath("$.items[0].notificationId").value(104));
    }

    @Test
    @DisplayName("숨김·만료·미노출 공지의 알림 이력을 유지하고 제목과 링크는 감춘다")
    void hidesUnavailableNoticeDetailsButKeepsInboxHistory() throws Exception {
        var owner = signIn(1001);
        for (int id = 51; id <= 56; id++) {
            createNotice(id, "운영 공지 " + id, "https://example.com/notice", NOW.minusDays(1));
            addNotification(id, id, 1001, NOW.minusMinutes(2), NOW.minusMinutes(1));
        }
        jdbc.update("UPDATE place_notice SET publish_status = 2 WHERE place_notice_id = 51");
        jdbc.update("UPDATE place_notice SET valid_until = ? WHERE place_notice_id = 52", NOW);
        jdbc.update("UPDATE place_notice SET valid_from = ? WHERE place_notice_id = 53", NOW.plusSeconds(1));
        jdbc.update("UPDATE place SET publish_status = 2 WHERE place_id = 54");
        jdbc.update("UPDATE source_record SET verified_at = NULL WHERE source_record_id = 55");
        jdbc.update("UPDATE place_notice SET valid_from = ? WHERE place_notice_id = 56", NOW);

        var result = mvc.perform(get(INBOX).session(owner))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(6))
                .andExpect(jsonPath("$.items[0].notificationId").value(56))
                .andExpect(jsonPath("$.items[0].noticeAvailable").value(true))
                .andExpect(jsonPath("$.items[0].title").value("운영 공지 56"))
                .andExpect(jsonPath("$.items[0].sourceUrl").value("https://example.com/notice"));
        for (int index = 1; index <= 5; index++) {
            String item = "$.items[" + index + "]";
            result.andExpect(jsonPath(item + ".notificationId").value(56 - index))
                    .andExpect(jsonPath(item + ".noticeAvailable").value(false))
                    .andExpect(jsonPath(item + ".title").value(nullValue()))
                    .andExpect(jsonPath(item + ".sourceUrl").value(nullValue()))
                    .andExpect(jsonPath(item + ".readAt").value(NOW.minusMinutes(1).toString()));
        }
    }

    @Test
    @DisplayName("알림함은 로그인 세션을 요구하고 빈 목록과 잘못된 페이지 요청을 처리한다")
    void enforcesSessionAndPageBounds() throws Exception {
        mvc.perform(get(INBOX)).andExpect(status().isUnauthorized());

        var owner = signIn(1001);
        mvc.perform(get(INBOX).session(owner))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items").isEmpty())
                .andExpect(jsonPath("$.totalElements").value(0));

        mvc.perform(get(INBOX).session(owner).param("page", "-1"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.fieldErrors[0].field").value("page"));
        mvc.perform(get(INBOX).session(owner).param("size", "51"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors[0].field").value("size"));
    }

    @Test
    @DisplayName("본인 알림의 최초 읽음 시각을 저장하고 목록에 반영한다")
    void marksOwnNotificationReadAndPreservesTheFirstReadTime() throws Exception {
        var owner = signIn(1001);
        var alreadyReadAt = NOW.minusMinutes(1);
        createNotice(51, "새 휴무 안내", null, NOW.minusDays(1));
        createNotice(52, "이전 휴무 안내", null, NOW.minusDays(1));
        addNotification(101, 51, 1001, NOW.minusMinutes(2), null);
        addNotification(102, 52, 1001, NOW.minusMinutes(3), alreadyReadAt);

        mvc.perform(markRead(owner, 101))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.notificationId").value(101))
                .andExpect(jsonPath("$.readAt").value(NOW.toString()));
        mvc.perform(markRead(owner, 102))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.readAt").value(alreadyReadAt.toString()));
        assertThat(storedReadAt(101)).isEqualTo(NOW);
        assertThat(storedReadAt(102)).isEqualTo(alreadyReadAt);

        mvc.perform(get(INBOX).session(owner))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].readAt").value(NOW.toString()))
                .andExpect(jsonPath("$.items[1].readAt").value(alreadyReadAt.toString()));
    }

    @Test
    @DisplayName("읽음 요청은 세션과 CSRF를 검증하고 본인 외 알림의 존재를 노출하지 않는다")
    void enforcesReadRequestAuthenticationCsrfAndOwnership() throws Exception {
        var owner = signIn(1001);
        var other = signIn(1002);
        createNotice(51, "휴무 안내", null, NOW.minusDays(1));
        addNotification(101, 51, 1001, NOW.minusMinutes(2), null);
        addNotification(102, 51, 1002, NOW.minusMinutes(2), null);

        mvc.perform(markRead(new MockHttpSession(), 101)).andExpect(status().isUnauthorized());
        mvc.perform(patch(INBOX + "/{notificationId}/read", 101).session(owner))
                .andExpect(status().isForbidden());
        mvc.perform(markRead(owner, 0))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors[0].field").value("notificationId"));
        mvc.perform(markRead(other, 101))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("NOTIFICATION_NOT_FOUND"));
        mvc.perform(markRead(other, 999))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOTIFICATION_NOT_FOUND"));
        assertThat(storedReadAt(101)).isNull();
        assertThat(storedReadAt(102)).isNull();
    }

    @Test
    @DisplayName("같은 알림을 동시에 읽음 처리해도 두 요청에 저장된 읽음 시각을 반환한다")
    void returnsTheStoredReadTimeForConcurrentRequests() throws Exception {
        var owner = signIn(1101);
        createNotice(61, "휴무 안내", null, NOW.minusDays(1));
        addNotification(201, 61, 1101, NOW.minusMinutes(2), null);
        var firstRequest = markRead(owner, 201);
        var secondRequest = markRead(owner, 201);
        TestTransaction.flagForCommit();
        TestTransaction.end();

        var start = new CyclicBarrier(2);
        try (var requests = Executors.newFixedThreadPool(2)) {
            var first = requests.submit(() -> {
                start.await(5, TimeUnit.SECONDS);
                return mvc.perform(firstRequest).andExpect(status().isOk()).andReturn();
            });
            var second = requests.submit(() -> {
                start.await(5, TimeUnit.SECONDS);
                return mvc.perform(secondRequest).andExpect(status().isOk()).andReturn();
            });
            var firstResponse = first.get(10, TimeUnit.SECONDS).getResponse().getContentAsString();
            var secondResponse = second.get(10, TimeUnit.SECONDS).getResponse().getContentAsString();
            assertThat(JsonPath.<String>read(firstResponse, "$.readAt")).isEqualTo(NOW.toString());
            assertThat(JsonPath.<String>read(secondResponse, "$.readAt")).isEqualTo(NOW.toString());
        }
        assertThat(storedReadAt(201)).isEqualTo(NOW);
    }

    private MockHttpServletRequestBuilder markRead(MockHttpSession session, long notificationId) throws Exception {
        var csrf = mvc.perform(get("/api/v1/auth/csrf").session(session)).andReturn();
        var csrfData = JsonPath.parse(csrf.getResponse().getContentAsString());
        return patch(INBOX + "/{notificationId}/read", notificationId).session(session)
                .header(csrfData.<String>read("$.headerName"), csrfData.<String>read("$.token"));
    }

    private LocalDateTime storedReadAt(long notificationId) {
        return jdbc.queryForObject("SELECT read_at FROM notification WHERE notification_id = ?",
                LocalDateTime.class, notificationId);
    }

    private MockHttpSession signIn(long memberId) throws Exception {
        String email = "inbox-" + memberId + "@example.com";
        String password = "test-password";
        jdbc.update("""
                INSERT INTO `user` (user_id, email, nickname, password_hash, created_at, updated_at)
                VALUES (?, ?, '알림 테스트', ?, ?, ?)
                """, memberId, email, passwordEncoder.encode(password), NOW, NOW);
        var csrf = mvc.perform(get("/api/v1/auth/csrf")).andReturn();
        var session = (MockHttpSession) csrf.getRequest().getSession(false);
        var csrfData = JsonPath.parse(csrf.getResponse().getContentAsString());
        mvc.perform(post("/api/v1/auth/login").session(session)
                        .header(csrfData.<String>read("$.headerName"), csrfData.<String>read("$.token"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s","password":"%s"}
                                """.formatted(email, password)))
                .andExpect(status().isOk());
        return session;
    }

    private void createNotice(long id, String title, String url, LocalDateTime verifiedAt) {
        jdbc.update("INSERT INTO region (region_id, name) VALUES (?, '알림 지역')", id);
        jdbc.update("INSERT INTO place (place_id, region_id, publish_status) VALUES (?, ?, 1)", id, id);
        jdbc.update("""
                INSERT INTO source_record (source_record_id, place_id, url, verified_at) VALUES (?, ?, ?, ?)
                """, id, id, url, verifiedAt);
        jdbc.update("""
                INSERT INTO place_notice (place_notice_id, place_id, source_record_id, title, publish_status)
                VALUES (?, ?, ?, ?, 1)
                """, id, id, id, title);
        jdbc.update("""
                INSERT INTO place_change_event (place_change_event_id, place_notice_id, task_status, eligible_at, processed_at)
                VALUES (?, ?, 'COMPLETED', ?, ?)
                """, id, id, NOW.minusMinutes(3), NOW.minusMinutes(2));
    }

    private void addNotification(long id, long eventId, long memberId, LocalDateTime createdAt, LocalDateTime readAt) {
        jdbc.update("""
                INSERT INTO notification (notification_id, place_change_event_id, user_id, created_at, read_at)
                VALUES (?, ?, ?, ?, ?)
                """, id, eventId, memberId, createdAt, readAt);
    }
}
