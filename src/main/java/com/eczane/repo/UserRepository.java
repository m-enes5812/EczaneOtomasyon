package com.eczane.repo;

import com.eczane.db.Sql;
import com.eczane.model.Role;
import com.eczane.model.User;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Optional;

public final class UserRepository {

    /** Kimlik doğrulama için gizli alanlarla birlikte kullanıcı. */
    public record Credentials(User user, String passwordHash, String securityQuestion, String securityAnswerHash) {
    }

    private static final String COLS = "id, role, tc, full_name, eczane_id, created_at";

    public Optional<User> findById(Connection c, long id) throws SQLException {
        return Sql.one(c, "SELECT " + COLS + " FROM users WHERE id = ?", UserRepository::map, id);
    }

    public Optional<User> findByTc(Connection c, String tc) throws SQLException {
        return Sql.one(c, "SELECT " + COLS + " FROM users WHERE tc = ? ORDER BY id LIMIT 1", UserRepository::map, tc);
    }

    public Optional<Credentials> credentialsByTc(Connection c, String tc) throws SQLException {
        return Sql.one(c, "SELECT " + COLS + ", password, security_q, security_a_hash FROM users WHERE tc = ? ORDER BY id LIMIT 1",
                rs -> new Credentials(map(rs), rs.getString("password"), rs.getString("security_q"),
                        rs.getString("security_a_hash")), tc);
    }

    public Optional<String> passwordHash(Connection c, long id) throws SQLException {
        return Sql.one(c, "SELECT password FROM users WHERE id = ?", rs -> rs.getString(1), id);
    }

    public boolean tcExists(Connection c, String tc) throws SQLException {
        return Sql.intValue(c, "SELECT COUNT(*) FROM users WHERE tc = ?", tc) > 0;
    }

    public long insert(Connection c, Role role, String tc, String passwordHash, String fullName, Long eczaneId,
                       String securityQ, String securityAHash) throws SQLException {
        return Sql.insert(c, """
                        INSERT INTO users(role, tc, password, full_name, eczane_id, created_at, security_q, security_a_hash)
                        VALUES(?,?,?,?,?,?,?,?)""",
                role.code(), tc, passwordHash, fullName, eczaneId, Sql.now(), securityQ, securityAHash);
    }

    public void updatePassword(Connection c, long id, String hash) throws SQLException {
        Sql.update(c, "UPDATE users SET password = ? WHERE id = ?", hash, id);
    }

    public void updateSecurity(Connection c, long id, String question, String answerHash) throws SQLException {
        Sql.update(c, "UPDATE users SET security_q = ?, security_a_hash = ? WHERE id = ?", question, answerHash, id);
    }

    public void updateName(Connection c, long id, String fullName) throws SQLException {
        Sql.update(c, "UPDATE users SET full_name = ? WHERE id = ?", fullName, id);
    }

    public void touchLogin(Connection c, long id) throws SQLException {
        Sql.update(c, "UPDATE users SET last_login = ? WHERE id = ?", Sql.now(), id);
    }

    public int count(Connection c) throws SQLException {
        return Sql.intValue(c, "SELECT COUNT(*) FROM users");
    }

    static User map(ResultSet rs) throws SQLException {
        return new User(rs.getLong("id"), Role.fromCode(rs.getString("role")), rs.getString("tc"),
                rs.getString("full_name"), Sql.nullableLong(rs, "eczane_id"), Sql.parseTs(rs.getString("created_at")));
    }
}
