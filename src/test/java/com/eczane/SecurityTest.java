package com.eczane;

import com.eczane.security.LoginThrottle;
import com.eczane.security.PasswordHasher;
import com.eczane.security.PasswordPolicy;
import com.eczane.security.TcKimlik;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.*;

class SecurityTest {

    private final PasswordHasher hasher = new PasswordHasher(1000);

    @Test
    void pythonSurumuIleUretilenOzetDogrulanir() {
        // Python: hashlib.pbkdf2_hmac('sha256', 'Şifre123'.encode(), bytes.fromhex('0011..ff'), 1000).hex()
        String python = "pbkdf2$1000$00112233445566778899aabbccddeeff$"
                + "4c8362a581dd75d3f47913deb19628bc4d9091e283eb852b98e3e11c2147203f";
        assertTrue(hasher.verify("Şifre123", python));
        assertFalse(hasher.verify("şifre123", python));
    }

    @Test
    void ozetOlusturVeDogrula() {
        String h = hasher.hash("Gizli123");
        assertTrue(h.startsWith("pbkdf2$1000$"));
        assertTrue(hasher.verify("Gizli123", h));
        assertFalse(hasher.verify("gizli123", h));
        assertNotEquals(h, hasher.hash("Gizli123"), "her özette farklı tuz olmalı");
    }

    @Test
    void eskiDuzMetinSifreKabulEdilirVeYukseltmeIster() {
        assertTrue(hasher.verify("1234", "1234"));
        assertTrue(hasher.needsRehash("1234"));
        assertTrue(new PasswordHasher(2000).needsRehash(hasher.hash("x")));
        assertFalse(hasher.needsRehash(hasher.hash("x")));
    }

    @Test
    void bozukOzetHataVermezFalseDoner() {
        assertFalse(hasher.verify("a", "pbkdf2$abc$zz$yy"));
        assertFalse(hasher.verify("a", "pbkdf2$1000"));
        assertFalse(hasher.verify(null, "x"));
    }

    @Test
    void guvenlikCevabiBuyukKucukHarfDuyarsiz() {
        String h = hasher.hashAnswer("  İstanbul ");
        assertTrue(hasher.verifyAnswer("istanbul", h));
        assertTrue(hasher.verifyAnswer("İSTANBUL", h));
        assertFalse(hasher.verifyAnswer("ankara", h));
    }

    @Test
    void tcKimlikAlgoritmasi() {
        assertTrue(TcKimlik.isValid("10000000146"));
        assertTrue(TcKimlik.isValid("12345678950"));
        assertFalse(TcKimlik.isValid("12345678901"));
        assertFalse(TcKimlik.isValid("02345678950"), "ilk hane 0 olamaz");
        assertFalse(TcKimlik.isValid("1234567890"));
        assertFalse(TcKimlik.isValid("1234567895a"));
        assertTrue(TcKimlik.isWellFormed("12345678901"));
        assertFalse(TcKimlik.isWellFormed("١٢٣٤٥٦٧٨٩٠١"), "Arapça rakamlar kabul edilmez");
    }

    @Test
    void sifrePolitikasi() {
        assertTrue(PasswordPolicy.check("kisa1").isPresent());
        assertTrue(PasswordPolicy.check("sadeceharf").isPresent());
        assertTrue(PasswordPolicy.check("12345678").isPresent());
        assertTrue(PasswordPolicy.check("Eczane2026").isEmpty());
        assertTrue(PasswordPolicy.strength("Eczane2026!x") > PasswordPolicy.strength("eczane12"));
    }

    @Test
    void ucHatadanSonraKilitlenirVeSureIkiKatinaCikar() {
        MutableClock clock = new MutableClock();
        LoginThrottle t = new LoginThrottle(3, Duration.ofSeconds(60), clock);
        t.recordFailure("k");
        t.recordFailure("k");
        assertEquals(Duration.ZERO, t.remainingLock("k"));
        t.recordFailure("k");
        assertEquals(Duration.ofSeconds(60), t.remainingLock("k"));

        clock.advance(Duration.ofSeconds(61));
        assertEquals(Duration.ZERO, t.remainingLock("k"));
        t.recordFailure("k");
        t.recordFailure("k");
        t.recordFailure("k");
        assertEquals(Duration.ofSeconds(120), t.remainingLock("k"));

        t.recordSuccess("k");
        assertEquals(Duration.ZERO, t.remainingLock("k"));
    }

    static final class MutableClock extends Clock {
        private Instant now = Instant.parse("2026-01-01T00:00:00Z");

        void advance(Duration d) { now = now.plus(d); }

        @Override public ZoneOffset getZone() { return ZoneOffset.UTC; }

        @Override public Clock withZone(java.time.ZoneId zone) { return this; }

        @Override public Instant instant() { return now; }
    }
}
