package com.eczane.service;

import com.eczane.db.Database;
import com.eczane.model.DashboardStats;
import com.eczane.model.ReceteDetay;
import com.eczane.model.ReceteDurum;
import com.eczane.model.ReceteKalemi;
import com.eczane.model.ReceteOzet;
import com.eczane.model.Role;
import com.eczane.model.User;
import com.eczane.repo.CatalogRepository;
import com.eczane.repo.MesajRepository;
import com.eczane.repo.ReceteRepository;
import com.eczane.repo.ReceteRepository.Sahiplik;
import com.eczane.repo.StokRepository;
import com.eczane.repo.UserRepository;
import com.eczane.security.TcKimlik;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.logging.Logger;

/** Reçete yazma, listeleme, verme ve iptal işlemleri. */
public final class ReceteService {

    private static final Logger LOG = Logger.getLogger(ReceteService.class.getName());
    public static final int MAX_KALEM = 20;
    public static final int MAX_ADET = 99;

    /** Doktorun formdan girdiği ilaç satırı. */
    public record KalemInput(String ilacAdi, int adet, String kullanim) {
    }

    /** Eczacının verme ekranında gördüğü satır: istenen ve stoktaki miktar. */
    public record KalemStok(ReceteKalemi kalem, int stok) {
        public boolean yeterli() { return stok >= kalem.adet(); }
    }

    private final Database db;
    private final UserRepository users = new UserRepository();
    private final CatalogRepository catalog = new CatalogRepository();
    private final ReceteRepository receteler = new ReceteRepository();
    private final StokRepository stok = new StokRepository();
    private final MesajRepository mesajlar = new MesajRepository();

    public ReceteService(Database db) {
        this.db = db;
    }

    /** Doktorun reçete yazarken hastayı doğrulaması için. Yalnızca hasta rolündeki hesapları döndürür. */
    public Optional<User> findHasta(User doktor, String tc) {
        require(doktor, Role.DOKTOR);
        String key = Text.clean(tc);
        if (!TcKimlik.isWellFormed(key)) return Optional.empty();
        return db.read(c -> users.findByTc(c, key)).filter(u -> u.is(Role.HASTA));
    }

    public long create(User doktor, String hastaTc, Long eczaneId, String aciklama, List<KalemInput> kalemler) {
        require(doktor, Role.DOKTOR);
        String tc = Text.clean(hastaTc);
        if (!TcKimlik.isWellFormed(tc)) throw new ServiceException("Hasta T.C. Kimlik No 11 haneli olmalıdır.");
        if (eczaneId == null) throw new ServiceException("Eczane seçmelisin.");
        String not = Text.optional(aciklama, 500, "Açıklama");
        if (kalemler == null || kalemler.isEmpty()) throw new ServiceException("En az bir ilaç eklemelisin.");
        if (kalemler.size() > MAX_KALEM) throw new ServiceException("Bir reçetede en fazla " + MAX_KALEM + " ilaç olabilir.");

        // Aynı ilaç iki kez yazıldıysa birleştir.
        Map<String, KalemInput> birlesik = new LinkedHashMap<>();
        for (KalemInput k : kalemler) {
            String ad = Text.require(k.ilacAdi(), "İlaç adı", 100);
            if (k.adet() < 1 || k.adet() > MAX_ADET) throw new ServiceException(ad + ": adet 1 ile " + MAX_ADET + " arasında olmalı.");
            String kullanim = Text.optional(k.kullanim(), 200, "Kullanım talimatı");
            birlesik.merge(ad.toLowerCase(java.util.Locale.forLanguageTag("tr")), new KalemInput(ad, k.adet(), kullanim),
                    (a, b) -> new KalemInput(a.ilacAdi(), Math.min(MAX_ADET, a.adet() + b.adet()),
                            a.kullanim() != null ? a.kullanim() : b.kullanim()));
        }

        long id = db.tx(c -> {
            User hasta = users.findByTc(c, tc).filter(u -> u.is(Role.HASTA))
                    .orElseThrow(() -> new ServiceException("Bu T.C. Kimlik No ile kayıtlı hasta bulunamadı."));
            if (hasta.id() == doktor.id()) throw new ServiceException("Kendine reçete yazamazsın.");
            catalog.eczane(c, eczaneId).orElseThrow(() -> new ServiceException("Seçilen eczane bulunamadı."));
            long rid = receteler.insert(c, hasta.id(), doktor.id(), eczaneId, not);
            for (KalemInput k : birlesik.values()) {
                receteler.insertKalem(c, rid, catalog.ilacIdOrCreate(c, k.ilacAdi()), k.adet(), k.kullanim());
            }
            mesajlar.insert(c, rid, doktor.id(), Role.DOKTOR, "Reçete oluşturuldu. Doktor: " + doktor.fullName(), true);
            return rid;
        });
        LOG.info(() -> "Reçete #" + id + " oluşturuldu (doktor #" + doktor.id() + ")");
        return id;
    }

