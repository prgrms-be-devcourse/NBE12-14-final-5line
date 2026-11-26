package com.outpick.domain.member;

import com.jayway.jsonpath.JsonPath;
import jakarta.servlet.http.Cookie;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.AbstractMockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** 쿠키 기반 인증·CSRF 요청을 MockMvc에서 브라우저와 같은 방식으로 재현한다. */
public class AuthTestClient {
    private final MockMvc mvc;
    private final Cookie csrfCookie;
    private final String csrfHeader;
    private final String csrfToken;
    private Cookie accessCookie;
    private Cookie refreshCookie;

    private AuthTestClient(MockMvc mvc, Cookie csrfCookie, String csrfHeader, String csrfToken) {
        this.mvc = mvc;
        this.csrfCookie = csrfCookie;
        this.csrfHeader = csrfHeader;
        this.csrfToken = csrfToken;
    }

    public static AuthTestClient create(MockMvc mvc) throws Exception {
        var result = mvc.perform(MockMvcRequestBuilders.get("/api/v1/auth/csrf"))
                .andExpect(status().isOk()).andReturn();
        String body = result.getResponse().getContentAsString();
        Cookie cookie = result.getResponse().getCookie("XSRF-TOKEN");
        if (cookie == null) throw new AssertionError("CSRF cookie was not issued");
        if (result.getResponse().getCookie("JSESSIONID") != null) throw new AssertionError("session cookie was issued");
        return new AuthTestClient(mvc, cookie, JsonPath.read(body, "$.headerName"), JsonPath.read(body, "$.token"));
    }

    public <T extends AbstractMockHttpServletRequestBuilder<T>> T csrf(T request) {
        request.cookie(csrfCookie).header(csrfHeader, csrfToken);
        if (accessCookie != null) request.cookie(accessCookie);
        if (refreshCookie != null) request.cookie(refreshCookie);
        return request;
    }

    public <T extends AbstractMockHttpServletRequestBuilder<T>> T auth(T request) {
        if (accessCookie != null) request.cookie(accessCookie);
        if (refreshCookie != null) request.cookie(refreshCookie);
        request.cookie(csrfCookie);
        return request;
    }

    public void signup(String email, String password, String nickname) throws Exception {
        mvc.perform(csrf(MockMvcRequestBuilders.post("/api/v1/auth/signup"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"password\":\"" + password
                                + "\",\"nickname\":\"" + nickname + "\"}"))
                .andExpect(status().isCreated());
    }

    public void login(String email, String password) throws Exception {
        var result = mvc.perform(csrf(MockMvcRequestBuilders.post("/api/v1/auth/login"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"password\":\"" + password + "\"}"))
                .andExpect(status().isOk()).andReturn();
        accessCookie = result.getResponse().getCookie("outpick_access");
        refreshCookie = result.getResponse().getCookie("outpick_refresh");
        if (accessCookie == null || refreshCookie == null) throw new AssertionError("auth cookies were not issued");
        if (result.getResponse().getCookie("JSESSIONID") != null) throw new AssertionError("session cookie was issued");
    }

    public Cookie accessCookie() { return accessCookie; }
    public Cookie refreshCookie() { return refreshCookie; }
    public Cookie csrfCookie() { return csrfCookie; }
    public String header() { return csrfHeader; }
    public String token() { return csrfToken; }
    public Cookie[] cookies() {
        return accessCookie == null ? new Cookie[]{csrfCookie} : new Cookie[]{csrfCookie, accessCookie, refreshCookie};
    }
    public <T extends AbstractMockHttpServletRequestBuilder<T>> T withCsrf(T request) { return csrf(request); }
}
