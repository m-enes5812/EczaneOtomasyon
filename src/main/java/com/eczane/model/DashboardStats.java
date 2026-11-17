package com.eczane.model;

/** Genel bakış ekranındaki sayaçlar. */
public record DashboardStats(int toplam, int bekleyen, int verilen, int iptal,
                             int okunmamis, int bugun, int kritikStok) {
}
