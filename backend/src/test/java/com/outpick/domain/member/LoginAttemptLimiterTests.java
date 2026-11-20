package com.outpick.domain.member;

import com.outpick.domain.member.service.LoginAttemptLimiter;
import com.outpick.global.error.BusinessException;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LoginAttemptLimiterTests {
    @Test
    @DisplayName("로그인 실패 제한은 15분 뒤 풀리고 성공한 계정의 실패 횟수는 초기화된다")
    void limitExpiresAndSuccessfulLoginClearsFailures() {
        var clock = new AdjustableClock();
        var limiter = new LoginAttemptLimiter(clock, 5, 15);
        String key = limiter.key("USER@example.com", "127.0.0.1");
        String sameKey = limiter.key("user@example.com", "127.0.0.1");
        for (int i = 0; i < 5; i++) limiter.recordFailure(key);

        assertThatThrownBy(() -> limiter.requireAllowed(sameKey)).isInstanceOf(BusinessException.class);
        clock.advanceSeconds(15 * 60);
        assertThatCode(() -> limiter.requireAllowed(key)).doesNotThrowAnyException();
        limiter.recordFailure(key);
        limiter.clear(key);
        assertThatCode(() -> limiter.requireAllowed(key)).doesNotThrowAnyException();
    }

    private static final class AdjustableClock extends Clock {
        private Instant now = Instant.parse("2026-01-01T00:00:00Z");

        void advanceSeconds(long seconds) { now = now.plusSeconds(seconds); }
        @Override public ZoneId getZone() { return ZoneId.of("UTC"); }
        @Override public Clock withZone(ZoneId zone) { return this; }
        @Override public Instant instant() { return now; }
    }
}
