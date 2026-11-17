package com.eczane.model;

/** Kullanıcı rolleri. {@code code} veritabanında saklanan değerdir. */
public enum Role {
    HASTA("hasta", "Hasta"),
    DOKTOR("doktor", "Doktor"),
    ECZACI("eczaci", "Eczacı");

    private final String code;
    private final String label;

    Role(String code, String label) {
        this.code = code;
        this.label = label;
    }

    public String code() { return code; }

    public String label() { return label; }

    public static Role fromCode(String code) {
        for (Role r : values()) {
            if (r.code.equalsIgnoreCase(code)) return r;
        }
        throw new IllegalArgumentException("Bilinmeyen rol: " + code);
    }

    @Override
    public String toString() { return label; }
}
