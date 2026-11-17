package com.eczane.model;

import java.time.LocalDateTime;

public record StokKalemi(long ilacId, String ilacAdi, int miktar, int minMiktar, LocalDateTime sonGuncelleme) {

    public boolean kritik() { return miktar <= minMiktar; }

    public boolean tukendi() { return miktar <= 0; }
}
