package com.eczane.service;

import com.eczane.db.Database;
import com.eczane.model.Eczane;
import com.eczane.model.Ilac;
import com.eczane.model.Role;
import com.eczane.model.StokHareketi;
import com.eczane.model.StokKalemi;
import com.eczane.model.User;
import com.eczane.repo.CatalogRepository;
import com.eczane.repo.StokRepository;

import java.util.List;
import java.util.logging.Logger;

/** Eczane stok yönetimi ve katalog (eczane/ilaç) erişimi. */
public final class StokService {

    private static final Logger LOG = Logger.getLogger(StokService.class.getName());
    public static final int MAX_MIKTAR = 100_000;

    public static final List<String> GIRIS_SEBEPLERI = List.of("Depodan alım", "Hasta iadesi", "Sayım düzeltmesi");
    public static final List<String> CIKIS_SEBEPLERI = List.of("Son kullanma tarihi geçti", "Hasarlı / fire", "Sayım düzeltmesi", "Depoya iade");

    private final Database db;
    private final StokRepository stok = new StokRepository();
    private final CatalogRepository catalog = new CatalogRepository();

    public StokService(Database db) {
        this.db = db;
    }

    public List<StokKalemi> list(User eczaci) {
        long eczaneId = ReceteService.ownerId(requireEczaci(eczaci));
        return db.read(c -> stok.list(c, eczaneId));
    }

    /**
     * Stoğa giriş (+) ya da çıkış (−). Stok sıfırın altına düşürülemez.
     *
     * @return yeni miktar
     */
    public int adjust(User eczaci, String ilacAdi, int delta, String sebep) {
        long eczaneId = ReceteService.ownerId(requireEczaci(eczaci));
        String ad = Text.require(ilacAdi, "İlaç adı", 100);
        String neden = Text.require(sebep, "Sebep", 100);
        if (delta == 0) throw new ServiceException("Miktar sıfır olamaz.");
        if (Math.abs(delta) > MAX_MIKTAR) throw new ServiceException("Tek seferde en fazla " + MAX_MIKTAR + " adet işlenebilir.");
        int yeni = db.tx(c -> {
            long ilacId = catalog.ilacIdOrCreate(c, ad);
            int mevcut = stok.miktar(c, eczaneId, ilacId);
            int sonuc = mevcut + delta;
            if (sonuc < 0) throw new ServiceException(ad + " için stokta yalnızca " + mevcut + " adet var.");
            if (sonuc > MAX_MIKTAR) throw new ServiceException("Stok en fazla " + MAX_MIKTAR + " olabilir.");
            stok.setMiktar(c, eczaneId, ilacId, sonuc);
            stok.hareketEkle(c, eczaneId, ilacId, delta, sonuc, neden, eczaci.id(), null);
            return sonuc;
        });
        LOG.info(() -> "Stok: eczane #" + eczaneId + " " + ad + " " + (delta > 0 ? "+" : "") + delta + " → " + yeni);
        return yeni;
    }

    public void setMin(User eczaci, long ilacId, int min) {
        long eczaneId = ReceteService.ownerId(requireEczaci(eczaci));
        if (min < 0 || min > MAX_MIKTAR) throw new ServiceException("Kritik seviye 0 ile " + MAX_MIKTAR + " arasında olmalı.");
        db.txRun(c -> stok.setMin(c, eczaneId, ilacId, min));
    }

    public List<StokHareketi> hareketler(User eczaci, int limit) {
        long eczaneId = ReceteService.ownerId(requireEczaci(eczaci));
        return db.read(c -> stok.hareketler(c, eczaneId, limit));
    }

    public List<Eczane> eczaneler() {
        return db.read(catalog::eczaneler);
    }

    public List<Ilac> ilaclar() {
        return db.read(catalog::ilaclar);
    }

    public String eczaneAdi(Long eczaneId) {
        if (eczaneId == null) return null;
        return db.read(c -> catalog.eczane(c, eczaneId)).map(Eczane::ad).orElse(null);
    }

    private static User requireEczaci(User u) {
        ReceteService.require(u, Role.ECZACI);
        return u;
    }
}
