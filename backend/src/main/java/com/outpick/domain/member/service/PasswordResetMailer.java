package com.outpick.domain.member.service;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;

@Component
public class PasswordResetMailer {
    private final JavaMailSender sender;
    private final String from;
    private final String frontendBaseUrl;

    public PasswordResetMailer(JavaMailSender sender,
                               @Value("${app.password-reset.from}") String from,
                               @Value("${app.password-reset.frontend-base-url}") String frontendBaseUrl) {
        this.sender = sender;
        this.from = from;
        this.frontendBaseUrl = frontendBaseUrl;
    }

    public void send(PasswordResetService.ResetMail reset) {
        // Fragment는 웹 서버로 전송되지 않아 토큰이 접속 로그의 URL에 남지 않습니다.
        String link = frontendBaseUrl.replaceAll("/+$", "") + "/reset-password/#token="
                + URLEncoder.encode(reset.token(), StandardCharsets.UTF_8);
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(from);
        message.setTo(reset.email());
        message.setSubject("[OUTPICK] 비밀번호 재설정");
        message.setText("아래 링크에서 30분 이내에 비밀번호를 재설정해 주세요.\n\n" + link
                + "\n\n본인이 요청하지 않았다면 이 메일을 무시해 주세요.");
        sender.send(message);
    }
}
