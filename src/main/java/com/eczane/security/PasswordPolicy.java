package com.eczane.security;

import java.util.Optional;

/** Yeni şifre kuralları. Eski kısa şifreler girişte kabul edilmeye devam eder. */
public final class PasswordPolicy {

    public static final int MIN_LENGTH = 8;
    public static final int MAX_LENGTH = 128;

    private PasswordPolicy() {
    }

    /** Kural ihlali varsa kullanıcıya gösterilecek mesaj. */
    public static Optional<String> check(String password) {
        if (password == null || password.length() < MIN_LENGTH) {
            return Optional.of("Şifre en az " + MIN_LENGTH + " karakter olmalı.");
        }
        if (password.length() > MAX_LENGTH) {
            return Optional.of("Şifre en fazla " + MAX_LENGTH + " karakter olabilir.");
        }
        boolean letter = password.chars().anyMatch(Character::isLetter);
        boolean digit = password.chars().anyMatch(Character::isDigit);
        if (!letter || !digit) {
            return Optional.of("Şifre en az bir harf ve bir rakam içermeli.");
        }
        return Optional.empty();
    }

    /** 0–4 arası basit güç puanı (arayüzdeki gösterge için). */
    public static int strength(String password) {
        if (password == null || password.isEmpty()) return 0;
        int score = 0;
        if (password.length() >= MIN_LENGTH) score++;
        if (password.length() >= 12) score++;
        if (password.chars().anyMatch(Character::isUpperCase) && password.chars().anyMatch(Character::isLowerCase)) score++;
        if (password.chars().anyMatch(Character::isDigit)) score++;
        if (password.chars().anyMatch(c -> !Character.isLetterOrDigit(c))) score++;
        return Math.min(4, score);
    }
}
