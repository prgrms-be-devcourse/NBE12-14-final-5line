package com.outpick.domain.place;

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

        mvc.perform(post("/api/v1/admin/places").session(admin.session())
                        .header(admin.header(), admin.token()).contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"regionId":%d,"name":"관리자 등록 장소","address":"테스트 주소",
                                 "description":"검토 전 초안","latitude":37.5,"longitude":127.1}
                                """.formatted(regionId)))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.status").value("DRAFT"));

        mvc.perform(get("/api/v1/admin/places").session(admin.session()))
                .andExpect(status().isOk()).andExpect(jsonPath("$[0].name").value("관리자 등록 장소"));
        mvc.perform(get("/api/v1/places").param("query", "관리자 등록 장소"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(0));
    }

    @Test
    @DisplayName("일반 회원은 운영 현황과 장소 등록에 접근할 수 없다")
    void memberCannotUseAdminPlaces() throws Exception {
        var guest = mvc.perform(get("/api/v1/admin/dashboard"));
        guest.andExpect(status().isUnauthorized());
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
        mvc.perform(post("/api/v1/admin/places").session(member.session())
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

        mvc.perform(get("/api/v1/admin/dashboard").session(admin.session()))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/admin/places").session(admin.session()))
                .andExpect(status().isForbidden());
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
