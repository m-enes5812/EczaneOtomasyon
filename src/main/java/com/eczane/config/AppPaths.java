package com.eczane.config;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Uygulama veri klasörü. Öncelik sırası:
 * <ol>
 *     <li>{@code -Deczane.home=...} sistem özelliği</li>
 *     <li>{@code ECZANE_HOME} ortam değişkeni</li>
 *     <li>Windows: {@code %LOCALAPPDATA%\EczaneOtomasyon}, diğer: {@code ~/.eczane-otomasyon}</li>
 * </ol>
 */
public record AppPaths(Path home) {

    public static AppPaths detect() {
        String prop = System.getProperty("eczane.home");
        if (prop != null && !prop.isBlank()) return of(Path.of(prop));
        String env = System.getenv("ECZANE_HOME");
        if (env != null && !env.isBlank()) return of(Path.of(env));
        String local = System.getenv("LOCALAPPDATA");
        if (local != null && !local.isBlank()) return of(Path.of(local, "EczaneOtomasyon"));
        return of(Path.of(System.getProperty("user.home"), ".eczane-otomasyon"));
    }

    public static AppPaths of(Path home) {
        AppPaths p = new AppPaths(home.toAbsolutePath());
        try {
            Files.createDirectories(p.home());
            Files.createDirectories(p.logs());
            Files.createDirectories(p.backups());
        } catch (IOException e) {
            throw new UncheckedIOException("Veri klasörü oluşturulamadı: " + home, e);
        }
        return p;
    }

    public Path database() { return home.resolve("eczane.db"); }

    public Path logs() { return home.resolve("logs"); }

    public Path backups() { return home.resolve("yedekler"); }
}
