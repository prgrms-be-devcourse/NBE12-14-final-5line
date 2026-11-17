package com.outpick.domain.member.controller;

import com.outpick.domain.member.dto.LoginRequest;
import com.outpick.domain.member.dto.MemberResponse;
import com.outpick.domain.member.dto.SignupRequest;
import com.outpick.domain.member.dto.SignupResponse;
import com.outpick.domain.member.service.MemberService;
import com.outpick.domain.member.security.MemberSessionPrincipal;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.SecurityContextRepository;
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
    private final SecurityContextRepository securityContextRepository;

    public AuthController(MemberService memberService, SecurityContextRepository securityContextRepository) {
        this.memberService = memberService;
        this.securityContextRepository = securityContextRepository;
    }

    @GetMapping("/csrf")
    public Map<String, String> csrf(CsrfToken csrfToken) {
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
        var member = memberService.authenticate(login.email(), login.password());
        if (request.getSession(false) != null) {
            request.changeSessionId();
        }
        var authentication = new UsernamePasswordAuthenticationToken(
                new MemberSessionPrincipal(member.getId(), member.getAuthVersion()), null,
                List.of(new SimpleGrantedAuthority("ROLE_USER")));
        var context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(authentication);
        SecurityContextHolder.setContext(context);
        securityContextRepository.saveContext(context, request, response);
        return MemberResponse.from(member);
    }
}
