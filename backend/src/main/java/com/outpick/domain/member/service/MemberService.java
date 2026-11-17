package com.outpick.domain.member.service;

import com.outpick.domain.member.dto.SignupRequest;
import com.outpick.domain.member.dto.SignupResponse;
import com.outpick.domain.member.entity.Member;
import com.outpick.domain.member.exception.MemberErrorCode;
import com.outpick.domain.member.repository.MemberRepository;
import com.outpick.domain.member.repository.PasswordResetTokenRepository;
import com.outpick.global.error.BusinessException;
import java.util.Locale;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class MemberService {

    private final MemberRepository memberRepository;
    private final PasswordEncoder passwordEncoder;
    private final PasswordResetTokenRepository passwordResetTokenRepository;

    public MemberService(MemberRepository memberRepository, PasswordEncoder passwordEncoder,
                         PasswordResetTokenRepository passwordResetTokenRepository) {
        this.memberRepository = memberRepository;
        this.passwordEncoder = passwordEncoder;
        this.passwordResetTokenRepository = passwordResetTokenRepository;
    }

    @Transactional
    public SignupResponse register(SignupRequest request) {
        String email = request.email().trim().toLowerCase(Locale.ROOT);

        if (memberRepository.existsByEmail(email)) {
            throw new BusinessException(MemberErrorCode.EMAIL_ALREADY_EXISTS.getErrorCode());
        }

        String passwordHash = passwordEncoder.encode(request.password());
        Member member = memberRepository.save(
                new Member(email, request.nickname().trim(), passwordHash)
        );

        return new SignupResponse(member.getId(), member.getEmail(), member.getNickname());
    }

    @Transactional(readOnly = true)
    public Member authenticate(String rawEmail, String password) {
        String email = rawEmail.trim().toLowerCase(Locale.ROOT);
        Member member = memberRepository.findByEmail(email)
                .orElseThrow(() -> new BusinessException(MemberErrorCode.INVALID_CREDENTIALS.getErrorCode()));
        if (!"ACTIVE".equals(member.getStatus()) || !passwordEncoder.matches(password, member.getPasswordHash())) {
            throw new BusinessException(MemberErrorCode.INVALID_CREDENTIALS.getErrorCode());
        }
        return member;
    }

    @Transactional(readOnly = true)
    public Member findActiveMember(Long id) {
        Member member = memberRepository.findById(id)
                .orElseThrow(() -> new BusinessException(MemberErrorCode.MEMBER_NOT_FOUND.getErrorCode()));
        if (!"ACTIVE".equals(member.getStatus())) {
            throw new BusinessException(MemberErrorCode.MEMBER_NOT_FOUND.getErrorCode());
        }
        return member;
    }

    @Transactional
    public void revokeAllSessions(Long id) {
        findActiveMember(id).revokeSessions();
    }

    @Transactional
    public void withdraw(Long id, String currentPassword) {
        Member member = findActiveMember(id);
        if (!passwordEncoder.matches(currentPassword, member.getPasswordHash())) {
            throw new BusinessException(MemberErrorCode.WITHDRAWAL_PASSWORD_MISMATCH.getErrorCode());
        }
        passwordResetTokenRepository.deleteAllByMemberId(id);
        memberRepository.delete(member);
    }
}
