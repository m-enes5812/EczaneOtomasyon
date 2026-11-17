package com.eczane.model;

import java.time.LocalDateTime;

/** Sisteme kayıtlı kullanıcı. Şifre özeti bu nesnede tutulmaz. */
public record User(long id, Role role, String tc, String fullName, Long eczaneId, LocalDateTime createdAt) {

    public boolean is(Role r) { return role == r; }

    /** Ekranda gösterilecek maskeli TC: 105******76 */
    public String maskedTc() {
        if (tc == null || tc.length() < 5) return tc;
        return tc.substring(0, 3) + "******" + tc.substring(tc.length() - 2);
    }

    public String initials() {
        if (fullName == null || fullName.isBlank()) return "?";
        String[] parts = fullName.trim().split("\s+");
        String first = parts[0].substring(0, 1);
        String last = parts.length > 1 ? parts[parts.length - 1].substring(0, 1) : "";
        return (first + last).toUpperCase(java.util.Locale.forLanguageTag("tr"));
    }
}
