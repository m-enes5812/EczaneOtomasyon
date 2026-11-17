package com.eczane.repo;

import com.eczane.db.Sql;
import com.eczane.model.ReceteDetay;
import com.eczane.model.ReceteDurum;
import com.eczane.model.ReceteKalemi;
import com.eczane.model.ReceteOzet;
import com.eczane.model.Role;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;

public final class ReceteRepository {

    /** Yetki kontrolü için reçetenin sahipleri. */
    public record Sahiplik(long hastaId, long doktorId, Long eczaneId, ReceteDurum durum) {
    }

    public long insert(Connection c, long hastaId, long doktorId, long eczaneId, String aciklama) throws SQLException {
        String now = Sql.now();
        return Sql.insert(c, """
                        INSERT INTO receteler(hasta_id, doktor_id, eczane_id, durum, aciklama, created_at, updated_at)
                        VALUES(?,?,?,'AKTIF',?,?,?)""",
                hastaId, doktorId, eczaneId, aciklama, now, now);
    }

    public void insertKalem(Connection c, long receteId, long ilacId, int adet, String kullanim) throws SQLException {
        Sql.insert(c, "INSERT INTO recete_kalemleri(recete_id, ilac_id, adet, kullanim) VALUES(?,?,?,?)",
                receteId, ilacId, adet, kullanim);
    }

    /**
     * Durumu yalnızca beklenen durumdaysa değiştirir (iyimser kilit). Aynı reçete iki kez verilemez.
     *
     * @return değişiklik yapıldıysa true
     */
    public boolean changeDurum(Connection c, long id, ReceteDurum from, ReceteDurum to) throws SQLException {
        return Sql.update(c, "UPDATE receteler SET durum = ?, updated_at = ? WHERE id = ? AND durum = ?",
                to.code(), Sql.now(), id, from.code()) == 1;
    }

    public Optional<Sahiplik> sahiplik(Connection c, long id) throws SQLException {
        return Sql.one(c, "SELECT hasta_id, doktor_id, eczane_id, durum FROM receteler WHERE id = ?",
                rs -> new Sahiplik(rs.getLong(1), rs.getLong(2), Sql.nullableLong(rs, "eczane_id"),
                        ReceteDurum.fromCode(rs.getString(4))), id);
    }

    public List<ReceteOzet> listFor(Connection c, Role role, long ownerId) throws SQLException {
        String where = switch (role) {
            case HASTA -> "r.hasta_id = ?";
            case DOKTOR -> "r.doktor_id = ?";
            case ECZACI -> "r.eczane_id = ?";
        };
        String sql = """
                SELECT r.id, r.durum, r.aciklama, r.created_at,
                       hu.full_name AS hasta, du.full_name AS doktor, e.ad AS eczane,
                       (SELECT COUNT(*) FROM recete_kalemleri k WHERE k.recete_id = r.id) AS kalem,
                       (SELECT COUNT(*) FROM recete_mesajlari m WHERE m.recete_id = r.id AND m.%s = 0) AS okunmamis
                FROM receteler r
                LEFT JOIN users hu ON hu.id = r.hasta_id
                LEFT JOIN users du ON du.id = r.doktor_id
                LEFT JOIN eczaneler e ON e.id = r.eczane_id
                WHERE %s
                ORDER BY r.id DESC""".formatted(MesajRepository.readColumn(role), where);
        return Sql.list(c, sql, rs -> new ReceteOzet(rs.getLong("id"), ReceteDurum.fromCode(rs.getString("durum")),
                rs.getString("aciklama"), Sql.parseTs(rs.getString("created_at")), rs.getString("hasta"),
                rs.getString("doktor"), rs.getString("eczane"), rs.getInt("kalem"), rs.getInt("okunmamis")), ownerId);
    }

    public Optional<ReceteDetay> detay(Connection c, long id) throws SQLException {
        List<ReceteKalemi> kalemler = Sql.list(c, """
                        SELECT i.id, i.ad, k.adet, k.kullanim
                        FROM recete_kalemleri k JOIN ilaclar i ON i.id = k.ilac_id
                        WHERE k.recete_id = ? ORDER BY k.id""",
                rs -> new ReceteKalemi(rs.getLong(1), rs.getString(2), rs.getInt(3), rs.getString(4)), id);
        return Sql.one(c, """
                        SELECT r.id, r.durum, r.aciklama, r.created_at,
                               r.hasta_id, hu.full_name AS hasta, hu.tc AS hasta_tc,
                               r.doktor_id, du.full_name AS doktor,
                               r.eczane_id, e.ad AS eczane
                        FROM receteler r
                        LEFT JOIN users hu ON hu.id = r.hasta_id
                        LEFT JOIN users du ON du.id = r.doktor_id
                        LEFT JOIN eczaneler e ON e.id = r.eczane_id
                        WHERE r.id = ?""",
                rs -> new ReceteDetay(rs.getLong("id"), ReceteDurum.fromCode(rs.getString("durum")), rs.getString("aciklama"),
                        Sql.parseTs(rs.getString("created_at")), rs.getLong("hasta_id"), rs.getString("hasta"),
                        rs.getString("hasta_tc"), rs.getLong("doktor_id"), rs.getString("doktor"),
                        Sql.nullableLong(rs, "eczane_id"), rs.getString("eczane"), kalemler), id);
    }

    /** Bugün verilen reçete sayısı (eczane için). */
    public int verilenBugun(Connection c, long eczaneId) throws SQLException {
        return Sql.intValue(c, """
                SELECT COUNT(*) FROM receteler
                WHERE eczane_id = ? AND durum = 'VERILDI' AND substr(COALESCE(updated_at, created_at), 1, 10) = date('now', 'localtime')""",
                eczaneId);
    }

    public int olusturulanBugun(Connection c, Role role, long ownerId) throws SQLException {
        String col = switch (role) {
            case HASTA -> "hasta_id";
            case DOKTOR -> "doktor_id";
            case ECZACI -> "eczane_id";
        };
        return Sql.intValue(c, "SELECT COUNT(*) FROM receteler WHERE " + col
                + " = ? AND substr(created_at, 1, 10) = date('now', 'localtime')", ownerId);
    }
}
