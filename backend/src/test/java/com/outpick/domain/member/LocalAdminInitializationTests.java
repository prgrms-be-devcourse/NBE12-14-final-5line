package com.outpick.domain.member;

import com.outpick.domain.member.config.LocalAdminInitializer;
import com.outpick.domain.member.entity.Member;
import com.outpick.domain.member.entity.MemberRole;
import com.outpick.domain.member.repository.MemberRepository;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.mysql.MySQLContainer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
@Transactional
class LocalAdminInitializationTests {

    private static final String ADMIN_EMAIL = "admin@example.com";
    private static final String PASSWORD = "12341234";

    @Container
    @ServiceConnection
    static MySQLContainer mysql = new MySQLContainer("mysql:8.4.13");

    @Autowired LocalAdminInitializer initializer;
    @Autowired MemberRepository members;
    @Autowired PasswordEncoder passwordEncoder;
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;

    @Test
    @DisplayName("로컬 운영자의 역할은 DB에 ADMIN으로 저장되며 로그인 세션에 운영자 권한이 부여된다")
    void startsWithAnAdminWhoCanLogIn() throws Exception {
        var admin = members.findByEmail(ADMIN_EMAIL).orElseThrow();
        assertThat(admin.getRole()).isEqualTo(MemberRole.ADMIN);
        assertThat(jdbc.queryForObject("SELECT role FROM `user` WHERE user_id = ?", String.class, admin.getId()))
                .isEqualTo("ADMIN");
        assertThat(admin.getPasswordHash()).isNotEqualTo(PASSWORD);
        assertThat(passwordEncoder.matches(PASSWORD, admin.getPasswordHash())).isTrue();

        var client = AuthTestClient.create(mvc);
        client.login(ADMIN_EMAIL, PASSWORD);
        mvc.perform(client.auth(get("/api/v1/admin/dashboard")))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("운영자 초기화를 다시 실행해도 기존 운영자와 비밀번호가 유지된다")
    void repeatedInitializationKeepsTheExistingAdmin() {
        var admin = members.findByEmail(ADMIN_EMAIL).orElseThrow();

        initializer.run(new DefaultApplicationArguments());

        assertThat(members.findAll()).singleElement().satisfies(existing -> {
            assertThat(existing.getId()).isEqualTo(admin.getId());
            assertThat(existing.getPasswordHash()).isEqualTo(admin.getPasswordHash());
        });
    }

    @Test
    @DisplayName("다른 이메일의 운영자가 있으면 로컬 운영자를 추가하지 않는다")
    void skipsCreationWhenAnotherAdminExists() {
        members.deleteAll();
        members.save(Member.createAdmin("other-admin@example.com", "기존 운영자", passwordEncoder.encode(PASSWORD)));

        initializer.run(new DefaultApplicationArguments());

        assertThat(members.findAll()).singleElement()
                .extracting(Member::getEmail).isEqualTo("other-admin@example.com");
    }

    @Test
    @DisplayName("초기화 이메일을 사용 중인 일반 회원의 역할과 비밀번호를 보존한다")
    void preservesMemberWithTheSameEmail() {
        members.deleteAll();
        members.flush();
        var member = members.save(new Member(ADMIN_EMAIL, "일반 회원", passwordEncoder.encode("member-password")));

        initializer.run(new DefaultApplicationArguments());

        assertThat(members.findAll()).singleElement().satisfies(existing -> {
            assertThat(existing.getId()).isEqualTo(member.getId());
            assertThat(existing.getRole()).isEqualTo(MemberRole.USER);
            assertThat(passwordEncoder.matches("member-password", existing.getPasswordHash())).isTrue();
        });
    }
}
