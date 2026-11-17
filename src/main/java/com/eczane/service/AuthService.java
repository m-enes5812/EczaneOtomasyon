package com.eczane.service;

import com.eczane.db.Database;
import com.eczane.model.Role;
import com.eczane.model.User;
import com.eczane.repo.CatalogRepository;
import com.eczane.repo.UserRepository;
import com.eczane.repo.UserRepository.Credentials;
import com.eczane.security.LoginThrottle;
import com.eczane.security.PasswordHasher;
import com.eczane.security.PasswordPolicy;
import com.eczane.security.TcKimlik;

import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.logging.Logger;

/** Giriş, kayıt ve şifre işlemleri. */
public final class AuthService {

    private static final Logger LOG = Logger.getLogger(AuthService.class.getName());

    public static final List<String> SECURITY_QUESTIONS = List.of(
            "İlkokul öğretmeninin adı neydi?",
            "İlk evcil hayvanının adı neydi?",
            "Çocukluk lakabın neydi?",
            "Annenin kızlık soyadı nedir?",
            "Doğduğun şehir neresidir?");

    /** Kayıt formu. {@code yeniEczaneAdi} doluysa eczacı için yeni eczane oluşturulur. */
    public record RegisterRequest(Role role, String fullName, String tc, String password, String passwordAgain,
                                  Long eczaneId, String yeniEczaneAdi, String securityQuestion, String securityAnswer) {
    }

    private final Database db;
    private final PasswordHasher hasher;
    private final LoginThrottle loginThrottle;
    private final LoginThrottle resetThrottle;
    private final UserRepository users = new UserRepository();
    private final CatalogRepository catalog = new CatalogRepository();
    private final String dummyHash;

    public AuthService(Database db, PasswordHasher hasher, LoginThrottle loginThrottle, LoginThrottle resetThrottle) {
        this.db = db;
        this.hasher = hasher;
        this.loginThrottle = loginThrottle;
        this.resetThrottle = resetThrottle;
        // Hesap yoksa da aynı maliyette karşılaştırma yapılır; yanıt süresinden hesap varlığı anlaşılmaz.
        this.dummyHash = hasher.hash(java.util.UUID.randomUUID().toString());
    }

    public User login(String tc, String password) {
        tc = Text.clean(tc);
        if (!TcKimlik.isWellFormed(tc)) throw new ServiceException("T.C. Kimlik No 11 haneli olmalıdır.");
        ensureNotLocked(loginThrottle, tc);

        final String key = tc;
        Optional<Credentials> cred = db.read(c -> users.credentialsByTc(c, key));
        String stored = cred.map(Credentials::passwordHash).orElse(dummyHash);
        boolean ok = hasher.verify(password == null ? "" : password, stored) && cred.isPresent();
        if (!ok) {
            loginThrottle.recordFailure(tc);
            LOG.info("Başarısız giriş denemesi");
            Duration left = loginThrottle.remainingLock(tc);
            if (!left.isZero()) throw lockedException(left);
            throw new ServiceException("T.C. Kimlik No veya şifre hatalı.");
        }
        loginThrottle.recordSuccess(tc);
        User user = cred.get().user();

        // Eski düz metin ya da düşük tekrarlı özetleri sessizce yükselt.
        String upgraded = hasher.needsRehash(stored) ? hasher.hash(password) : null;
        db.txRun(c -> {
            if (upgraded != null) users.updatePassword(c, user.id(), upgraded);
            users.touchLogin(c, user.id());
        });
        LOG.info(() -> "Giriş yapıldı: kullanıcı #" + user.id() + " (" + user.role().code() + ")");
        return user;
    }

    public User register(RegisterRequest r) {
        if (r.role() == null) throw new ServiceException("Rol seçmelisin.");
        String name = Text.require(r.fullName(), "Ad Soyad", 80);
        if (name.length() < 3 || !name.contains(" ")) throw new ServiceException("Ad ve soyadını birlikte yaz.");
        String tc = Text.clean(r.tc());
        if (!TcKimlik.isValid(tc)) throw new ServiceException("Geçerli bir T.C. Kimlik No gir.");
        PasswordPolicy.check(r.password()).ifPresent(msg -> {
            throw new ServiceException(msg);
        });
        if (!r.password().equals(r.passwordAgain())) throw new ServiceException("Şifreler eşleşmiyor.");
        if (r.securityQuestion() == null || r.securityQuestion().isBlank()) throw new ServiceException("Güvenlik sorusu seç.");
        String answer = Text.require(r.securityAnswer(), "Güvenlik cevabı", 80);
        String yeniEczane = Text.clean(r.yeniEczaneAdi());
        if (r.role() == Role.ECZACI && r.eczaneId() == null && yeniEczane.isEmpty()) {
            throw new ServiceException("Eczacı hesabı için eczane seç ya da yeni eczane adı gir.");
        }
        if (yeniEczane.length() > 80) throw new ServiceException("Eczane adı en fazla 80 karakter olabilir.");

        // Özetleme pahalı; veritabanı kilidini tutmadan önce yapılır.
        String pwHash = hasher.hash(r.password());
        String ansHash = hasher.hashAnswer(answer);

        long id = db.tx(c -> {
            if (users.tcExists(c, tc)) throw new ServiceException("Bu T.C. Kimlik No ile kayıtlı bir hesap zaten var.");
            Long eczaneId = null;
            if (r.role() == Role.ECZACI) {
                if (!yeniEczane.isEmpty()) {
                    eczaneId = catalog.eczaneByName(c, yeniEczane).map(e -> e.id()).orElse(null);
                    if (eczaneId == null) eczaneId = catalog.insertEczane(c, yeniEczane);
                } else {
                    eczaneId = catalog.eczane(c, r.eczaneId())
                            .orElseThrow(() -> new ServiceException("Seçilen eczane bulunamadı.")).id();
                }
            }
            return users.insert(c, r.role(), tc, pwHash, name, eczaneId, r.securityQuestion(), ansHash);
        });
        LOG.info(() -> "Yeni kullanıcı #" + id + " (" + r.role().code() + ")");
        return db.read(c -> users.findById(c, id)).orElseThrow();
    }

