package com.outpick.domain.place;

import com.jayway.jsonpath.JsonPath;
import com.outpick.domain.place.entity.ActivityCategory;
import com.outpick.domain.place.entity.ActivityType;
import com.outpick.domain.place.entity.Place;
import com.outpick.domain.place.entity.PlaceActivity;
import com.outpick.domain.place.entity.PlaceActivitySeason;
import com.outpick.domain.place.entity.PlaceNotice;
import com.outpick.domain.place.entity.PlacePublishStatus;
import com.outpick.domain.place.entity.Region;
import com.outpick.domain.place.entity.SourceRecord;
import com.outpick.domain.place.repository.PlaceNoticeRepository;
import com.outpick.domain.place.repository.PlaceRepository;
import com.outpick.domain.place.repository.RegionRepository;
import com.outpick.domain.place.repository.SourceRecordRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
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
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.context.transaction.TestTransaction;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.mysql.MySQLContainer;

import java.time.Clock;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = "spring.jpa.hibernate.ddl-auto=validate")
@AutoConfigureMockMvc
@Import(AdminPlaceApiTests.TestTime.class)
@Testcontainers
@Transactional
class AdminPlaceApiTests {
    private static final LocalDateTime PUBLISHED_AT = LocalDateTime.parse("2026-11-24T10:00:00.123456");

    @TestConfiguration(proxyBeanMethods = false)
    static class TestTime {
        @Bean
        @Primary
        Clock fixedPlaceClock() {
            var seoul = ZoneId.of("Asia/Seoul");
            return Clock.fixed(PUBLISHED_AT.atZone(seoul).toInstant(), seoul);
        }
    }

    @Container @ServiceConnection
    static MySQLContainer mysql = new MySQLContainer("mysql:8.4.13");

    @Autowired MockMvc mvc;
    @Autowired RegionRepository regions;
    @Autowired PlaceRepository places;
    @Autowired PlaceNoticeRepository notices;
    @Autowired SourceRecordRepository sources;
    @Autowired EntityManager entityManager;
    @Autowired JdbcTemplate jdbc;

