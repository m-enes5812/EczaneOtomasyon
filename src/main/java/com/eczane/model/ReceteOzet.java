package com.eczane.model;

import java.time.LocalDateTime;

/**
 * Listelerde gösterilen reçete satırı.
 *
 * @param okunmamis oturumdaki kullanıcının rolüne göre okunmamış mesaj sayısı
 */
public record ReceteOzet(long id, ReceteDurum durum, String aciklama, LocalDateTime tarih,
                         String hastaAdi, String doktorAdi, String eczaneAdi,
                         int kalemSayisi, int okunmamis) {
}
