package com.outpick.domain.member.controller;

import com.outpick.domain.member.dto.LoginRequest;
import com.outpick.domain.member.dto.MemberResponse;
import com.outpick.domain.member.dto.SignupRequest;
import com.outpick.domain.member.dto.SignupResponse;
import com.outpick.domain.member.entity.Member;
import com.outpick.domain.member.service.MemberService;
import com.outpick.domain.member.service.LoginAttemptLimiter;
import com.outpick.domain.member.exception.MemberErrorCode;
import com.outpick.global.error.BusinessException;
import com.outpick.domain.member.security.AuthTokenService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/auth")
public class AuthController {

    private final MemberService memberService;
    private final AuthTokenService authTokens;
    private final LoginAttemptLimiter loginAttemptLimiter;

    public AuthController(MemberService memberService, AuthTokenService authTokens,
                          LoginAttemptLimiter loginAttemptLimiter) {
        this.memberService = memberService;
        this.authTokens = authTokens;
        this.loginAttemptLimiter = loginAttemptLimiter;
    }

    @GetMapping("/csrf")
    public Map<String, String> csrf(CsrfToken csrfToken, HttpServletResponse response) {
        response.setHeader("Cache-Control", "no-store");
        return Map.of("token", csrfToken.getToken(), "headerName", csrfToken.getHeaderName());
    }

    @PostMapping("/signup")
    public ResponseEntity<SignupResponse> signup(@Valid @RequestBody SignupRequest request) {
        SignupResponse response = memberService.register(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PostMapping("/login")
    public MemberResponse login(@Valid @RequestBody LoginRequest login,
                                HttpServletRequest request, HttpServletResponse response) {
        response.setHeader("Cache-Control", "no-store");
        String attemptKey = loginAttemptLimiter.key(login.email(), request.getRemoteAddr());
        loginAttemptLimiter.requireAllowed(attemptKey);
        var member = authenticate(login, attemptKey);
        loginAttemptLimiter.clear(attemptKey);
        authTokens.issue(member, response);
        return MemberResponse.from(member);
    }

    @PostMapping("/refresh")
    public ResponseEntity<Void> refresh(HttpServletRequest request, HttpServletResponse response) {
        response.setHeader("Cache-Control", "no-store");
        if (!authTokens.rotate(AuthTokenService.cookie(request, AuthTokenService.REFRESH_COOKIE), response)) {
            authTokens.clearCookies(response);
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(HttpServletRequest request, HttpServletResponse response) {
        authTokens.revoke(AuthTokenService.cookie(request, AuthTokenService.REFRESH_COOKIE),
                AuthTokenService.cookie(request, AuthTokenService.ACCESS_COOKIE), response);
        return ResponseEntity.noContent().build();
    }

    private Member authenticate(LoginRequest login, String attemptKey) {
        try {
            return memberService.authenticate(login.email(), login.password());
        } catch (BusinessException error) {
            if (error.getErrorCode().code().equals(MemberErrorCode.INVALID_CREDENTIALS.name())) {
                loginAttemptLimiter.recordFailure(attemptKey);
            }
            throw error;
        }
    }
}
