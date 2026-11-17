package com.eczane.db;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;
import java.util.logging.Logger;

/**
 * Şema sürümleri ({@code PRAGMA user_version}).
 *
 * <p>Sürüm 1, eski Python uygulamasının şemasıyla aynıdır; mevcut bir veritabanı açıldığında hiçbir şey
 * silinmez, yalnızca eksik tablo/sütunlar eklenir. Bu yüzden eski uygulama da aynı dosyayı okuyabilir.
 */
final class Migrations {

    private static final Logger LOG = Logger.getLogger(Migrations.class.getName());

    @FunctionalInterface
    private interface Step {
        void apply(Connection c) throws SQLException;
    }

    private static final List<Step> STEPS = List.of(Migrations::v1LegacySchema, Migrations::v2Modern);

    private Migrations() {
    }

    static void apply(Database db) {
        db.txRun(c -> {
            int current = Sql.intValue(c, "PRAGMA user_version");
            for (int v = current; v < STEPS.size(); v++) {
                STEPS.get(v).apply(c);
                Sql.exec(c, "PRAGMA user_version = " + (v + 1));
                int target = v + 1;
                LOG.info(() -> "Şema sürümü " + target + " uygulandı");
            }
        });
    }

    /** Python sürümündeki init_db() ile aynı. */
    private static void v1LegacySchema(Connection c) throws SQLException {
        Sql.exec(c, """
                CREATE TABLE IF NOT EXISTS users(
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    role TEXT NOT NULL CHECK(role IN ('doktor','eczaci','hasta')),
                    tc TEXT,
                    password TEXT NOT NULL,
                    full_name TEXT,
                    eczane_id INTEGER,
                    created_at TEXT
                )""");
        boolean hadUsername = Sql.columns(c, "users").contains("username");
        Sql.ensureColumn(c, "users", "tc TEXT");
        Sql.ensureColumn(c, "users", "security_q TEXT");
        Sql.ensureColumn(c, "users", "security_a_hash TEXT");
        if (hadUsername) {
            Sql.exec(c, "UPDATE users SET tc = COALESCE(tc, username) WHERE tc IS NULL OR TRIM(tc) = ''");
        }

        Sql.exec(c, "CREATE TABLE IF NOT EXISTS eczaneler(id INTEGER PRIMARY KEY AUTOINCREMENT, ad TEXT UNIQUE NOT NULL)");
        Sql.exec(c, "CREATE TABLE IF NOT EXISTS ilaclar(id INTEGER PRIMARY KEY AUTOINCREMENT, ad TEXT UNIQUE NOT NULL)");
        Sql.exec(c, """
                CREATE TABLE IF NOT EXISTS stoklar(
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    eczane_id INTEGER NOT NULL,
                    ilac_id INTEGER NOT NULL,
                    miktar INTEGER NOT NULL DEFAULT 0,
                    UNIQUE(eczane_id, ilac_id)
                )""");
        Sql.exec(c, """
                CREATE TABLE IF NOT EXISTS receteler(
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    hasta_id INTEGER NOT NULL,
                    doktor_id INTEGER NOT NULL,
                    eczane_id INTEGER,
                    durum TEXT NOT NULL DEFAULT 'AKTIF' CHECK(durum IN ('AKTIF','VERILDI','IPTAL')),
                    aciklama TEXT,
                    created_at TEXT
                )""");
        Sql.exec(c, """
                CREATE TABLE IF NOT EXISTS recete_kalemleri(
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    recete_id INTEGER NOT NULL,
                    ilac_id INTEGER NOT NULL,
                    adet INTEGER NOT NULL,
                    kullanim TEXT
                )""");
        Sql.exec(c, """
                CREATE TABLE IF NOT EXISTS recete_mesajlari(
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    recete_id INTEGER NOT NULL,
                    gonderen_id INTEGER NOT NULL,
                    mesaj TEXT NOT NULL,
                    created_at TEXT
                )""");
        Sql.ensureColumn(c, "recete_mesajlari", "is_system INTEGER NOT NULL DEFAULT 0");
        Sql.ensureColumn(c, "recete_mesajlari", "read_by_hasta INTEGER NOT NULL DEFAULT 0");
        Sql.ensureColumn(c, "recete_mesajlari", "read_by_eczaci INTEGER NOT NULL DEFAULT 0");
        Sql.ensureColumn(c, "recete_mesajlari", "read_by_doktor INTEGER NOT NULL DEFAULT 0");

        Sql.update(c, "INSERT OR IGNORE INTO eczaneler(ad) VALUES(?)", "Merkez Eczanesi");
    }

    /** Java sürümüyle gelen eklemeler: stok eşiği, stok hareket kaydı, indeksler. */
    private static void v2Modern(Connection c) throws SQLException {
        Sql.ensureColumn(c, "stoklar", "min_miktar INTEGER NOT NULL DEFAULT 10");
        Sql.ensureColumn(c, "stoklar", "son_guncelleme TEXT");
        Sql.ensureColumn(c, "users", "last_login TEXT");
        Sql.ensureColumn(c, "receteler", "updated_at TEXT");

        Sql.exec(c, """
                CREATE TABLE IF NOT EXISTS stok_hareketleri(
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    eczane_id INTEGER NOT NULL,
                    ilac_id INTEGER NOT NULL,
                    degisim INTEGER NOT NULL,
                    yeni_miktar INTEGER NOT NULL,
                    sebep TEXT NOT NULL,
                    user_id INTEGER,
                    recete_id INTEGER,
                    created_at TEXT NOT NULL
                )""");

        Sql.exec(c, "CREATE INDEX IF NOT EXISTS ix_users_tc ON users(tc)");
        Sql.exec(c, "CREATE INDEX IF NOT EXISTS ix_receteler_hasta ON receteler(hasta_id)");
        Sql.exec(c, "CREATE INDEX IF NOT EXISTS ix_receteler_doktor ON receteler(doktor_id)");
        Sql.exec(c, "CREATE INDEX IF NOT EXISTS ix_receteler_eczane ON receteler(eczane_id, durum)");
        Sql.exec(c, "CREATE INDEX IF NOT EXISTS ix_kalemler_recete ON recete_kalemleri(recete_id)");
        Sql.exec(c, "CREATE INDEX IF NOT EXISTS ix_mesajlar_recete ON recete_mesajlari(recete_id)");
        Sql.exec(c, "CREATE INDEX IF NOT EXISTS ix_hareket_eczane ON stok_hareketleri(eczane_id, created_at)");
    }
}
