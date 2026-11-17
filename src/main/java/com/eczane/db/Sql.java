package com.eczane.db;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Types;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/** Küçük JDBC yardımcıları. Tarihler eski sürümle uyumlu "yyyy-MM-dd HH:mm:ss" metni olarak saklanır. */
public final class Sql {

    public static final DateTimeFormatter TS = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private Sql() {
    }

    @FunctionalInterface
    public interface RowMapper<T> {
        T map(ResultSet rs) throws SQLException;
    }

    public static String now() {
        return LocalDateTime.now().format(TS);
    }

    public static LocalDateTime parseTs(String s) {
        if (s == null || s.isBlank()) return null;
        try {
            return LocalDateTime.parse(s.length() > 19 ? s.substring(0, 19) : s, TS);
        } catch (DateTimeParseException e) {
            return null;
        }
    }

    public static <T> List<T> list(Connection c, String sql, RowMapper<T> mapper, Object... params) throws SQLException {
        try (PreparedStatement ps = prepare(c, sql, params); ResultSet rs = ps.executeQuery()) {
            List<T> out = new ArrayList<>();
            while (rs.next()) out.add(mapper.map(rs));
            return out;
        }
    }

    public static <T> Optional<T> one(Connection c, String sql, RowMapper<T> mapper, Object... params) throws SQLException {
        try (PreparedStatement ps = prepare(c, sql, params); ResultSet rs = ps.executeQuery()) {
            return rs.next() ? Optional.ofNullable(mapper.map(rs)) : Optional.empty();
        }
    }

    public static int intValue(Connection c, String sql, Object... params) throws SQLException {
        return one(c, sql, rs -> rs.getInt(1), params).orElse(0);
    }

    public static int update(Connection c, String sql, Object... params) throws SQLException {
        try (PreparedStatement ps = prepare(c, sql, params)) {
            return ps.executeUpdate();
        }
    }

    /** INSERT çalıştırır ve oluşan satır kimliğini döner. */
    public static long insert(Connection c, String sql, Object... params) throws SQLException {
        try (PreparedStatement ps = prepare(c, sql, params)) {
            ps.executeUpdate();
        }
        try (Statement st = c.createStatement(); ResultSet rs = st.executeQuery("SELECT last_insert_rowid()")) {
            rs.next();
            return rs.getLong(1);
        }
    }

    public static void exec(Connection c, String sql) throws SQLException {
        try (Statement st = c.createStatement()) {
            st.execute(sql);
        }
    }

    public static Set<String> columns(Connection c, String table) throws SQLException {
        Set<String> cols = new HashSet<>();
        try (Statement st = c.createStatement(); ResultSet rs = st.executeQuery("PRAGMA table_info(" + table + ")")) {
            while (rs.next()) cols.add(rs.getString("name").toLowerCase());
        }
        return cols;
    }

    public static boolean ensureColumn(Connection c, String table, String columnDef) throws SQLException {
        String name = columnDef.trim().split("\\s+")[0].toLowerCase();
        if (columns(c, table).contains(name)) return false;
        exec(c, "ALTER TABLE " + table + " ADD COLUMN " + columnDef);
        return true;
    }

    public static Long nullableLong(ResultSet rs, String col) throws SQLException {
        long v = rs.getLong(col);
        return rs.wasNull() ? null : v;
    }

    private static PreparedStatement prepare(Connection c, String sql, Object... params) throws SQLException {
        PreparedStatement ps = c.prepareStatement(sql);
        try {
            for (int i = 0; i < params.length; i++) {
                Object p = params[i];
                if (p == null) ps.setNull(i + 1, Types.NULL);
                else if (p instanceof Boolean b) ps.setInt(i + 1, b ? 1 : 0);
                else ps.setObject(i + 1, p);
            }
            return ps;
        } catch (SQLException e) {
            ps.close();
            throw e;
        }
    }
}
