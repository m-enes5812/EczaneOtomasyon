package com.eczane.ui;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/** Türkçe tarih biçimleri. */
public final class Fmt {

    private static final Locale TR = Locale.forLanguageTag("tr");
    private static final DateTimeFormatter FULL = DateTimeFormatter.ofPattern("d MMMM yyyy, HH:mm", TR);
    private static final DateTimeFormatter SHORT = DateTimeFormatter.ofPattern("d MMM yyyy", TR);
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm", TR);
    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("d MMMM yyyy, EEEE", TR);

    private Fmt() {
    }

    public static String full(LocalDateTime t) {
        return t == null ? "—" : t.format(FULL);
    }

    /** "Bugün 14:32", "Dün 09:10" ya da "3 Kas 2026". */
    public static String relative(LocalDateTime t) {
        if (t == null) return "—";
        LocalDate d = t.toLocalDate(), today = LocalDate.now();
        if (d.equals(today)) return "Bugün " + t.format(TIME);
        if (d.equals(today.minusDays(1))) return "Dün " + t.format(TIME);
        return t.format(SHORT);
    }

    public static String time(LocalDateTime t) {
        return t == null ? "" : t.format(TIME);
    }

    public static String today() {
        return LocalDate.now().format(DAY);
    }

    public static String greeting() {
        int h = LocalDateTime.now().getHour();
        if (h < 6) return "İyi geceler";
        if (h < 12) return "Günaydın";
        if (h < 18) return "İyi günler";
        return "İyi akşamlar";
    }

    public static String firstName(String full) {
        if (full == null || full.isBlank()) return "";
        String first = full.strip().split("\\s+")[0];
        // "MUSTAFA" -> "Mustafa"
        return first.substring(0, 1).toUpperCase(TR) + first.substring(1).toLowerCase(TR);
    }
}