    public List<ReceteOzet> list(User user) {
        return db.read(c -> receteler.listFor(c, user.role(), ownerId(user)));
    }

    public ReceteDetay detay(User user, long receteId) {
        return db.read(c -> {
            authorize(c, user, receteId);
            return receteler.detay(c, receteId).orElseThrow(() -> new ServiceException("Reçete bulunamadı."));
        });
    }

    /** Eczacı için: her kalemin stoktaki karşılığı. */
    public List<KalemStok> stokDurumu(User eczaci, long receteId) {
        require(eczaci, Role.ECZACI);
        return db.read(c -> {
            authorize(c, eczaci, receteId);
            ReceteDetay d = receteler.detay(c, receteId).orElseThrow(() -> new ServiceException("Reçete bulunamadı."));
            List<KalemStok> out = new ArrayList<>();
            for (ReceteKalemi k : d.kalemler()) out.add(new KalemStok(k, stok.miktar(c, eczaci.eczaneId(), k.ilacId())));
            return out;
        });
    }

    /** İlaçları hastaya verir: stok kontrolü, stok düşümü, hareket kaydı ve durum değişikliği tek işlemde yapılır. */
    public void ver(User eczaci, long receteId) {
        require(eczaci, Role.ECZACI);
        db.txRun(c -> {
            Sahiplik s = authorize(c, eczaci, receteId);
            if (s.durum() != ReceteDurum.AKTIF) throw new ServiceException("Bu reçete artık beklemede değil (" + s.durum().label() + ").");
            ReceteDetay d = receteler.detay(c, receteId).orElseThrow();
            long eczaneId = eczaci.eczaneId();

            // Eski kayıtlarda aynı ilaç iki satırda olabilir; ilaç başına toplam istenen miktar.
            Map<Long, Integer> istenen = new LinkedHashMap<>();
            Map<Long, String> adlar = new LinkedHashMap<>();
            for (ReceteKalemi k : d.kalemler()) {
                istenen.merge(k.ilacId(), k.adet(), Integer::sum);
                adlar.putIfAbsent(k.ilacId(), k.ilacAdi());
            }

            List<String> eksik = new ArrayList<>();
            for (var e : istenen.entrySet()) {
                int mevcut = stok.miktar(c, eczaneId, e.getKey());
                if (mevcut < e.getValue()) {
                    eksik.add("• " + adlar.get(e.getKey()) + " — istenen " + e.getValue() + ", stokta " + mevcut);
                }
            }
            if (!eksik.isEmpty()) throw new ServiceException("Stok yetersiz:\n" + String.join("\n", eksik));

            if (!receteler.changeDurum(c, receteId, ReceteDurum.AKTIF, ReceteDurum.VERILDI)) {
                throw new ServiceException("Reçete başka bir işlem tarafından değiştirildi. Listeyi yenile.");
            }
            for (var e : istenen.entrySet()) {
                int yeni = stok.miktar(c, eczaneId, e.getKey()) - e.getValue();
                stok.setMiktar(c, eczaneId, e.getKey(), yeni);
                stok.hareketEkle(c, eczaneId, e.getKey(), -e.getValue(), yeni, "Reçete #" + receteId, eczaci.id(), receteId);
            }
            mesajlar.insert(c, receteId, eczaci.id(), Role.ECZACI, "İlaçlar teslim edildi. Eczacı: " + eczaci.fullName(), true);
        });
        LOG.info(() -> "Reçete #" + receteId + " verildi (eczacı #" + eczaci.id() + ")");
    }

