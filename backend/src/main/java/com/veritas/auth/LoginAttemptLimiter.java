package com.veritas.auth;

import com.veritas.common.AppException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Per-username throttle on failed logins. In-process and therefore per-replica; that is
 * enough to blunt credential stuffing against a single instance, and it is deliberately
 * bounded so the map itself cannot be used as a memory-exhaustion vector.
 */
@Component
public class LoginAttemptLimiter {

    private static final int MAX_FAILURES = 5;
    private static final Duration LOCKOUT = Duration.ofMinutes(15);
    private static final int MAX_TRACKED_USERNAMES = 10_000;

    private final Map<String, Attempts> attempts = new ConcurrentHashMap<>();
    private final Clock clock;

    public LoginAttemptLimiter(Clock clock) {
        this.clock = clock;
    }

    public void checkAllowed(String username) {
        Attempts current = attempts.get(key(username));
        if (current == null) {
            return;
        }
        if (current.blockedUntil != null && current.blockedUntil.isAfter(clock.instant())) {
            throw new TooManyAttemptsException(current.blockedUntil);
        }
        if (current.blockedUntil != null) {
            attempts.remove(key(username));
        }
    }

    public void recordFailure(String username) {
        if (attempts.size() >= MAX_TRACKED_USERNAMES) {
            evictExpired();
        }
        Attempts current = attempts.computeIfAbsent(key(username), k -> new Attempts());
        if (current.count.incrementAndGet() >= MAX_FAILURES) {
            current.blockedUntil = clock.instant().plus(LOCKOUT);
        }
    }

    public void recordSuccess(String username) {
        attempts.remove(key(username));
    }

    private void evictExpired() {
        Instant now = clock.instant();
        attempts.entrySet().removeIf(e -> e.getValue().blockedUntil != null
                && e.getValue().blockedUntil.isBefore(now));
        if (attempts.size() >= MAX_TRACKED_USERNAMES) {
            attempts.clear();
        }
    }

    private String key(String username) {
        return username == null ? "" : username.toLowerCase();
    }

    private static final class Attempts {
        private final AtomicInteger count = new AtomicInteger();
        private volatile Instant blockedUntil;
    }

    public static class TooManyAttemptsException extends AppException {
        public TooManyAttemptsException(Instant until) {
            super(HttpStatus.TOO_MANY_REQUESTS, "TOO_MANY_LOGIN_ATTEMPTS",
                    "Too many failed login attempts. Try again after " + until + ".");
        }
    }
}
