package com.eczane.model;

/** Reçete durumları. {@code code} veritabanında saklanan değerdir. */
public enum ReceteDurum {
    AKTIF("AKTIF", "Bekliyor"),
    VERILDI("VERILDI", "Verildi"),
    IPTAL("IPTAL", "İptal");

    private final String code;
    private final String label;

    ReceteDurum(String code, String label) {
        this.code = code;
        this.label = label;
    }

    public String code() { return code; }

    public String label() { return label; }

    public static ReceteDurum fromCode(String code) {
        for (ReceteDurum d : values()) {
            if (d.code.equalsIgnoreCase(code)) return d;
        }
        throw new IllegalArgumentException("Bilinmeyen durum: " + code);
    }

    @Override
    public String toString() { return label; }
}
