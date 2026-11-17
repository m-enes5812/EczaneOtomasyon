package com.eczane.security;

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.HexFormat;
import java.util.Locale;

/**
 * PBKDF2-HMAC-SHA256 ile şifre ve güvenlik cevabı özetleme.
 *
 * <p>Biçim eski Python sürümüyle birebir aynıdır: {@code pbkdf2$<tekrar>$<tuz-hex>$<özet-hex>}.
 * Böylece eski veritabanındaki hesaplar aynı şifreyle giriş yapabilir.
 */
public final class PasswordHasher {

    /** OWASP 2023 önerisi (PBKDF2-HMAC-SHA256). */
    public static final int DEFAULT_ITERATIONS = 600_000;
    private static final String PREFIX = "pbkdf2$";
    private static final HexFormat HEX = HexFormat.of();
    private static final Locale TR = Locale.forLanguageTag("tr");

    private final int iterations;
    private final SecureRandom random = new SecureRandom();

    public PasswordHasher() {
        this(DEFAULT_ITERATIONS);
    }

    public PasswordHasher(int iterations) {
        if (iterations < 1) throw new IllegalArgumentException("iterations");
        this.iterations = iterations;
    }

    public String hash(String secret) {
        byte[] salt = new byte[16];
        random.nextBytes(salt);
        String saltHex = HEX.formatHex(salt);
        return PREFIX + iterations + "$" + saltHex + "$" + derive(secret, saltHex, iterations);
    }

    public boolean verify(String secret, String stored) {
        if (secret == null || stored == null) return false;
        try {
            if (stored.startsWith(PREFIX)) {
                String[] parts = stored.split("\\$", 4);
                if (parts.length != 4) return false;
                int iters = Integer.parseInt(parts[1]);
                return constantTimeEquals(derive(secret, parts[2], iters), parts[3]);
            }
            // Eski veritabanı uyumu: düz metin şifre (girişte otomatik yükseltilir)
            return constantTimeEquals(secret, stored);
        } catch (RuntimeException e) {
            return false;
        }
    }

    /** Özet eski biçimdeyse ya da tekrar sayısı düşükse yeniden özetlenmelidir. */
    public boolean needsRehash(String stored) {
        if (stored == null || !stored.startsWith(PREFIX)) return true;
        try {
            return Integer.parseInt(stored.split("\\$", 4)[1]) < iterations;
        } catch (RuntimeException e) {
            return true;
        }
    }

    /** Güvenlik cevapları büyük/küçük harf ve baştaki/sondaki boşluktan bağımsızdır. */
    public String hashAnswer(String answer) {
        return hash(normalizeAnswer(answer));
    }

    public boolean verifyAnswer(String answer, String stored) {
        if (stored == null) return false;
        if (!stored.startsWith(PREFIX)) {
            return constantTimeEquals(normalizeAnswer(answer), normalizeAnswer(stored));
        }
        // Eski sürüm Python'un lower() fonksiyonunu kullanıyordu (İ -> i̇). Önce Türkçe kurala göre,
        // olmazsa kök (ROOT) kurala göre dene; böylece eski cevaplar da tutar.
        return verify(normalizeAnswer(answer), stored)
                || verify(answer == null ? "" : answer.strip().toLowerCase(Locale.ROOT), stored);
    }

    static String normalizeAnswer(String answer) {
        return answer == null ? "" : answer.strip().toLowerCase(TR);
    }

    static String derive(String secret, String saltHex, int iters) {
        PBEKeySpec spec = new PBEKeySpec(secret.toCharArray(), HEX.parseHex(saltHex), iters, 256);
        try {
            byte[] dk = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).getEncoded();
            return HEX.formatHex(dk);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("PBKDF2 kullanılamıyor", e);
        } finally {
            spec.clearPassword();
        }
    }

    private static boolean constantTimeEquals(String a, String b) {
        return MessageDigest.isEqual(a.getBytes(StandardCharsets.UTF_8), b.getBytes(StandardCharsets.UTF_8));
    }
}
