package com.eczane.repo;

import com.eczane.db.Sql;
import com.eczane.model.StokHareketi;
import com.eczane.model.StokKalemi;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;

public final class StokRepository {

    public List<StokKalemi> list(Connection c, long eczaneId) throws SQLException {
        return Sql.list(c, """
                        SELECT i.id, i.ad, s.miktar, s.min_miktar, s.son_guncelleme
                        FROM stoklar s JOIN ilaclar i ON i.id = s.ilac_id
                        WHERE s.eczane_id = ?
                        ORDER BY i.ad COLLATE NOCASE""",
                rs -> new StokKalemi(rs.getLong(1), rs.getString(2), rs.getInt(3), rs.getInt(4), Sql.parseTs(rs.getString(5))),
                eczaneId);
    }

    public int miktar(Connection c, long eczaneId, long ilacId) throws SQLException {
        return Sql.intValue(c, "SELECT miktar FROM stoklar WHERE eczane_id = ? AND ilac_id = ?", eczaneId, ilacId);
    }

    public void setMiktar(Connection c, long eczaneId, long ilacId, int miktar) throws SQLException {
        Sql.update(c, """
                INSERT INTO stoklar(eczane_id, ilac_id, miktar, son_guncelleme) VALUES(?,?,?,?)
                ON CONFLICT(eczane_id, ilac_id) DO UPDATE SET miktar = excluded.miktar, son_guncelleme = excluded.son_guncelleme""",
                eczaneId, ilacId, miktar, Sql.now());
    }

    public void setMin(Connection c, long eczaneId, long ilacId, int min) throws SQLException {
        Sql.update(c, "UPDATE stoklar SET min_miktar = ? WHERE eczane_id = ? AND ilac_id = ?", min, eczaneId, ilacId);
    }

    public int kritikSayisi(Connection c, long eczaneId) throws SQLException {
        return Sql.intValue(c, "SELECT COUNT(*) FROM stoklar WHERE eczane_id = ? AND miktar <= min_miktar", eczaneId);
    }

    public void hareketEkle(Connection c, long eczaneId, long ilacId, int degisim, int yeniMiktar, String sebep,
                            Long userId, Long receteId) throws SQLException {
        Sql.insert(c, """
                        INSERT INTO stok_hareketleri(eczane_id, ilac_id, degisim, yeni_miktar, sebep, user_id, recete_id, created_at)
                        VALUES(?,?,?,?,?,?,?,?)""",
                eczaneId, ilacId, degisim, yeniMiktar, sebep, userId, receteId, Sql.now());
    }

    public List<StokHareketi> hareketler(Connection c, long eczaneId, int limit) throws SQLException {
        return Sql.list(c, """
                        SELECT h.id, h.created_at, i.ad, h.degisim, h.yeni_miktar, h.sebep, u.full_name, h.recete_id
                        FROM stok_hareketleri h
                        JOIN ilaclar i ON i.id = h.ilac_id
                        LEFT JOIN users u ON u.id = h.user_id
                        WHERE h.eczane_id = ?
                        ORDER BY h.id DESC LIMIT ?""",
                rs -> new StokHareketi(rs.getLong(1), Sql.parseTs(rs.getString(2)), rs.getString(3), rs.getInt(4),
                        rs.getInt(5), rs.getString(6), rs.getString(7), Sql.nullableLong(rs, "recete_id")),
                eczaneId, limit);
    }
}
