package com.eczane.model;

import java.time.LocalDateTime;

public record Mesaj(long id, long gonderenId, String gonderenAdi, Role gonderenRol,
                    String metin, LocalDateTime tarih, boolean sistem) {
}