    /** Şifre sıfırlama için güvenlik sorusu. */
    public String securityQuestion(String tc) {
        String key = Text.clean(tc);
        if (!TcKimlik.isWellFormed(key)) throw new ServiceException("T.C. Kimlik No 11 haneli olmalıdır.");
        ensureNotLocked(resetThrottle, key);
        Optional<Credentials> cred = db.read(c -> users.credentialsByTc(c, key));
        if (cred.isEmpty()) {
            resetThrottle.recordFailure(key);
            throw new ServiceException("Bu T.C. Kimlik No ile kayıtlı hesap bulunamadı.");
        }
        Credentials cr = cred.get();
        if (cr.securityQuestion() == null || cr.securityAnswerHash() == null) {
            throw new ServiceException("Bu hesapta güvenlik sorusu tanımlı değil. Yöneticinize başvurun.");
        }
        return cr.securityQuestion();
    }

    public void resetPassword(String tc, String answer, String newPassword, String newPasswordAgain) {
        String key = Text.clean(tc);
        if (!TcKimlik.isWellFormed(key)) throw new ServiceException("T.C. Kimlik No 11 haneli olmalıdır.");
        ensureNotLocked(resetThrottle, key);
        Credentials cr = db.read(c -> users.credentialsByTc(c, key))
                .orElseThrow(() -> new ServiceException("Bu T.C. Kimlik No ile kayıtlı hesap bulunamadı."));
        if (!hasher.verifyAnswer(answer, cr.securityAnswerHash())) {
            resetThrottle.recordFailure(key);
            Duration left = resetThrottle.remainingLock(key);
            if (!left.isZero()) throw lockedException(left);
            throw new ServiceException("Güvenlik cevabı yanlış.");
        }
        PasswordPolicy.check(newPassword).ifPresent(msg -> {
            throw new ServiceException(msg);
        });
        if (!newPassword.equals(newPasswordAgain)) throw new ServiceException("Şifreler eşleşmiyor.");
        String hash = hasher.hash(newPassword);
        db.txRun(c -> users.updatePassword(c, cr.user().id(), hash));
        resetThrottle.recordSuccess(key);
        loginThrottle.recordSuccess(key);
        LOG.info(() -> "Şifre sıfırlandı: kullanıcı #" + cr.user().id());
    }

    public void changePassword(User user, String current, String newPassword, String newPasswordAgain) {
        String stored = db.read(c -> users.passwordHash(c, user.id())).orElseThrow();
        if (!hasher.verify(current == null ? "" : current, stored)) throw new ServiceException("Mevcut şifre yanlış.");
        PasswordPolicy.check(newPassword).ifPresent(msg -> {
            throw new ServiceException(msg);
        });
        if (!newPassword.equals(newPasswordAgain)) throw new ServiceException("Yeni şifreler eşleşmiyor.");
        if (newPassword.equals(current)) throw new ServiceException("Yeni şifre mevcut şifreyle aynı olamaz.");
        String hash = hasher.hash(newPassword);
        db.txRun(c -> users.updatePassword(c, user.id(), hash));
    }

    public void changeSecurity(User user, String currentPassword, String question, String answer) {
        String stored = db.read(c -> users.passwordHash(c, user.id())).orElseThrow();
        if (!hasher.verify(currentPassword == null ? "" : currentPassword, stored)) {
            throw new ServiceException("Mevcut şifre yanlış.");
        }
        if (question == null || question.isBlank()) throw new ServiceException("Güvenlik sorusu seç.");
        String a = Text.require(answer, "Güvenlik cevabı", 80);
        String hash = hasher.hashAnswer(a);
        db.txRun(c -> users.updateSecurity(c, user.id(), question, hash));
    }

    public User changeName(User user, String fullName) {
        String name = Text.require(fullName, "Ad Soyad", 80);
        if (!name.contains(" ")) throw new ServiceException("Ad ve soyadını birlikte yaz.");
        db.txRun(c -> users.updateName(c, user.id(), name));
        return db.read(c -> users.findById(c, user.id())).orElseThrow();
    }

    private static void ensureNotLocked(LoginThrottle throttle, String key) {
        Duration left = throttle.remainingLock(key);
        if (!left.isZero()) throw lockedException(left);
    }

    private static ServiceException lockedException(Duration left) {
        long s = Math.max(1, left.toSeconds() + (left.toMillisPart() > 0 ? 1 : 0));
        String sure = s >= 60 ? (s / 60) + " dk " + (s % 60) + " sn" : s + " sn";
        return new ServiceException("Çok fazla hatalı deneme. " + sure + " sonra tekrar dene.");
    }
}
