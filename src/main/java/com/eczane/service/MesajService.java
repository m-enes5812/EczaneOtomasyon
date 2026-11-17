package com.eczane.service;

import com.eczane.db.Database;
import com.eczane.model.Mesaj;
import com.eczane.model.User;
import com.eczane.repo.MesajRepository;

import java.util.List;

/** Reçete bazlı mesajlaşma (doktor, hasta ve eczacı arasında). */
public final class MesajService {

    public static final int MAX_LENGTH = 1000;

    private final Database db;
    private final ReceteService receteService;
    private final MesajRepository mesajlar = new MesajRepository();

    public MesajService(Database db, ReceteService receteService) {
        this.db = db;
        this.receteService = receteService;
    }

    /** Mesajları getirir ve kullanıcı için okundu olarak işaretler. */
    public List<Mesaj> list(User user, long receteId) {
        return db.tx(c -> {
            receteService.authorize(c, user, receteId);
            mesajlar.markRead(c, receteId, user.role());
            return mesajlar.list(c, receteId);
        });
    }

    public void send(User user, long receteId, String text) {
        String metin = text == null ? "" : text.strip();
        if (metin.isEmpty()) throw new ServiceException("Boş mesaj gönderilemez.");
        if (metin.length() > MAX_LENGTH) throw new ServiceException("Mesaj en fazla " + MAX_LENGTH + " karakter olabilir.");
        db.txRun(c -> {
            receteService.authorize(c, user, receteId);
            mesajlar.insert(c, receteId, user.id(), user.role(), metin, false);
        });
    }

    public int unreadTotal(User user) {
        return db.read(c -> mesajlar.unreadTotal(c, user.role(), ReceteService.ownerId(user)));
    }
}
