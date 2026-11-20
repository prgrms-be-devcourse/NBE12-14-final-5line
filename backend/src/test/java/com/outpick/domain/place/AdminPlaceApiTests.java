package com.outpick.domain.place;

import com.outpick.domain.member.AuthTestClient;
import com.jayway.jsonpath.JsonPath;
import com.outpick.domain.place.entity.Region;
import com.outpick.domain.place.repository.RegionRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.mysql.MySQLContainer;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = "spring.jpa.hibernate.ddl-auto=validate")
@AutoConfigureMockMvc
@Testcontainers
@Transactional
class AdminPlaceApiTests {
    @Container @ServiceConnection
    static MySQLContainer mysql = new MySQLContainer("mysql:8.4.13");

    @Autowired MockMvc mvc;
    @Autowired RegionRepository regions;
    @Autowired JdbcTemplate jdbc;
    @Autowired EntityManager entityManager;

    @Test
    @DisplayName("운영자가 추가한 장소는 초안으로 저장되고 공개 검색에는 보이지 않는다")
    void adminCreatesPrivateDraft() throws Exception {
        long regionId = regions.saveAndFlush(Region.builder().code("ADMIN-TEST").name("관리자 테스트 지역").build()).getId();
        var admin = login("admin@example.com", "12341234");

        mvc.perform(post("/api/v1/admin/places").cookie(admin.cookies())
                        .header(admin.header(), admin.token()).contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"regionId":%d,"name":"관리자 등록 장소","address":"테스트 주소",
                                 "description":"검토 전 초안","latitude":37.5,"longitude":127.1}
                                """.formatted(regionId)))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.status").value("DRAFT"));

        mvc.perform(get("/api/v1/admin/places").cookie(admin.cookies()))
                .andExpect(status().isOk()).andExpect(jsonPath("$[0].name").value("관리자 등록 장소"));
        mvc.perform(get("/api/v1/places").param("query", "관리자 등록 장소"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(0));
    }

    @Test
    @DisplayName("일반 회원은 운영 현황과 장소 등록에 접근할 수 없다")
    void memberCannotUseAdminPlaces() throws Exception {
        var guest = mvc.perform(get("/api/v1/admin/dashboard"));
        guest.andExpect(status().isUnauthorized());
        AuthTestClient.create(mvc).signup("place-user@example.com", "password123", "회원");
        var member = login("place-user@example.com", "password123");
        mvc.perform(get("/api/v1/admin/dashboard").cookie(member.cookies()))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/v1/admin/places").cookie(member.cookies())
                        .header(member.header(), member.token()).contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"regionId":1,"name":"허용 안 됨","address":"주소",
                                 "latitude":37.5,"longitude":127.1}
                                """))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("로그인 후 운영자 역할을 잃으면 기존 세션으로 운영 데이터를 조회할 수 없다")
    void currentRoleIsCheckedDespiteCachedSessionAuthority() throws Exception {
        var admin = login("admin@example.com", "12341234");
        jdbc.update("UPDATE `user` SET role = 'USER' WHERE email = ?", "admin@example.com");
        entityManager.clear();

        mvc.perform(get("/api/v1/admin/dashboard").cookie(admin.cookies()))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/admin/places").cookie(admin.cookies()))
                .andExpect(status().isForbidden());
    }

    private AuthTestClient login(String email, String password) throws Exception {
        var client = AuthTestClient.create(mvc);
        client.login(email, password);
        return client;
    }
}
