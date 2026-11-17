package com.eczane.security;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Hatalı deneme sınırlayıcı. Belirli sayıda hatadan sonra anahtarı (TC) bir süre kilitler.
 * Her yeni kilitte süre ikiye katlanır (en fazla 15 dk).
 */
public final class LoginThrottle {

    private static final Duration MAX_LOCK = Duration.ofMinutes(15);

    private final int maxFailures;
    private final Duration baseLock;
    private final Clock clock;
    private final Map<String, State> states = new ConcurrentHashMap<>();

    public LoginThrottle(int maxFailures, Duration baseLock, Clock clock) {
        this.maxFailures = maxFailures;
        this.baseLock = baseLock;
        this.clock = clock;
    }

    public LoginThrottle() {
        this(3, Duration.ofSeconds(60), Clock.systemUTC());
    }

    /** Kilitliyse kalan süre, değilse {@link Duration#ZERO}. */
    public Duration remainingLock(String key) {
        State s = states.get(key);
        if (s == null || s.lockedUntil == null) return Duration.ZERO;
        Duration left = Duration.between(clock.instant(), s.lockedUntil);
        return left.isNegative() ? Duration.ZERO : left;
    }

    public void recordFailure(String key) {
        states.compute(key, (k, s) -> {
            State st = s == null ? new State() : s;
            Instant now = clock.instant();
            if (st.lockedUntil != null && now.isBefore(st.lockedUntil)) return st;
            st.failures++;
            if (st.failures >= maxFailures) {
                Duration lock = baseLock.multipliedBy(1L << Math.min(st.lockCount, 10));
                if (lock.compareTo(MAX_LOCK) > 0) lock = MAX_LOCK;
                st.lockedUntil = now.plus(lock);
                st.lockCount++;
                st.failures = 0;
            }
            return st;
        });
    }

    public void recordSuccess(String key) {
        states.remove(key);
    }

    private static final class State {
        int failures;
        int lockCount;
        Instant lockedUntil;
    }
}
