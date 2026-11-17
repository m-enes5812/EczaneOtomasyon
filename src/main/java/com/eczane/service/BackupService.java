package com.eczane.service;

import com.eczane.db.Database;
import com.eczane.db.Sql;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Stream;

/** Veritabanı yedekleme. SQLite {@code VACUUM INTO} ile tutarlı bir anlık kopya alınır. */
public final class BackupService {

    private static final Logger LOG = Logger.getLogger(BackupService.class.getName());
    private static final DateTimeFormatter NAME = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss");
    private static final int KEEP = 14;

    private final Database db;
    private final Path dir;

    public BackupService(Database db, Path dir) {
        this.db = db;
        this.dir = dir;
    }

    public Path backupTo(Path target) {
        try {
            Files.createDirectories(target.toAbsolutePath().getParent());
            Files.deleteIfExists(target);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        db.read(c -> {
            Sql.update(c, "VACUUM INTO ?", target.toAbsolutePath().toString());
            return null;
        });
        LOG.info(() -> "Yedek alındı: " + target);
        return target;
    }

    public Path backupNow() {
        return backupTo(dir.resolve("eczane_" + LocalDateTime.now().format(NAME) + ".db"));
    }

    /** Günde bir kez otomatik yedek alır ve en yeni {@value #KEEP} yedeği tutar. */
    public void dailyBackup() {
        try {
            String today = LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE);
            boolean exists;
            try (Stream<Path> s = Files.list(dir)) {
                exists = s.anyMatch(p -> p.getFileName().toString().startsWith("eczane_" + today));
            }
            if (!exists) backupNow();
            prune();
        } catch (IOException | RuntimeException e) {
            LOG.log(Level.WARNING, "Otomatik yedek alınamadı", e);
        }
    }

    public List<Path> list() {
        try (Stream<Path> s = Files.list(dir)) {
            return s.filter(p -> p.getFileName().toString().matches("eczane_\\d{8}_\\d{6}\\.db"))
                    .sorted(Comparator.comparing((Path p) -> p.getFileName().toString()).reversed())
                    .toList();
        } catch (IOException e) {
            return List.of();
        }
    }

    public Path directory() { return dir; }

    private void prune() throws IOException {
        List<Path> all = list();
        for (int i = KEEP; i < all.size(); i++) Files.deleteIfExists(all.get(i));
    }
}
