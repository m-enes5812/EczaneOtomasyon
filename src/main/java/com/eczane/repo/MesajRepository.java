package com.eczane.repo;

import com.eczane.db.Sql;
import com.eczane.model.Mesaj;
import com.eczane.model.Role;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;

public final class MesajRepository {

    /** Eski sürümle uyum için sistem mesajları bu önekle saklanır. */
    public static final String SYSTEM_PREFIX = "[SİSTEM] ";

    /** Rol başına "okundu" sütunu. Sabit listeden seçildiği için SQL'e güvenle eklenebilir. */
    static String readColumn(Role role) {
        return switch (role) {
            case HASTA -> "read_by_hasta";
            case ECZACI -> "read_by_eczaci";
            case DOKTOR -> "read_by_doktor";
        };
    }

    /**
     * Mesaj ekler. Gönderenin kendi rolü için mesaj okunmuş sayılır; sistem mesajları da işlemi yapan
     * kişi için okunmuş sayılır (eski sürümde doktor kendi oluşturduğu reçeteyi "yeni" görüyordu).
     */
    public long insert(Connection c, long receteId, long gonderenId, Role gonderenRol, String metin, boolean sistem)
            throws SQLException {
        return Sql.insert(c, """
                        INSERT INTO recete_mesajlari(recete_id, gonderen_id, mesaj, created_at, is_system,
                                                     read_by_hasta, read_by_eczaci, read_by_doktor)
                        VALUES(?,?,?,?,?,?,?,?)""",
                receteId, gonderenId, sistem ? SYSTEM_PREFIX + metin : metin, Sql.now(), sistem,
                gonderenRol == Role.HASTA, gonderenRol == Role.ECZACI, gonderenRol == Role.DOKTOR);
    }

    public List<Mesaj> list(Connection c, long receteId) throws SQLException {
        return Sql.list(c, """
                        SELECT m.id, m.gonderen_id, u.full_name, u.role, m.mesaj, m.created_at, m.is_system
                        FROM recete_mesajlari m LEFT JOIN users u ON u.id = m.gonderen_id
                        WHERE m.recete_id = ? ORDER BY m.id""",
                rs -> {
                    boolean sistem = rs.getInt("is_system") == 1;
                    String metin = rs.getString("mesaj");
                    if (sistem && metin.startsWith(SYSTEM_PREFIX)) metin = metin.substring(SYSTEM_PREFIX.length());
                    String role = rs.getString("role");
                    return new Mesaj(rs.getLong("id"), rs.getLong("gonderen_id"), rs.getString("full_name"),
                            role == null ? null : Role.fromCode(role), metin, Sql.parseTs(rs.getString("created_at")), sistem);
                }, receteId);
    }

    public void markRead(Connection c, long receteId, Role role) throws SQLException {
        String col = readColumn(role);
        Sql.update(c, "UPDATE recete_mesajlari SET " + col + " = 1 WHERE recete_id = ? AND " + col + " = 0", receteId);
    }

    /** Kullanıcının tüm reçetelerindeki okunmamış mesaj toplamı. */
    public int unreadTotal(Connection c, Role role, long ownerId) throws SQLException {
        String owner = switch (role) {
            case HASTA -> "r.hasta_id";
            case DOKTOR -> "r.doktor_id";
            case ECZACI -> "r.eczane_id";
        };
        return Sql.intValue(c, "SELECT COUNT(*) FROM recete_mesajlari m JOIN receteler r ON r.id = m.recete_id WHERE "
                + owner + " = ? AND m." + readColumn(role) + " = 0", ownerId);
    }
}
