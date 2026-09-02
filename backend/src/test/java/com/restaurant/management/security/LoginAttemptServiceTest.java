package com.restaurant.management.security;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;

class LoginAttemptServiceTest {

    @Test
    void blocksAfterConfiguredFailuresAndResetClearsBlock() {
        MutableClock clock = new MutableClock(Instant.parse("2026-07-25T12:00:00Z"));
        LoginAttemptService service = new LoginAttemptService(3, Duration.ofMinutes(15), clock);

        assertThat(service.registerFailure("User@Example.com", "127.0.0.1")).isFalse();
        assertThat(service.registerFailure("user@example.com", "127.0.0.1")).isFalse();
        assertThat(service.registerFailure("user@example.com", "127.0.0.1")).isTrue();
        assertThat(service.isBlocked("USER@example.com", "127.0.0.1")).isTrue();

        service.reset("user@example.com", "127.0.0.1");

        assertThat(service.isBlocked("user@example.com", "127.0.0.1")).isFalse();
    }

    @Test
    void expiredWindowAllowsNewAttempt() {
        MutableClock clock = new MutableClock(Instant.parse("2026-07-25T12:00:00Z"));
        LoginAttemptService service = new LoginAttemptService(1, Duration.ofMinutes(15), clock);

        assertThat(service.registerFailure("user@example.com", "127.0.0.1")).isTrue();
        clock.advance(Duration.ofMinutes(16));

        assertThat(service.isBlocked("user@example.com", "127.0.0.1")).isFalse();
        assertThat(service.registerFailure("user@example.com", "127.0.0.1")).isTrue();
    }

    private static final class MutableClock extends Clock {

        private Instant currentInstant;

        private MutableClock(Instant currentInstant) {
            this.currentInstant = currentInstant;
        }

        private void advance(Duration duration) {
            currentInstant = currentInstant.plus(duration);
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return currentInstant;
        }
    }
}
