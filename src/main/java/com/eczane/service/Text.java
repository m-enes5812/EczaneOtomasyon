package com.eczane.service;

/** Girdi temizleme yardımcıları. */
final class Text {

    private Text() {
    }

    /** Baş/son boşlukları siler, aradaki çoklu boşlukları teke indirir. */
    static String clean(String s) {
        return s == null ? "" : s.strip().replaceAll("\\s+", " ");
    }

    static String require(String s, String fieldLabel, int maxLen) {
        String v = clean(s);
        if (v.isEmpty()) throw new ServiceException(fieldLabel + " boş bırakılamaz.");
        if (v.length() > maxLen) throw new ServiceException(fieldLabel + " en fazla " + maxLen + " karakter olabilir.");
        return v;
    }

    static String optional(String s, int maxLen, String fieldLabel) {
        String v = s == null ? "" : s.strip();
        if (v.length() > maxLen) throw new ServiceException(fieldLabel + " en fazla " + maxLen + " karakter olabilir.");
        return v.isEmpty() ? null : v;
    }
}
