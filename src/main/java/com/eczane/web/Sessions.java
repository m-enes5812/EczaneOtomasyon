package com.eczane.web;

import com.eczane.model.User;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Sunucu tarafı oturumlar. Tarayıcıya yalnızca rastgele bir anahtar (çerez) verilir; kullanıcı bilgisi sunucuda kalır.
 * Oturum, {@link #IDLE} boyunca işlem yapılmazsa ya da {@link #MAX_AGE} dolunca sona erer.
 */
final class Sessions {

    static final Duration IDLE = Duration.ofHours(2);
    static final Duration MAX_AGE = Duration.ofHours(12);

    private static final class Session {
        volatile User user;
        final Instant created;
        volatile Instant lastSeen;

        Session(User user, Instant now) {
            this.user = user;
            this.created = now;
            this.lastSeen = now;
        }
    }

    private final Map<String, Session> sessions = new ConcurrentHashMap<>();
    private final SecureRandom random = new SecureRandom();
    private final Clock clock;

    Sessions(Clock clock) {
        this.clock = clock;
    }

    Sessions() {
        this(Clock.systemUTC());
    }

    String create(User user) {
        byte[] b = new byte[32];
        random.nextBytes(b);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(b);
        sessions.put(token, new Session(user, clock.instant()));
        if (sessions.size() % 64 == 0) purge();
        return token;
    }

    Optional<User> get(String token) {
        if (token == null) return Optional.empty();
        Session s = sessions.get(token);
        if (s == null) return Optional.empty();
        Instant now = clock.instant();
        if (expired(s, now)) {
            sessions.remove(token);
            return Optional.empty();
        }
        s.lastSeen = now;
        return Optional.of(s.user);
    }

    /** Ad değişikliği gibi durumlarda kullanıcının tüm oturumlarını günceller. */
    void refresh(User user) {
        sessions.values().forEach(s -> {
            if (s.user.id() == user.id()) s.user = user;
        });
    }

    void remove(String token) {
        if (token != null) sessions.remove(token);
    }

    /** Şifre değişince diğer cihazlardaki oturumları kapatır. */
    void removeOthers(long userId, String keepToken) {
        sessions.entrySet().removeIf(e -> e.getValue().user.id() == userId && !e.getKey().equals(keepToken));
    }

    int size() {
        return sessions.size();
    }

    private boolean expired(Session s, Instant now) {
        return now.isAfter(s.lastSeen.plus(IDLE)) || now.isAfter(s.created.plus(MAX_AGE));
    }

    private void purge() {
        Instant now = clock.instant();
        sessions.values().removeIf(s -> expired(s, now));
    }
}