    /** Bekleyen reçeteyi iptal eder. Yazan doktor ya da ilgili eczanenin eczacısı yapabilir. */
    public void iptal(User user, long receteId, String sebep) {
        if (user.is(Role.HASTA)) throw new ServiceException("Hastalar reçete iptal edemez.");
        String neden = Text.optional(sebep, 200, "İptal sebebi");
        db.txRun(c -> {
            Sahiplik s = authorize(c, user, receteId);
            if (s.durum() != ReceteDurum.AKTIF) throw new ServiceException("Yalnızca bekleyen reçeteler iptal edilebilir.");
            if (!receteler.changeDurum(c, receteId, ReceteDurum.AKTIF, ReceteDurum.IPTAL)) {
                throw new ServiceException("Reçete başka bir işlem tarafından değiştirildi. Listeyi yenile.");
            }
            String metin = "Reçete iptal edildi. " + user.role().label() + ": " + user.fullName()
                    + (neden == null ? "" : " — Sebep: " + neden);
            mesajlar.insert(c, receteId, user.id(), user.role(), metin, true);
        });
        LOG.info(() -> "Reçete #" + receteId + " iptal edildi (kullanıcı #" + user.id() + ")");
    }

    public DashboardStats stats(User user) {
        return db.read(c -> {
            List<ReceteOzet> all = receteler.listFor(c, user.role(), ownerId(user));
            int bekleyen = 0, verilen = 0, iptal = 0, okunmamis = 0;
            for (ReceteOzet r : all) {
                switch (r.durum()) {
                    case AKTIF -> bekleyen++;
                    case VERILDI -> verilen++;
                    case IPTAL -> iptal++;
                }
                okunmamis += r.okunmamis();
            }
            int bugun = user.is(Role.ECZACI)
                    ? receteler.verilenBugun(c, user.eczaneId())
                    : receteler.olusturulanBugun(c, user.role(), ownerId(user));
            int kritik = user.is(Role.ECZACI) ? stok.kritikSayisi(c, user.eczaneId()) : 0;
            return new DashboardStats(all.size(), bekleyen, verilen, iptal, okunmamis, bugun, kritik);
        });
    }

    // ---------------------------------------------------------------- yetki

    static long ownerId(User user) {
        if (user.is(Role.ECZACI)) {
            if (user.eczaneId() == null) throw new ServiceException("Eczacı hesabı bir eczaneye bağlı değil.");
            return user.eczaneId();
        }
        return user.id();
    }

    /** Kullanıcı bu reçeteyi görebilir mi? Göremiyorsa hata fırlatır. */
    Sahiplik authorize(Connection c, User user, long receteId) throws SQLException {
        Sahiplik s = receteler.sahiplik(c, receteId).orElseThrow(() -> new ServiceException("Reçete bulunamadı."));
        boolean ok = switch (user.role()) {
            case HASTA -> s.hastaId() == user.id();
            case DOKTOR -> s.doktorId() == user.id();
            case ECZACI -> user.eczaneId() != null && user.eczaneId().equals(s.eczaneId());
        };
        if (!ok) throw new ServiceException("Bu reçeteye erişim yetkin yok.");
        return s;
    }

    static void require(User user, Role role) {
        if (user == null || !user.is(role)) throw new ServiceException("Bu işlem için " + role.label() + " yetkisi gerekir.");
    }
}
