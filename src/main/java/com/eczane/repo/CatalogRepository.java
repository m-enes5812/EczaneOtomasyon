package com.eczane.repo;

import com.eczane.db.Sql;
import com.eczane.model.Eczane;
import com.eczane.model.Ilac;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;

/** Eczane ve ilaç tanımları. */
public final class CatalogRepository {

    public List<Eczane> eczaneler(Connection c) throws SQLException {
        return Sql.list(c, "SELECT id, ad FROM eczaneler ORDER BY ad COLLATE NOCASE",
                rs -> new Eczane(rs.getLong(1), rs.getString(2)));
    }

    public Optional<Eczane> eczane(Connection c, long id) throws SQLException {
        return Sql.one(c, "SELECT id, ad FROM eczaneler WHERE id = ?", rs -> new Eczane(rs.getLong(1), rs.getString(2)), id);
    }

    public Optional<Eczane> eczaneByName(Connection c, String ad) throws SQLException {
        return Sql.one(c, "SELECT id, ad FROM eczaneler WHERE ad = ? COLLATE NOCASE",
                rs -> new Eczane(rs.getLong(1), rs.getString(2)), ad);
    }

    public long insertEczane(Connection c, String ad) throws SQLException {
        return Sql.insert(c, "INSERT INTO eczaneler(ad) VALUES(?)", ad);
    }

    public List<Ilac> ilaclar(Connection c) throws SQLException {
        return Sql.list(c, "SELECT id, ad FROM ilaclar ORDER BY ad COLLATE NOCASE", rs -> new Ilac(rs.getLong(1), rs.getString(2)));
    }

    /** İlacı adına göre bulur (büyük/küçük harf duyarsız), yoksa ekler. */
    public long ilacIdOrCreate(Connection c, String ad) throws SQLException {
        Optional<Long> found = Sql.one(c, "SELECT id FROM ilaclar WHERE ad = ? COLLATE NOCASE ORDER BY id LIMIT 1",
                rs -> rs.getLong(1), ad);
        if (found.isPresent()) return found.get();
        return Sql.insert(c, "INSERT INTO ilaclar(ad) VALUES(?)", ad);
    }
}
