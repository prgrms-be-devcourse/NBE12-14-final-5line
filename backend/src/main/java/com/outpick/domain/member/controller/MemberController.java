package com.outpick.domain.member.controller;

import com.outpick.domain.member.dto.MemberResponse;
import com.outpick.domain.member.dto.WithdrawalRequest;
import com.outpick.domain.member.service.MemberService;
import com.outpick.domain.member.security.MemberSessionPrincipal;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.Map;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/members")
public class MemberController {
    private final MemberService memberService;

    public MemberController(MemberService memberService) {
        this.memberService = memberService;
    }

    @GetMapping("/me")
    public MemberResponse me(Authentication authentication) {
        MemberSessionPrincipal principal = (MemberSessionPrincipal) authentication.getPrincipal();
        return MemberResponse.from(memberService.findActiveMember(principal.memberId()));
    }

    @DeleteMapping("/me")
    public Map<String, String> withdraw(@Valid @RequestBody WithdrawalRequest request,
                                                    Authentication authentication, HttpServletRequest servletRequest) {
        MemberSessionPrincipal principal = (MemberSessionPrincipal) authentication.getPrincipal();
        memberService.withdraw(principal.memberId(), request.password());
        var session = servletRequest.getSession(false);
        if (session != null) {
            session.invalidate();
        }
        SecurityContextHolder.clearContext();
        return Map.of("message", "회원 탈퇴가 완료되었습니다.");
    }
}
