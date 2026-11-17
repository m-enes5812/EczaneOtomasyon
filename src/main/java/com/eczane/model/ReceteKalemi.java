package com.eczane.model;

/** Reçetedeki bir ilaç satırı. */
public record ReceteKalemi(long ilacId, String ilacAdi, int adet, String kullanim) {
}
