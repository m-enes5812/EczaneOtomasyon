package com.eczane.service;

import com.eczane.config.AppPaths;
import com.eczane.db.Database;
import com.eczane.security.LoginThrottle;
import com.eczane.security.PasswordHasher;

/** Uygulamadaki tüm servislerin tek noktadan oluşturulması. */
public record Services(AppPaths paths, Database db, AuthService auth, ReceteService recete, MesajService mesaj,
                       StokService stok, BackupService backup) implements AutoCloseable {

    public static Services create(AppPaths paths, Database db, PasswordHasher hasher) {
        ReceteService recete = new ReceteService(db);
        return new Services(paths, db,
                new AuthService(db, hasher, new LoginThrottle(), new LoginThrottle()),
                recete,
                new MesajService(db, recete),
                new StokService(db),
                new BackupService(db, paths.backups()));
    }

    @Override
    public void close() {
        db.close();
    }
}
