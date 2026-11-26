package com.outpick.domain.member.controller;

import com.outpick.domain.member.dto.MemberResponse;
import com.outpick.domain.member.dto.WithdrawalRequest;
import com.outpick.domain.member.service.MemberService;
import com.outpick.domain.member.security.MemberPrincipal;
import com.outpick.domain.member.security.AuthTokenService;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import java.util.Map;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/members")
public class MemberController {
    private final MemberService memberService;
    private final AuthTokenService authTokens;

    public MemberController(MemberService memberService, AuthTokenService authTokens) {
        this.memberService = memberService;
        this.authTokens = authTokens;
    }

    @GetMapping("/me")
    public MemberResponse me(Authentication authentication) {
        MemberPrincipal principal = (MemberPrincipal) authentication.getPrincipal();
        return MemberResponse.from(memberService.findActiveMember(principal.memberId()));
    }

    @DeleteMapping("/me")
    public Map<String, String> withdraw(@Valid @RequestBody WithdrawalRequest request,
                                                    Authentication authentication, HttpServletResponse response) {
        MemberPrincipal principal = (MemberPrincipal) authentication.getPrincipal();
        memberService.withdraw(principal.memberId(), request.password());
        authTokens.clearCookies(response);
        return Map.of("message", "회원 탈퇴가 완료되었습니다.");
    }
}
