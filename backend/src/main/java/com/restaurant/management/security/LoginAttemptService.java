package com.restaurant.management.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

@Service
public class LoginAttemptService {

    private final ConcurrentMap<String, AttemptWindow> attempts = new ConcurrentHashMap<>();
    private final int maximumAttempts;
    private final Duration windowDuration;
    private final Clock clock;

    @Autowired
    public LoginAttemptService(
            @Value("${app.security.login-attempts.maximum:5}") int maximumAttempts,
            @Value("${app.security.login-attempts.window:15m}") Duration windowDuration
    ) {
        this(maximumAttempts, windowDuration, Clock.systemUTC());
    }

    LoginAttemptService(int maximumAttempts, Duration windowDuration, Clock clock) {
        if (maximumAttempts < 1 || windowDuration.isZero() || windowDuration.isNegative()) {
            throw new IllegalArgumentException("Configuration de limitation des connexions invalide");
        }
        this.maximumAttempts = maximumAttempts;
        this.windowDuration = windowDuration;
        this.clock = clock;
    }

    public boolean isBlocked(String email, String remoteAddress) {
        String key = key(email, remoteAddress);
        AttemptWindow attemptWindow = attempts.get(key);
        if (attemptWindow == null) {
            return false;
        }
        if (attemptWindow.startedAt().plus(windowDuration).isBefore(clock.instant())) {
            attempts.remove(key, attemptWindow);
            return false;
        }
        return attemptWindow.failures() >= maximumAttempts;
    }

    public boolean registerFailure(String email, String remoteAddress) {
        Instant now = clock.instant();
        AttemptWindow updated = attempts.compute(key(email, remoteAddress), (key, current) -> {
            if (current == null || current.startedAt().plus(windowDuration).isBefore(now)) {
                return new AttemptWindow(now, 1);
            }
            return new AttemptWindow(current.startedAt(), current.failures() + 1);
        });
        return updated.failures() >= maximumAttempts;
    }

    public void reset(String email, String remoteAddress) {
        attempts.remove(key(email, remoteAddress));
    }

    private String key(String email, String remoteAddress) {
        String normalizedEmail = email == null ? "" : email.strip().toLowerCase(Locale.ROOT);
        String normalizedAddress = remoteAddress == null ? "" : remoteAddress;
        return normalizedAddress + '\u0000' + normalizedEmail;
    }

    private record AttemptWindow(Instant startedAt, int failures) {
    }
}
