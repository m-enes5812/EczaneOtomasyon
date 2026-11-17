package com.eczane.model;

import java.time.LocalDateTime;
import java.util.List;

public record ReceteDetay(long id, ReceteDurum durum, String aciklama, LocalDateTime tarih,
                          long hastaId, String hastaAdi, String hastaTc,
                          long doktorId, String doktorAdi,
                          Long eczaneId, String eczaneAdi,
                          List<ReceteKalemi> kalemler) {
}
