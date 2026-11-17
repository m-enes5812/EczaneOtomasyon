package com.eczane.model;

import java.time.LocalDateTime;

/** Stok değişikliklerinin denetim kaydı. */
public record StokHareketi(long id, LocalDateTime tarih, String ilacAdi, int degisim, int yeniMiktar,
                           String sebep, String kullaniciAdi, Long receteId) {
}
