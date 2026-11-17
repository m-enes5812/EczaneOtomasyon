package com.eczane.db;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.concurrent.locks.ReentrantLock;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Tek SQLite bağlantısı üzerinde iş parçacığı güvenli erişim.
 *
 * <p>Masaüstü uygulaması için tek bağlantı hem en hızlı hem en basit çözümdür; tüm erişim bir kilit
 * altında yapılır. {@link #tx} bir bütün olarak ya tamamen uygulanır ya hiç uygulanmaz.
 */
public final class Database implements AutoCloseable {

    private static final Logger LOG = Logger.getLogger(Database.class.getName());

    @FunctionalInterface
    public interface SqlWork<T> {
        T apply(Connection c) throws SQLException;
    }

    private final Path file;
    private final Connection conn;
    private final ReentrantLock lock = new ReentrantLock();

    private Database(Path file, Connection conn) {
        this.file = file;
        this.conn = conn;
    }

    public static Database open(Path file) {
        try {
            Connection c = DriverManager.getConnection("jdbc:sqlite:" + file.toAbsolutePath());
            try (Statement st = c.createStatement()) {
                st.execute("PRAGMA busy_timeout = 5000");
                st.execute("PRAGMA journal_mode = WAL");
                st.execute("PRAGMA synchronous = NORMAL");
                st.execute("PRAGMA foreign_keys = ON");
            }
            Database db = new Database(file, c);
            Migrations.apply(db);
            LOG.info(() -> "Veritabanı açıldı: " + file);
            return db;
        } catch (SQLException e) {
            throw new DataException("Veritabanı açılamadı: " + file, e);
        }
    }

    public Path file() { return file; }

    /** Okuma ya da tek ifadelik yazma işlemleri. */
    public <T> T read(SqlWork<T> work) {
        lock.lock();
        try {
            return work.apply(conn);
        } catch (SQLException e) {
            throw new DataException(e.getMessage(), e);
        } finally {
            lock.unlock();
        }
    }

    /** İşlem (transaction): hata olursa tüm değişiklikler geri alınır. İç içe çağrılar dış işleme katılır. */
    public <T> T tx(SqlWork<T> work) {
        lock.lock();
        try {
            if (!conn.getAutoCommit()) {
                return work.apply(conn); // zaten bir işlemin içindeyiz
            }
            conn.setAutoCommit(false);
            try {
                T result = work.apply(conn);
                conn.commit();
                return result;
            } catch (SQLException | RuntimeException e) {
                rollbackQuietly();
                throw e;
            } finally {
                conn.setAutoCommit(true);
            }
        } catch (SQLException e) {
            throw new DataException(e.getMessage(), e);
        } finally {
            lock.unlock();
        }
    }

    public void txRun(SqlRunnable work) {
        tx(c -> {
            work.run(c);
            return null;
        });
    }

    @FunctionalInterface
    public interface SqlRunnable {
        void run(Connection c) throws SQLException;
    }

    private void rollbackQuietly() {
        try {
            conn.rollback();
        } catch (SQLException e) {
            LOG.log(Level.WARNING, "Geri alma başarısız", e);
        }
    }

    @Override
    public void close() {
        lock.lock();
        try {
            try (Statement st = conn.createStatement()) {
                st.execute("PRAGMA wal_checkpoint(TRUNCATE)");
            } catch (SQLException ignored) {
                // kapanışta önemli değil
            }
            conn.close();
        } catch (SQLException e) {
            LOG.log(Level.WARNING, "Veritabanı kapatılamadı", e);
        } finally {
            lock.unlock();
        }
    }
}
