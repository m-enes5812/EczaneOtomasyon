package com.eczane;

import com.eczane.config.AppPaths;
import com.eczane.db.Database;
import com.eczane.model.Mesaj;
import com.eczane.model.ReceteDetay;
import com.eczane.model.ReceteDurum;
import com.eczane.model.ReceteOzet;
import com.eczane.model.Role;
import com.eczane.model.StokKalemi;
import com.eczane.model.User;
import com.eczane.security.PasswordHasher;
import com.eczane.service.AuthService.RegisterRequest;
import com.eczane.service.ReceteService.KalemInput;
import com.eczane.service.ServiceException;
import com.eczane.service.Services;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ServiceFlowTest {

    static final String TC_DOKTOR = "10000000146";
    static final String TC_HASTA = "12345678950";
    static final String TC_ECZACI = "11111111110";
    static final String TC_HASTA2 = "22222222220";

    @TempDir
    Path tmp;
    Services s;
    User doktor, hasta, eczaci, hasta2;
    long merkezId;

    @BeforeEach
    void setUp() {
        AppPaths paths = AppPaths.of(tmp.resolve("data"));
        s = Services.create(paths, Database.open(paths.database()), new PasswordHasher(1000));
        merkezId = s.stok().eczaneler().getFirst().id();
        doktor = register(Role.DOKTOR, "Ayşe Yılmaz", TC_DOKTOR, null, null);
        hasta = register(Role.HASTA, "Mehmet Demir", TC_HASTA, null, null);
        eczaci = register(Role.ECZACI, "Zeynep Kaya", TC_ECZACI, merkezId, null);
        hasta2 = register(Role.HASTA, "Ali Can", TC_HASTA2, null, null);
    }

    @AfterEach
    void tearDown() {
        s.close();
    }

    private User register(Role role, String name, String tc, Long eczaneId, String yeniEczane) {
        return s.auth().register(new RegisterRequest(role, name, tc, "Eczane2026", "Eczane2026", eczaneId, yeniEczane,
                "Doğduğun şehir neresidir?", "İzmir"));
    }

    @Test
    void kayitVeGiris() {
        assertEquals(Role.DOKTOR, s.auth().login(TC_DOKTOR, "Eczane2026").role());
        ServiceException e = assertThrows(ServiceException.class, () -> s.auth().login(TC_DOKTOR, "yanlis"));
        assertTrue(e.getMessage().contains("hatalı"));
        assertThrows(ServiceException.class, () -> register(Role.HASTA, "Tekrar Kayıt", TC_HASTA, null, null),
                "aynı TC ikinci kez kaydolamaz");
        assertThrows(ServiceException.class, () -> register(Role.HASTA, "Geçersiz Tc", "12345678901", null, null));
    }

    @Test
    void ucHataliGiristenSonraHesapKilitlenir() {
        for (int i = 0; i < 2; i++) assertThrows(ServiceException.class, () -> s.auth().login(TC_HASTA, "x"));
        ServiceException e = assertThrows(ServiceException.class, () -> s.auth().login(TC_HASTA, "x"));
        assertTrue(e.getMessage().contains("Çok fazla"), e.getMessage());
        // doğru şifre de kilit süresince reddedilir
        assertThrows(ServiceException.class, () -> s.auth().login(TC_HASTA, "Eczane2026"));
    }

    @Test
    void sifreSifirlama() {
        assertEquals("Doğduğun şehir neresidir?", s.auth().securityQuestion(TC_HASTA));
        assertThrows(ServiceException.class, () -> s.auth().resetPassword(TC_HASTA, "Ankara", "YeniSifre1", "YeniSifre1"));
        s.auth().resetPassword(TC_HASTA, "izmir", "YeniSifre1", "YeniSifre1");
        assertNotNull(s.auth().login(TC_HASTA, "YeniSifre1"));
    }

    @Test
    void yeniEczaneIleEczaciKaydi() {
        User e2 = register(Role.ECZACI, "Can Öz", "33333333330", null, "Hayat Eczanesi");
        assertNotNull(e2.eczaneId());
        assertNotEquals(merkezId, e2.eczaneId());
        assertEquals(2, s.stok().eczaneler().size());
    }

    @Test
    void receteYazVerStokDusVeMesajlas() {
        long id = s.recete().create(doktor, TC_HASTA, merkezId, "Kontrol 1 hafta sonra",
                List.of(new KalemInput("Parol 500 mg", 2, "Günde 3x1"), new KalemInput("Augmentin", 1, "Günde 2x1"),
                        new KalemInput("parol 500 mg", 1, null)));

        ReceteDetay d = s.recete().detay(hasta, id);
        assertEquals(2, d.kalemler().size(), "aynı ilaç birleştirilmeli");
        assertEquals(3, d.kalemler().getFirst().adet());

        // doktor kendi oluşturduğu reçeteyi "okunmamış" görmemeli; hasta ve eczacı görmeli
        assertEquals(0, s.recete().list(doktor).getFirst().okunmamis());
        assertEquals(1, s.recete().list(hasta).getFirst().okunmamis());
        assertEquals(1, s.mesaj().unreadTotal(eczaci));

        // stok yok → verilemez
        ServiceException e = assertThrows(ServiceException.class, () -> s.recete().ver(eczaci, id));
        assertTrue(e.getMessage().contains("Stok yetersiz"));
        assertEquals(ReceteDurum.AKTIF, s.recete().detay(eczaci, id).durum(), "hata sonrası durum değişmemeli");

        s.stok().adjust(eczaci, "Parol 500 mg", 10, "Depodan alım");
        s.stok().adjust(eczaci, "AUGMENTIN", 5, "Depodan alım");
        s.recete().ver(eczaci, id);

        assertEquals(ReceteDurum.VERILDI, s.recete().detay(hasta, id).durum());
        List<StokKalemi> stok = s.stok().list(eczaci);
        assertEquals(7, stok.stream().filter(k -> k.ilacAdi().equals("Parol 500 mg")).findFirst().orElseThrow().miktar());
        assertEquals(4, stok.stream().filter(k -> k.ilacAdi().equals("Augmentin")).findFirst().orElseThrow().miktar());
        assertEquals(4, s.stok().hareketler(eczaci, 50).size());

        assertThrows(ServiceException.class, () -> s.recete().ver(eczaci, id), "ikinci kez verilemez");
        assertThrows(ServiceException.class, () -> s.recete().iptal(doktor, id, null), "verilen reçete iptal edilemez");

        s.mesaj().send(hasta, id, "Teşekkürler!");
        List<Mesaj> mesajlar = s.mesaj().list(doktor, id);
        assertEquals(3, mesajlar.size());
        assertTrue(mesajlar.get(0).sistem());
        assertEquals("Teşekkürler!", mesajlar.get(2).metin());
        assertEquals(0, s.recete().list(doktor).getFirst().okunmamis(), "okununca sayaç sıfırlanır");
    }

    @Test
    void yetkiKontrolleri() {
        long id = s.recete().create(doktor, TC_HASTA, merkezId, null, List.of(new KalemInput("Aferin", 1, null)));
        assertThrows(ServiceException.class, () -> s.recete().detay(hasta2, id), "başka hasta göremez");
        assertThrows(ServiceException.class, () -> s.mesaj().send(hasta2, id, "merhaba"));
        assertThrows(ServiceException.class, () -> s.recete().iptal(hasta, id, null), "hasta iptal edemez");
        assertThrows(ServiceException.class, () -> s.recete().create(hasta, TC_HASTA2, merkezId, null,
                List.of(new KalemInput("X", 1, null))), "hasta reçete yazamaz");
        assertThrows(ServiceException.class, () -> s.stok().adjust(doktor, "X", 1, "Depodan alım"));

        User digerEczaci = register(Role.ECZACI, "Başka Eczacı", "33333333330", null, "Hayat Eczanesi");
        assertThrows(ServiceException.class, () -> s.recete().ver(digerEczaci, id), "başka eczane veremez");
        assertTrue(s.recete().list(digerEczaci).isEmpty());

        s.recete().iptal(doktor, id, "Yanlış ilaç");
        assertEquals(ReceteDurum.IPTAL, s.recete().detay(eczaci, id).durum());
    }

    @Test
    void stokSifirinAltinaDusmez() {
        s.stok().adjust(eczaci, "Majezik", 3, "Depodan alım");
        ServiceException e = assertThrows(ServiceException.class, () -> s.stok().adjust(eczaci, "Majezik", -5, "Hasarlı / fire"));
        assertTrue(e.getMessage().contains("3"));
        s.stok().adjust(eczaci, "Majezik", -3, "Hasarlı / fire");
        StokKalemi k = s.stok().list(eczaci).getFirst();
        assertEquals(0, k.miktar());
        assertTrue(k.kritik());
    }

    @Test
    void istatistikler() {
        s.recete().create(doktor, TC_HASTA, merkezId, null, List.of(new KalemInput("A", 1, null)));
        long id2 = s.recete().create(doktor, TC_HASTA2, merkezId, null, List.of(new KalemInput("B", 1, null)));
        s.recete().iptal(eczaci, id2, null);
        var st = s.recete().stats(doktor);
        assertEquals(2, st.toplam());
        assertEquals(1, st.bekleyen());
        assertEquals(1, st.iptal());
        assertEquals(2, st.bugun());
        List<ReceteOzet> l = s.recete().list(eczaci);
        assertEquals(2, l.size());
    }

    @Test
    void yedekAlinir() throws Exception {
        Path p = s.backup().backupNow();
        assertTrue(Files.size(p) > 0);
        try (Connection c = DriverManager.getConnection("jdbc:sqlite:" + p);
             Statement st = c.createStatement();
             var rs = st.executeQuery("SELECT COUNT(*) FROM users")) {
            rs.next();
            assertEquals(4, rs.getInt(1));
        }
    }

    /** Eski Python sürümünün oluşturduğu veritabanı açılabilmeli ve hesaplar çalışmalı. */
    @Test
    void eskiPythonVeritabaniniAcar() throws Exception {
        Path legacy = tmp.resolve("legacy.db");
        try (Connection c = DriverManager.getConnection("jdbc:sqlite:" + legacy); Statement st = c.createStatement()) {
            st.execute("CREATE TABLE users(id INTEGER PRIMARY KEY AUTOINCREMENT, role TEXT NOT NULL, tc TEXT, password TEXT NOT NULL, full_name TEXT, eczane_id INTEGER, created_at TEXT)");
            st.execute("CREATE TABLE eczaneler(id INTEGER PRIMARY KEY AUTOINCREMENT, ad TEXT UNIQUE NOT NULL)");
            st.execute("CREATE TABLE ilaclar(id INTEGER PRIMARY KEY AUTOINCREMENT, ad TEXT UNIQUE NOT NULL)");
            st.execute("CREATE TABLE stoklar(id INTEGER PRIMARY KEY AUTOINCREMENT, eczane_id INTEGER NOT NULL, ilac_id INTEGER NOT NULL, miktar INTEGER NOT NULL DEFAULT 0, UNIQUE(eczane_id, ilac_id))");
            st.execute("INSERT INTO eczaneler(ad) VALUES('Merkez Eczanesi')");
            st.execute("INSERT INTO ilaclar(ad) VALUES('Parol')");
            st.execute("INSERT INTO stoklar(eczane_id, ilac_id, miktar) VALUES(1, 1, 5)");
            // Python ile üretilmiş özet (şifre: Şifre123) ve düz metin şifreli eski hesap
            st.execute("INSERT INTO users(role, tc, password, full_name, eczane_id, created_at) VALUES('eczaci','" + TC_ECZACI
                    + "','pbkdf2$1000$00112233445566778899aabbccddeeff$4c8362a581dd75d3f47913deb19628bc4d9091e283eb852b98e3e11c2147203f','Eski Eczacı',1,'2025-12-04 22:00:00')");
            st.execute("INSERT INTO users(role, tc, password, full_name, created_at) VALUES('hasta','" + TC_HASTA + "','1234','Eski Hasta','2025-12-04 22:00:00')");
        }
        PasswordHasher h = new PasswordHasher(1000);
        AppPaths paths = AppPaths.of(tmp.resolve("legacy-home"));
        try (Services old = Services.create(paths, Database.open(legacy), h)) {
            User e = old.auth().login(TC_ECZACI, "Şifre123");
            assertEquals(Role.ECZACI, e.role());
            assertEquals(5, old.stok().list(e).getFirst().miktar());
            assertEquals(10, old.stok().list(e).getFirst().minMiktar());
            assertNotNull(old.auth().login(TC_HASTA, "1234"));
        }
        // düz metin şifre girişte özetlenmiş olmalı
        try (Connection c = DriverManager.getConnection("jdbc:sqlite:" + legacy); Statement st = c.createStatement();
             var rs = st.executeQuery("SELECT password FROM users WHERE tc='" + TC_HASTA + "'")) {
            rs.next();
            assertTrue(rs.getString(1).startsWith("pbkdf2$"));
        }
    }
}
