package com.outpick.domain.member.service;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.outpick.domain.member.exception.MemberErrorCode;
import com.outpick.global.error.BusinessException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Locale;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class LoginAttemptLimiter {
    private final Cache<String, Attempt> attempts;
    private final Clock clock;
    private final int maxFailures;
    private final Duration window;

    public LoginAttemptLimiter(Clock clock,
                               @Value("${app.login-limit.max-failures:5}") int maxFailures,
                               @Value("${app.login-limit.window-minutes:15}") int windowMinutes) {
        if (maxFailures < 1 || windowMinutes < 1) {
            throw new IllegalArgumentException("Login limit settings must be positive");
        }
        this.clock = clock;
        this.maxFailures = maxFailures;
        this.window = Duration.ofMinutes(windowMinutes);
        this.attempts = Caffeine.newBuilder().maximumSize(100_000)
                .expireAfterWrite(window.multipliedBy(2)).build();
    }

    public String key(String email, String remoteAddress) {
        String normalized = email.trim().toLowerCase(Locale.ROOT);
        String input = normalized + "\n" + remoteAddress;
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(input.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException error) {
            throw new IllegalStateException("SHA-256 is not available", error);
        }
    }

    public void requireAllowed(String key) {
        Attempt attempt = attempts.getIfPresent(key);
        if (attempt != null && attempt.failures() >= maxFailures && clock.instant().isBefore(attempt.expiresAt())) {
            throw new BusinessException(MemberErrorCode.LOGIN_RATE_LIMITED.getErrorCode());
        }
    }

    public void recordFailure(String key) {
        Instant now = clock.instant();
        attempts.asMap().compute(key, (ignored, current) -> {
            if (current == null || !now.isBefore(current.expiresAt())) {
                return new Attempt(1, now.plus(window));
            }
            return new Attempt(current.failures() + 1, current.expiresAt());
        });
    }

    public void clear(String key) {
        attempts.invalidate(key);
    }

    private record Attempt(int failures, Instant expiresAt) {
    }
}
