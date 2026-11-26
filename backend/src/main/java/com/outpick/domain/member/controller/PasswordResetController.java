package com.outpick.domain.member.controller;

import com.outpick.domain.member.dto.PasswordResetConfirmRequest;
import com.outpick.domain.member.dto.PasswordResetRequest;
import com.outpick.domain.member.dto.PasswordResetValidateRequest;
import com.outpick.domain.member.exception.MemberErrorCode;
import com.outpick.domain.member.service.PasswordResetMailer;
import com.outpick.domain.member.service.PasswordResetService;
import com.outpick.global.error.BusinessException;
import jakarta.validation.Valid;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mail.MailException;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/auth/password-reset")
public class PasswordResetController {
    private static final Logger LOG = LoggerFactory.getLogger(PasswordResetController.class);
    private static final String REQUEST_MESSAGE = "해당 이메일로 재설정할 수 있는 계정이 있다면 안내 메일이 발송됩니다.";
    private final PasswordResetService service;
    private final PasswordResetMailer mailer;

    public PasswordResetController(PasswordResetService service, PasswordResetMailer mailer) {
        this.service = service;
        this.mailer = mailer;
    }

    @PostMapping("/request")
    public ResponseEntity<Map<String, String>> request(@Valid @RequestBody PasswordResetRequest request) {
        service.request(request.email()).ifPresent(reset -> {
            try {
                mailer.send(reset);
            } catch (MailException error) {
                LOG.error("Password reset mail delivery failed: {}", error.getClass().getSimpleName());
            }
        });
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(Map.of("message", REQUEST_MESSAGE));
    }

    @PostMapping("/validate")
    public ResponseEntity<Void> validate(@Valid @RequestBody PasswordResetValidateRequest request) {
        if (!service.isValid(request.token())) {
            throw new BusinessException(MemberErrorCode.RESET_LINK_INVALID.getErrorCode());
        }
        return ResponseEntity.noContent().header("Cache-Control", "no-store").build();
    }

    @PostMapping("/confirm")
    public Map<String, String> confirm(@Valid @RequestBody PasswordResetConfirmRequest request) {
        service.confirm(request);
        return Map.of("message", "비밀번호가 변경되었습니다. 새 비밀번호로 로그인해 주세요.");
    }
}