    @Test
    @DisplayName("운영자가 등록한 장소 초안은 운영자 상세에서만 조회할 수 있다")
    void adminCreatesPrivateDraft() throws Exception {
        long regionId = regions.saveAndFlush(Region.builder().code("ADMIN-TEST").name("관리자 테스트 지역").build()).getId();
        var admin = login("admin@example.com", "12341234");

        var created = mvc.perform(post("/api/v1/admin/places").session(admin.session())
                        .header(admin.header(), admin.token()).contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"regionId":%d,"name":"관리자 등록 장소","address":"테스트 주소",
                                 "description":"검토 전 초안","latitude":37.5,"longitude":127.1}
                                """.formatted(regionId)))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.status").value("DRAFT")).andReturn();
        Number placeId = JsonPath.parse(created.getResponse().getContentAsString()).read("$.id");

        mvc.perform(get("/api/v1/admin/places").session(admin.session()))
                .andExpect(status().isOk()).andExpect(jsonPath("$[0].name").value("관리자 등록 장소"));
        mvc.perform(get("/api/v1/admin/places/{id}", placeId).session(admin.session()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.description").value("검토 전 초안"))
                .andExpect(jsonPath("$.activities").isEmpty()).andExpect(jsonPath("$.sources").isEmpty());
        mvc.perform(get("/api/v1/places").param("query", "관리자 등록 장소"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(0));
    }

    @Test
    @DisplayName("비로그인 사용자의 운영자 조회와 일반 회원의 운영자 조회·등록을 거절한다")
    void memberCannotUseAdminPlaces() throws Exception {
        var place = publishedPlace("운영자 조회 권한 테스트 장소");
        var guest = mvc.perform(get("/api/v1/admin/dashboard"));
        guest.andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/admin/places").param("status", "DRAFT")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/admin/places/{id}", place.getId())).andExpect(status().isUnauthorized());
        var csrf = mvc.perform(get("/api/v1/auth/csrf")).andReturn();
        var session = (MockHttpSession) csrf.getRequest().getSession(false);
        var json = JsonPath.parse(csrf.getResponse().getContentAsString());
        mvc.perform(post("/api/v1/auth/signup").session(session)
                        .header(json.<String>read("$.headerName"), json.<String>read("$.token"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"place-user@example.com","password":"password123","nickname":"회원"}
                                """))
                .andExpect(status().isCreated());
        var member = login("place-user@example.com", "password123");
        mvc.perform(get("/api/v1/admin/dashboard").session(member.session()))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/admin/places").param("status", "DRAFT").session(member.session()))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/admin/places/{id}", place.getId()).session(member.session()))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/v1/admin/places").session(member.session())
                        .header(member.header(), member.token()).contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"regionId":1,"name":"허용 안 됨","address":"주소",
                                 "latitude":37.5,"longitude":127.1}
                                """))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("운영자는 공지 초안에 새 출처를 기록하고 수정한 내용을 장소별 목록에서 확인한다")
    void adminCreatesAndUpdatesNoticeDraft() throws Exception {
        var place = publishedPlace("공지 관리 장소");
        var otherPlace = publishedPlace("다른 장소");
        var existingSource = sourceFor(place);
        var admin = login("admin@example.com", "12341234");
        String path = noticePath(place.getId());

        var created = mvc.perform(post(path).session(admin.session()).header(admin.header(), admin.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":"휴무 안내","content":"초안 내용","validFrom":"2026-11-23T09:00:00",
                                 "source":{"url":"https://example.com/holiday","verifiedAt":"2026-11-22T00:00:00"}}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.publishStatus").value("DRAFT"))
                .andExpect(jsonPath("$.source.url").value("https://example.com/holiday"))
                .andExpect(jsonPath("$.source.verifiedAt").value("2026-11-22T00:00:00"))
                .andReturn();
        var createdNotice = JsonPath.parse(created.getResponse().getContentAsString());
        Number noticeId = createdNotice.read("$.placeNoticeId");
        Number sourceId = createdNotice.read("$.source.sourceRecordId");

        entityManager.flush();
        entityManager.clear();
        var savedSource = sources.findById(sourceId.longValue()).orElseThrow();
        assertThat(savedSource.getPlace().getId()).isEqualTo(place.getId());
        assertThat(savedSource.getVerifierId()).isNotNull();
        mvc.perform(get("/api/v1/places/{id}", place.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.notices").isEmpty())
                .andExpect(jsonPath("$.sources.length()").value(1))
                .andExpect(jsonPath("$.sources[0].sourceRecordId").value(existingSource.getId().intValue()));

        mvc.perform(patch(path + "/" + noticeId).session(admin.session()).header(admin.header(), admin.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(noticeBody("수정한 휴무 안내", existingSource.getId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("수정한 휴무 안내"))
                .andExpect(jsonPath("$.publishStatus").value("DRAFT"))
                .andExpect(jsonPath("$.validFrom").doesNotExist())
                .andExpect(jsonPath("$.source.sourceRecordId").value(existingSource.getId().intValue()));
        entityManager.flush();
        entityManager.clear();

        mvc.perform(get(path).session(admin.session()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(20))
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.items[0].placeNoticeId").value(noticeId.intValue()))
                .andExpect(jsonPath("$.items[0].title").value("수정한 휴무 안내"))
                .andExpect(jsonPath("$.items[0].source.url").value("https://example.com/place"));
        mvc.perform(get(noticePath(otherPlace.getId())).session(admin.session()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.items").isEmpty());
    }

    @Test
    @DisplayName("운영자는 장소의 공지를 ID 내림차순으로 조회하고 페이지를 이동할 수 있다")
    void adminPaginatesNoticesInDescendingOrder() throws Exception {
        var place = publishedPlace("공지 목록 장소");
        var source = sourceFor(place);
        var first = saveNotice(place, source, "먼저 저장한 공지", PlacePublishStatus.DRAFT);
        var last = saveNotice(place, source, "나중에 저장한 공지", PlacePublishStatus.PUBLISHED);
        var admin = login("admin@example.com", "12341234");
        String path = noticePath(place.getId());

        mvc.perform(get(path).session(admin.session()).param("size", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].placeNoticeId").value(last.getId().intValue()))
                .andExpect(jsonPath("$.items[0].publishStatus").value("PUBLISHED"))
                .andExpect(jsonPath("$.totalElements").value(2));
        mvc.perform(get(path).session(admin.session()).param("page", "1").param("size", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].placeNoticeId").value(first.getId().intValue()));
        mvc.perform(get(path).session(admin.session()).param("size", "51"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors[0].field").value("size"));
        mvc.perform(get(noticePath(Long.MAX_VALUE)).session(admin.session()))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("PLACE_NOT_FOUND"));
    }

    @Test
    @DisplayName("다른 장소의 출처를 등록·수정에 쓸 수 없고 다른 장소의 공지를 수정할 수 없다")
    void rejectsNoticeAndSourceFromOtherPlace() throws Exception {
        var place = publishedPlace("공지 대상 장소");
        var otherPlace = publishedPlace("출처가 속한 장소");
        var source = sourceFor(place);
        var otherSource = sourceFor(otherPlace);
        var draft = saveNotice(place, source, "원래 제목", PlacePublishStatus.DRAFT);
        var admin = login("admin@example.com", "12341234");
        String path = noticePath(place.getId());
        String body = noticeBody("변경할 제목", otherSource.getId());

        mvc.perform(post(path).session(admin.session()).header(admin.header(), admin.token())
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("PLACE_NOTICE_INVALID_SOURCE"));
        mvc.perform(patch(path + "/" + draft.getId()).session(admin.session()).header(admin.header(), admin.token())
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("PLACE_NOTICE_INVALID_SOURCE"));
        mvc.perform(patch(noticePath(otherPlace.getId()) + "/" + draft.getId())
                        .session(admin.session()).header(admin.header(), admin.token())
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("PLACE_NOTICE_NOT_FOUND"));
        mvc.perform(get(path).session(admin.session()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.items[0].title").value("원래 제목"));
    }

    @Test
    @DisplayName("초안 등록은 종료 시각이 시작 시각보다 빠르면 거절한다")
    void rejectsInvalidValidityPeriod() throws Exception {
        var place = publishedPlace("유효기간 검사 장소");
        var source = sourceFor(place);
        var admin = login("admin@example.com", "12341234");
        String path = noticePath(place.getId());

        mvc.perform(post(path).session(admin.session()).header(admin.header(), admin.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":"유효기간이 잘못된 공지","content":"본문","sourceRecordId":%d,
                                 "validFrom":"2026-11-24T00:00:00","validUntil":"2026-11-23T00:00:00"}
                                """.formatted(source.getId())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.fieldErrors[0].field").value("validityPeriodValid"));
    }

    @Test
    @DisplayName("초안의 최초 공개에서만 이벤트를 만들고 수정과 숨김·재공개 뒤에도 같은 이벤트를 유지한다")
    void firstPublicationCreatesOneEventAcrossTheNoticeLifecycle() throws Exception {
        var place = publishedPlace("공개 흐름 장소");
        var source = sourceFor(place);
        var draft = saveNotice(place, source, "공개 전 제목", PlacePublishStatus.DRAFT);
        var admin = login("admin@example.com", "12341234");
        String path = noticePath(place.getId()) + "/" + draft.getId();
        assertThat(eventIds(draft.getId())).isEmpty();

        mvc.perform(patch(path).param("publish", "true").session(admin.session())
                        .header(admin.header(), admin.token()).contentType(MediaType.APPLICATION_JSON)
                        .content(noticeBody("처음 공개한 공지", source.getId())))
                .andExpect(status().isOk()).andExpect(jsonPath("$.publishStatus").value("PUBLISHED"));
        entityManager.flush();
        entityManager.clear();
        var event = jdbc.queryForMap("""
                SELECT place_change_event_id, task_status, processed_at FROM place_change_event WHERE place_notice_id = ?
                """, draft.getId());
        long eventId = ((Number) event.get("place_change_event_id")).longValue();
        assertThat(event).containsEntry("task_status", "PENDING").containsEntry("processed_at", null);
        mvc.perform(get("/api/v1/places/{id}", place.getId()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.notices[0].title").value("처음 공개한 공지"));

        mvc.perform(patch(path).param("publish", "true").session(admin.session())
                        .header(admin.header(), admin.token()).contentType(MediaType.APPLICATION_JSON)
                        .content(noticeBody("다시 도착한 공개 요청", source.getId())))
                .andExpect(status().isOk()).andExpect(jsonPath("$.title").value("처음 공개한 공지"));
        mvc.perform(patch(path).session(admin.session()).header(admin.header(), admin.token())
                        .contentType(MediaType.APPLICATION_JSON).content(noticeBody("수정한 공개 공지", source.getId())))
                .andExpect(status().isOk()).andExpect(jsonPath("$.title").value("수정한 공개 공지"))
                .andExpect(jsonPath("$.publishStatus").value("PUBLISHED"));
        jdbc.update("UPDATE place_change_event SET task_status = 'COMPLETED', processed_at = ? WHERE place_change_event_id = ?",
                LocalDateTime.of(2026, 11, 23, 12, 0), eventId);

        mvc.perform(visibilityRequest(path, false, admin))
                .andExpect(status().isOk()).andExpect(jsonPath("$.publishStatus").value("HIDDEN"));
        mvc.perform(get("/api/v1/places/{id}", place.getId()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.notices").isEmpty());
        mvc.perform(visibilityRequest(path, true, admin))
                .andExpect(status().isOk()).andExpect(jsonPath("$.publishStatus").value("PUBLISHED"));
        mvc.perform(get("/api/v1/places/{id}", place.getId()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.notices[0].title").value("수정한 공개 공지"));
        assertThat(eventIds(draft.getId())).containsExactly(eventId);
        assertThat(jdbc.queryForObject("SELECT task_status FROM place_change_event WHERE place_change_event_id = ?",
                String.class, eventId)).isEqualTo("COMPLETED");
    }

    @Test
    @DisplayName("공지 공개 요청 두 건이 겹쳐도 둘 다 성공하고 이벤트는 한 건만 남는다")
    void concurrentPublicationKeepsOneEvent() throws Exception {
        var place = publishedPlace("동시 공개 요청 장소");
        var draft = saveNotice(place, sourceFor(place), "동시 공개 공지", PlacePublishStatus.DRAFT);
        var firstAdmin = login("admin@example.com", "12341234");
        var secondAdmin = login("admin@example.com", "12341234");
        String path = noticePath(place.getId()) + "/" + draft.getId();
        TestTransaction.flagForCommit();
        TestTransaction.end();

        runTogether(
                () -> mvc.perform(visibilityRequest(path, true, firstAdmin)).andExpect(status().isOk()).andReturn(),
                () -> mvc.perform(visibilityRequest(path, true, secondAdmin)).andExpect(status().isOk()).andReturn());
        assertThat(eventIds(draft.getId())).hasSize(1);
        assertThat(notices.findById(draft.getId()).orElseThrow().getPublishStatus()).isEqualTo(PlacePublishStatus.PUBLISHED);
    }

    @Test
    @DisplayName("작성 즉시 공개 요청이 겹쳐도 같은 요청 ID라면 공지·출처·이벤트를 한 건씩 저장한다")
    void directPublicationReusesTheNoticeForTheSameRequestId() throws Exception {
        var place = publishedPlace("작성 즉시 공개 장소");
        var firstAdmin = login("admin@example.com", "12341234");
        var secondAdmin = login("admin@example.com", "12341234");
        String path = noticePath(place.getId());
        String requestId = UUID.randomUUID().toString();
        TestTransaction.flagForCommit();
        TestTransaction.end();

        mvc.perform(directPublicationRequest(path, firstAdmin))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("PLACE_NOTICE_PUBLICATION_REQUEST_REQUIRED"));
        var replies = runTogether(
                () -> mvc.perform(directPublicationRequest(path, firstAdmin).param("publicationRequestId", requestId))
                        .andExpect(status().isCreated()).andExpect(jsonPath("$.publishStatus").value("PUBLISHED")).andReturn(),
                () -> mvc.perform(directPublicationRequest(path, secondAdmin).param("publicationRequestId", requestId))
                        .andExpect(status().isCreated()).andExpect(jsonPath("$.publishStatus").value("PUBLISHED")).andReturn());
        long noticeId = noticeId(replies.getFirst());
        assertThat(noticeId(replies.getLast())).isEqualTo(noticeId);
        assertThat(eventIds(noticeId)).hasSize(1);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM source_record WHERE place_id = ?", Long.class, place.getId()))
                .isEqualTo(1L);
        mvc.perform(get(path).session(firstAdmin.session()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(1));
        mvc.perform(get("/api/v1/places/{id}", place.getId()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.notices[0].placeNoticeId").value(noticeId))
                .andExpect(jsonPath("$.sources[0].url").value("https://example.com/direct"));
    }

    @Test
    @DisplayName("이벤트를 DB에 저장하지 못하면 공지 수정·공개와 새 출처 저장을 함께 취소한다")
    void failedEventInsertRollsBackNoticeAndSource() throws Exception {
        var place = publishedPlace("공개 취소 검사 장소");
        var draft = saveNotice(place, sourceFor(place), "저장된 초안", PlacePublishStatus.DRAFT);
        jdbc.update("INSERT INTO place_change_event (place_notice_id) VALUES (?)", draft.getId());
        var admin = login("admin@example.com", "12341234");
        String path = noticePath(place.getId()) + "/" + draft.getId();
        TestTransaction.flagForCommit();
        TestTransaction.end();

        mvc.perform(patch(path).param("publish", "true").session(admin.session())
                        .header(admin.header(), admin.token()).contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":"공개 실패 후 저장되면 안 되는 제목","content":"본문",
                                 "source":{"url":"https://example.com/rollback","verifiedAt":"2026-11-22T00:00:00"}}
                                """))
                .andExpect(status().isInternalServerError());
        var saved = notices.findById(draft.getId()).orElseThrow();
        assertThat(saved.getTitle()).isEqualTo("저장된 초안");
        assertThat(saved.getPublishStatus()).isEqualTo(PlacePublishStatus.DRAFT);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM source_record WHERE place_id = ?", Long.class, place.getId()))
                .isEqualTo(1L);
        assertThat(eventIds(draft.getId())).hasSize(1);
    }

    @Test
    @DisplayName("확인일이 없는 출처의 초안 공개와 초안 숨김 요청을 거절한다")
    void rejectsInvalidVisibilityChanges() throws Exception {
        var place = publishedPlace("미확인 출처 검사 장소");
        var source = sources.saveAndFlush(SourceRecord.builder().place(place).url("https://example.com/unverified").build());
        var draft = saveNotice(place, source, "미확인 공지", PlacePublishStatus.DRAFT);
        var admin = login("admin@example.com", "12341234");
        String path = noticePath(place.getId()) + "/" + draft.getId();
        mvc.perform(visibilityRequest(path, true, admin)).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("PLACE_NOTICE_UNVERIFIED_SOURCE"));
        mvc.perform(visibilityRequest(path, false, admin)).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("PLACE_NOTICE_DRAFT_CANNOT_BE_HIDDEN"));
        assertThat(eventIds(draft.getId())).isEmpty();
    }

    @Test
    @DisplayName("초안 등록에는 기존 출처나 새 HTTP 출처 중 하나가 필요하다")
    void requiresExactlyOneValidSource() throws Exception {
        var place = publishedPlace("출처 입력 검사 장소");
        var source = sourceFor(place);
        var admin = login("admin@example.com", "12341234");
        String path = noticePath(place.getId());

        mvc.perform(post(path).session(admin.session()).header(admin.header(), admin.token())
                        .contentType(MediaType.APPLICATION_JSON).content("""
                                {"title":"출처 없는 공지","content":"본문"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors[0].field").value("sourceSelectionValid"));
        mvc.perform(post(path).session(admin.session()).header(admin.header(), admin.token())
                        .contentType(MediaType.APPLICATION_JSON).content("""
                                {"title":"출처 중복 공지","content":"본문","sourceRecordId":%d,
                                 "source":{"url":"https://example.com/notice","verifiedAt":"2026-11-22T00:00:00"}}
                                """.formatted(source.getId())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors[0].field").value("sourceSelectionValid"));
        mvc.perform(post(path).session(admin.session()).header(admin.header(), admin.token())
                        .contentType(MediaType.APPLICATION_JSON).content("""
                                {"title":"잘못된 출처 공지","content":"본문",
                                 "source":{"url":"file:///notice","verifiedAt":"2026-11-22T00:00:00"}}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors[0].field").value("source.url"));
    }

    @Test
    @DisplayName("일반 회원은 운영자 공지 목록 조회, 초안 등록과 수정을 할 수 없다")
    void memberCannotManageNotices() throws Exception {
        var place = publishedPlace("권한 검사 장소");
        var source = sourceFor(place);
        var draft = saveNotice(place, source, "운영자 공지", PlacePublishStatus.DRAFT);
        String path = noticePath(place.getId());
        String body = noticeBody("권한 없는 수정", source.getId());
        var csrf = mvc.perform(get("/api/v1/auth/csrf")).andReturn();
        var session = (MockHttpSession) csrf.getRequest().getSession(false);
        var json = JsonPath.parse(csrf.getResponse().getContentAsString());
        mvc.perform(post("/api/v1/auth/signup").session(session)
                        .header(json.<String>read("$.headerName"), json.<String>read("$.token"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"notice-user@example.com","password":"password123","nickname":"공지 회원"}
                                """))
                .andExpect(status().isCreated());
        var member = login("notice-user@example.com", "password123");

        mvc.perform(get(path)).andExpect(status().isUnauthorized());
        mvc.perform(get(path).session(member.session())).andExpect(status().isForbidden());
        mvc.perform(post(path).session(member.session()).header(member.header(), member.token())
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isForbidden());
        mvc.perform(patch(path + "/" + draft.getId()).session(member.session()).header(member.header(), member.token())
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isForbidden());
        mvc.perform(visibilityRequest(path + "/" + draft.getId(), true, member)).andExpect(status().isForbidden());
    }

    private MockHttpServletRequestBuilder visibilityRequest(String noticePath, boolean visible, Login login) {
        return patch(noticePath + "/visibility").session(login.session()).header(login.header(), login.token())
                .contentType(MediaType.APPLICATION_JSON).content("{\"visible\":" + visible + "}");
    }

    private MockHttpServletRequestBuilder directPublicationRequest(String path, Login login) {
        return post(path).param("publish", "true").session(login.session()).header(login.header(), login.token())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"title":"즉시 공개 공지","content":"본문",
                         "source":{"url":"https://example.com/direct","verifiedAt":"2026-11-22T00:00:00"}}
                        """);
    }

    private long noticeId(MvcResult response) throws Exception {
        Number id = JsonPath.read(response.getResponse().getContentAsString(), "$.placeNoticeId");
        return id.longValue();
    }

    private <T> List<T> runTogether(Callable<T> firstRequest, Callable<T> secondRequest) throws Exception {
        var start = new CyclicBarrier(2);
        try (var requests = Executors.newFixedThreadPool(2)) {
            var first = requests.submit(() -> {
                start.await(5, TimeUnit.SECONDS);
                return firstRequest.call();
            });
            var second = requests.submit(() -> {
                start.await(5, TimeUnit.SECONDS);
                return secondRequest.call();
            });
            return List.of(first.get(10, TimeUnit.SECONDS), second.get(10, TimeUnit.SECONDS));
        }
    }

    private List<Long> eventIds(long noticeId) {
        return jdbc.queryForList("SELECT place_change_event_id FROM place_change_event WHERE place_notice_id = ?", Long.class, noticeId);
    }

    private Place publishedPlace(String name) {
        var region = regions.save(Region.builder().name("공지 테스트 지역").build());
        return places.saveAndFlush(Place.builder().region(region).name(name)
                .publishStatus(PlacePublishStatus.PUBLISHED).build());
    }

    private SourceRecord sourceFor(Place place) {
        return sources.saveAndFlush(SourceRecord.builder().place(place).type("OFFICIAL")
                .url("https://example.com/place").verifiedFields("address")
                .verifiedAt(LocalDateTime.of(2026, 11, 21, 0, 0)).build());
    }

    private PlaceNotice saveNotice(Place place, SourceRecord source, String title, PlacePublishStatus status) {
        return notices.saveAndFlush(PlaceNotice.builder().place(place).sourceRecord(source)
                .title(title).content("공지 본문").publishStatus(status).build());
    }

    private String noticePath(long placeId) {
        return "/api/v1/admin/places/" + placeId + "/notices";
    }

    private String noticeBody(String title, long sourceId) {
        return """
                {"title":"%s","content":"공지 본문","sourceRecordId":%d}
                """.formatted(title, sourceId);
    }

    private Login login(String email, String password) throws Exception {
        var csrf = mvc.perform(get("/api/v1/auth/csrf")).andReturn();
        var session = (MockHttpSession) csrf.getRequest().getSession(false);
        var json = JsonPath.parse(csrf.getResponse().getContentAsString());
        String header = json.read("$.headerName");
        String token = json.read("$.token");
        mvc.perform(post("/api/v1/auth/login").session(session).header(header, token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s","password":"%s"}
                                """.formatted(email, password)))
                .andExpect(status().isOk());
        return new Login(session, header, token);
    }

    private record Login(MockHttpSession session, String header, String token) {}
}
