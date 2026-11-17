package com.eczane.web;

import com.eczane.model.ReceteOzet;
import com.eczane.model.ReceteDurum;
import com.eczane.model.Role;
import com.eczane.model.StokKalemi;
import com.eczane.model.User;
import com.eczane.service.AuthService;
import com.eczane.service.AuthService.RegisterRequest;
import com.eczane.service.MesajService;
import com.eczane.service.ReceteService;
import com.eczane.service.ReceteService.KalemInput;
import com.eczane.service.ServiceException;
import com.eczane.service.Services;
import com.eczane.service.StokService;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.math.BigDecimal;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Eczane Otomasyon web sunucusu: JSON API ({@code /api/...}) ve web sitesinin dosyaları.
 *
 * <p>Masaüstü uygulamasıyla aynı servis katmanını kullanır; tüm iş kuralları ve yetki kontrolleri orada yapılır.
 * Sunucu yalnızca isteği çözer, oturumu bulur ve sonucu JSON olarak döndürür.
 */
public final class WebServer implements AutoCloseable {

    private static final Logger LOG = Logger.getLogger(WebServer.class.getName());
    static final String COOKIE = "eczane_oturum";
    /** Siteler arası istek sahteciliğine (CSRF) karşı: tarayıcılar bu başlığı başka siteden izinsiz gönderemez. */
    static final String CSRF_HEADER = "X-Eczane";
    private static final int MAX_BODY = 64 * 1024;
    private static final String STATIC_ROOT = "/com/eczane/web/";

    private final Services s;
    private final Sessions sessions = new Sessions();
    private final List<Route> routes = new ArrayList<>();
    /** Şifre özetleme pahalıdır; aynı anda yapılan giriş/kayıt sayısı sınırlanır. */
    private final Semaphore authSlots = new Semaphore(4);
    private HttpServer server;

    public WebServer(Services services) {
        this.s = services;
        registerRoutes();
    }

    public void start(String host, int port) throws IOException {
        server = HttpServer.create(new InetSocketAddress(host, port), 64);
        server.setExecutor(Executors.newVirtualThreadPerTaskExecutor());
        server.createContext("/", this::handle);
        server.start();
        LOG.info(() -> "Web sunucusu başladı: " + host + ":" + port());
    }

    public int port() {
        return server.getAddress().getPort();
    }

    @Override
    public void close() {
        if (server != null) server.stop(1);
    }

    // ================================================================ yönlendirme

    @FunctionalInterface
    private interface Handler {
        Object handle(Req r) throws Exception;
    }

    private record Route(String method, Pattern pattern, boolean auth, Handler handler) {
    }

    private void get(String path, Handler h) { add("GET", path, true, h); }

    private void post(String path, Handler h) { add("POST", path, true, h); }

    private void add(String method, String path, boolean auth, Handler h) {
        String regex = path.replace("{id}", "(\\d{1,18})");
        routes.add(new Route(method, Pattern.compile("^" + regex + "$"), auth, h));
    }

    /** İstek bağlamı. */
    private final class Req {
        final HttpExchange ex;
        final List<String> params = new ArrayList<>();
        Map<String, Object> body = Map.of();
        User user;
        String token;

        Req(HttpExchange ex) {
            this.ex = ex;
        }

        long id() { return Long.parseLong(params.getFirst()); }

        String str(String key) {
            Object v = body.get(key);
            return v == null ? null : v instanceof String str ? str : String.valueOf(v);
        }

        int intVal(String key) {
            Object v = body.get(key);
            if (v instanceof BigDecimal b) {
                try {
                    return b.intValueExact();
                } catch (ArithmeticException e) {
                    throw new ServiceException("Geçersiz sayı: " + key);
                }
            }
            if (v instanceof String str && str.strip().matches("[+-]?\\d{1,9}")) return Integer.parseInt(str.strip());
            throw new ServiceException("Geçerli bir sayı gir.");
        }

        Long longOrNull(String key) {
            Object v = body.get(key);
            if (v == null || (v instanceof String str && str.isBlank())) return null;
            if (v instanceof BigDecimal b) return b.longValue();
            if (v instanceof String str && str.matches("\\d{1,18}")) return Long.parseLong(str);
            throw new ServiceException("Geçersiz değer: " + key);
        }

        String query(String key) {
            String q = ex.getRequestURI().getRawQuery();
            if (q == null) return null;
            for (String part : q.split("&")) {
                int eq = part.indexOf('=');
                String k = URLDecoder.decode(eq < 0 ? part : part.substring(0, eq), StandardCharsets.UTF_8);
                if (k.equals(key)) return eq < 0 ? "" : URLDecoder.decode(part.substring(eq + 1), StandardCharsets.UTF_8);
            }
            return null;
        }
    }

    private static final class HttpError extends RuntimeException {
        final int status;

        HttpError(int status, String message) {
            super(message);
            this.status = status;
        }
    }

    private void handle(HttpExchange ex) {
        try (ex) {
            securityHeaders(ex);
            String path = ex.getRequestURI().getPath();
            if (path.startsWith("/api/")) handleApi(ex, path);
            else serveStatic(ex, path);
        } catch (Exception e) {
            LOG.log(Level.WARNING, "İstek işlenemedi", e);
        }
    }

    private void handleApi(HttpExchange ex, String path) throws IOException {
        String method = ex.getRequestMethod();
        Req r = new Req(ex);
        try {
            Route route = null;
            boolean pathMatched = false;
            for (Route rt : routes) {
                Matcher m = rt.pattern.matcher(path);
                if (!m.matches()) continue;
                pathMatched = true;
                if (!rt.method.equals(method)) continue;
                route = rt;
                for (int i = 1; i <= m.groupCount(); i++) r.params.add(m.group(i));
                break;
            }
            if (route == null) throw new HttpError(pathMatched ? 405 : 404, pathMatched ? "Yöntem desteklenmiyor." : "Bulunamadı.");

            if (!method.equals("GET")) {
                if (!"1".equals(ex.getRequestHeaders().getFirst(CSRF_HEADER))) throw new HttpError(403, "İstek reddedildi.");
                r.body = readBody(ex);
            }
            r.token = cookie(ex, COOKIE);
            r.user = sessions.get(r.token).orElse(null);
            if (route.auth && r.user == null) throw new HttpError(401, "Oturumun sona erdi. Lütfen tekrar giriş yap.");

            Object result = route.handler.handle(r);
            sendJson(ex, 200, result == null ? Map.of("ok", true) : result);
        } catch (HttpError e) {
            sendJson(ex, e.status, Map.of("error", e.getMessage()));
        } catch (ServiceException e) {
            sendJson(ex, 400, Map.of("error", e.getMessage()));
        } catch (Exception e) {
            LOG.log(Level.SEVERE, "API hatası: " + method + " " + path, e);
            sendJson(ex, 500, Map.of("error", "Sunucuda beklenmeyen bir hata oluştu. Lütfen tekrar dene."));
        }
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> readBody(HttpExchange ex) throws IOException {
        byte[] data;
        try (InputStream in = ex.getRequestBody()) {
            data = in.readNBytes(MAX_BODY + 1);
        }
        if (data.length > MAX_BODY) throw new HttpError(413, "İstek çok büyük.");
        if (data.length == 0) return Map.of();
        try {
            Object o = Json.parse(new String(data, StandardCharsets.UTF_8));
            if (!(o instanceof Map<?, ?>)) throw new HttpError(400, "Geçersiz istek.");
            return (Map<String, Object>) o;
        } catch (IllegalArgumentException e) {
            throw new HttpError(400, "Geçersiz istek.");
        }
    }

    // ================================================================ API

    private void registerRoutes() {
        // ---- herkese açık
        add("GET", "/api/meta", false, r -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("guvenlikSorulari", AuthService.SECURITY_QUESTIONS);
            m.put("eczaneler", s.stok().eczaneler());
            m.put("girisSebepleri", StokService.GIRIS_SEBEPLERI);
            m.put("cikisSebepleri", StokService.CIKIS_SEBEPLERI);
            m.put("maxKalem", ReceteService.MAX_KALEM);
            m.put("maxAdet", ReceteService.MAX_ADET);
            m.put("mesajMax", MesajService.MAX_LENGTH);
            // Siteyi sunucu bilgisayarından açana, telefonda yazılacak ağ adreslerini göster.
            boolean local = r.ex.getRemoteAddress().getAddress().isLoopbackAddress();
            m.put("agAdresleri", local ? lanAddresses().stream().map(ip -> "http://" + ip + ":" + port()).toList() : List.of());
            return m;
        });

        add("GET", "/api/auth/me", false, r -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("user", r.user == null ? null : userDto(r.user));
            return m;
        });

        add("POST", "/api/auth/login", false, r -> withAuthSlot(() -> {
            User u = s.auth().login(r.str("tc"), r.str("password"));
            startSession(r, u);
            return Map.of("user", userDto(u));
        }));

        add("POST", "/api/auth/register", false, r -> withAuthSlot(() -> {
            Role role = parseRole(r.str("role"));
            User u = s.auth().register(new RegisterRequest(role, r.str("fullName"), r.str("tc"), r.str("password"),
                    r.str("passwordAgain"), r.longOrNull("eczaneId"), r.str("yeniEczaneAdi"),
                    r.str("securityQuestion"), r.str("securityAnswer")));
            startSession(r, u);
            return Map.of("user", userDto(u));
        }));

        add("POST", "/api/auth/logout", false, r -> {
            sessions.remove(r.token);
            r.ex.getResponseHeaders().add("Set-Cookie", COOKIE + "=; Path=/; Max-Age=0; HttpOnly; SameSite=Strict");
            return null;
        });

        add("POST", "/api/auth/forgot/question", false, r -> Map.of("question", s.auth().securityQuestion(r.str("tc"))));

        add("POST", "/api/auth/forgot/reset", false, r -> withAuthSlot(() -> {
            s.auth().resetPassword(r.str("tc"), r.str("answer"), r.str("password"), r.str("passwordAgain"));
            return null;
        }));

        // ---- hesap
        post("/api/hesap/ad", r -> {
            User u = s.auth().changeName(r.user, r.str("fullName"));
            sessions.refresh(u);
            return Map.of("user", userDto(u));
        });
        post("/api/hesap/sifre", r -> withAuthSlot(() -> {
            s.auth().changePassword(r.user, r.str("current"), r.str("password"), r.str("passwordAgain"));
            sessions.removeOthers(r.user.id(), r.token);
            return null;
        }));
        post("/api/hesap/guvenlik", r -> withAuthSlot(() -> {
            s.auth().changeSecurity(r.user, r.str("current"), r.str("question"), r.str("answer"));
            return null;
        }));

        // ---- genel bakış
        get("/api/ozet", r -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("stats", s.recete().stats(r.user));
            List<ReceteOzet> all = s.recete().list(r.user);
            m.put("sonReceteler", all.subList(0, Math.min(6, all.size())));
            if (r.user.is(Role.ECZACI)) {
                m.put("kritikStok", s.stok().list(r.user).stream().filter(StokKalemi::kritik).limit(8)
                        .map(WebServer::stokDto).toList());
            }
            return m;
        });
        get("/api/okunmamis", r -> Map.of("sayi", s.mesaj().unreadTotal(r.user)));

        // ---- reçeteler
        get("/api/receteler", r -> s.recete().list(r.user));
        get("/api/receteler/{id}", r -> {
            Map<String, Object> m = new LinkedHashMap<>();
            var d = s.recete().detay(r.user, r.id());
            m.put("detay", d);
            if (r.user.is(Role.ECZACI) && d.durum() == ReceteDurum.AKTIF) {
                m.put("stok", s.recete().stokDurumu(r.user, r.id()).stream().map(k -> Map.of(
                        "ilacAdi", k.kalem().ilacAdi(), "istenen", k.kalem().adet(),
                        "stok", k.stok(), "yeterli", k.yeterli())).toList());
            }
            m.put("iptalEdilebilir", d.durum() == ReceteDurum.AKTIF && !r.user.is(Role.HASTA));
            m.put("verilebilir", d.durum() == ReceteDurum.AKTIF && r.user.is(Role.ECZACI));
            return m;
        });
        post("/api/receteler", r -> {
            List<KalemInput> kalemler = new ArrayList<>();
            if (r.body.get("kalemler") instanceof List<?> list) {
                for (Object o : list) {
                    if (!(o instanceof Map<?, ?> k)) throw new ServiceException("Geçersiz ilaç satırı.");
                    Object adet = k.get("adet");
                    int a = adet instanceof BigDecimal b ? b.intValue()
                            : adet instanceof String str && str.strip().matches("\\d{1,3}") ? Integer.parseInt(str.strip()) : 0;
                    kalemler.add(new KalemInput(asString(k.get("ilacAdi")), a, asString(k.get("kullanim"))));
                }
            }
            long id = s.recete().create(r.user, r.str("hastaTc"), r.longOrNull("eczaneId"), r.str("aciklama"), kalemler);
            return Map.of("id", id);
        });
        post("/api/receteler/{id}/ver", r -> {
            s.recete().ver(r.user, r.id());
            return null;
        });
        post("/api/receteler/{id}/iptal", r -> {
            s.recete().iptal(r.user, r.id(), r.str("sebep"));
            return null;
        });
        get("/api/receteler/{id}/mesajlar", r -> s.mesaj().list(r.user, r.id()));
        post("/api/receteler/{id}/mesajlar", r -> {
            s.mesaj().send(r.user, r.id(), r.str("metin"));
            return s.mesaj().list(r.user, r.id());
        });
        get("/api/hasta", r -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("hasta", s.recete().findHasta(r.user, r.query("tc"))
                    .map(u -> Map.of("fullName", u.fullName(), "maskedTc", u.maskedTc())).orElse(null));
            return m;
        });
        get("/api/ilaclar", r -> s.stok().ilaclar());

        // ---- stok (eczacı)
        get("/api/stok", r -> s.stok().list(r.user).stream().map(WebServer::stokDto).toList());
        post("/api/stok/hareket", r -> {
            int yeni = s.stok().adjust(r.user, r.str("ilacAdi"), r.intVal("delta"), r.str("sebep"));
            return Map.of("yeniMiktar", yeni);
        });
        post("/api/stok/{id}/min", r -> {
            s.stok().setMin(r.user, r.id(), r.intVal("min"));
            return null;
        });
        get("/api/stok/hareketler", r -> s.stok().hareketler(r.user, 300));
    }

    private interface AuthWork {
        Object run() throws Exception;
    }

    private Object withAuthSlot(AuthWork work) throws Exception {
        if (!authSlots.tryAcquire(10, TimeUnit.SECONDS)) throw new HttpError(503, "Sunucu şu an yoğun. Biraz sonra tekrar dene.");
        try {
            return work.run();
        } finally {
            authSlots.release();
        }
    }

    private void startSession(Req r, User u) {
        sessions.remove(r.token); // oturum sabitleme saldırısına karşı her girişte yeni anahtar
        String token = sessions.create(u);
        boolean https = "https".equalsIgnoreCase(r.ex.getRequestHeaders().getFirst("X-Forwarded-Proto"));
        r.ex.getResponseHeaders().add("Set-Cookie", COOKIE + "=" + token + "; Path=/; HttpOnly; SameSite=Strict"
                + "; Max-Age=" + Sessions.MAX_AGE.toSeconds() + (https ? "; Secure" : ""));
    }

    private Map<String, Object> userDto(User u) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", u.id());
        m.put("role", u.role());
        m.put("roleLabel", u.role().label());
        m.put("fullName", u.fullName());
        m.put("maskedTc", u.maskedTc());
        m.put("initials", u.initials());
        m.put("eczaneId", u.eczaneId());
        m.put("eczaneAdi", s.stok().eczaneAdi(u.eczaneId()));
        m.put("createdAt", u.createdAt());
        return m;
    }

    private static Map<String, Object> stokDto(StokKalemi k) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("ilacId", k.ilacId());
        m.put("ilacAdi", k.ilacAdi());
        m.put("miktar", k.miktar());
        m.put("minMiktar", k.minMiktar());
        m.put("sonGuncelleme", k.sonGuncelleme());
        m.put("kritik", k.kritik());
        m.put("tukendi", k.tukendi());
        return m;
    }

    private static Role parseRole(String code) {
        if (code == null) throw new ServiceException("Rol seçmelisin.");
        try {
            return Role.fromCode(code);
        } catch (IllegalArgumentException e) {
            try {
                return Role.valueOf(code.toUpperCase(java.util.Locale.ROOT));
            } catch (IllegalArgumentException e2) {
                throw new ServiceException("Rol seçmelisin.");
            }
        }
    }

    private static String asString(Object o) {
        return o == null ? null : String.valueOf(o);
    }

    /** Bu bilgisayarın yerel ağ (Wi-Fi/Ethernet) IPv4 adresleri. */
    static List<String> lanAddresses() {
        List<String> out = new ArrayList<>();
        try {
            for (java.net.NetworkInterface ni : java.net.NetworkInterface.networkInterfaces().toList()) {
                if (!ni.isUp() || ni.isLoopback() || ni.isVirtual()) continue;
                String n = (ni.getDisplayName() + " " + ni.getName()).toLowerCase(java.util.Locale.ROOT);
                boolean sanal = n.contains("virtualbox") || n.contains("vmware") || n.contains("hyper-v") || n.contains("vethernet");
                ni.inetAddresses().filter(a -> a instanceof java.net.Inet4Address && a.isSiteLocalAddress())
                        .forEach(a -> { if (sanal) out.addLast(a.getHostAddress()); else out.addFirst(a.getHostAddress()); });
            }
        } catch (Exception ignored) {
            // yalnızca bilgi amaçlı
        }
        return out;
    }

    // ================================================================ yanıt yardımcıları

    private static void securityHeaders(HttpExchange ex) {
        var h = ex.getResponseHeaders();
        h.set("X-Content-Type-Options", "nosniff");
        h.set("X-Frame-Options", "DENY");
        h.set("Referrer-Policy", "no-referrer");
        h.set("Content-Security-Policy", "default-src 'self'; img-src 'self' data:; style-src 'self'; script-src 'self'; "
                + "connect-src 'self'; frame-ancestors 'none'; base-uri 'none'; form-action 'self'");
        h.set("Permissions-Policy", "camera=(), microphone=(), geolocation=()");
    }

    private static void sendJson(HttpExchange ex, int status, Object body) throws IOException {
        byte[] data = Json.write(body).getBytes(StandardCharsets.UTF_8);
        ex.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
        ex.getResponseHeaders().set("Cache-Control", "no-store");
        ex.sendResponseHeaders(status, data.length);
        try (OutputStream out = ex.getResponseBody()) {
            out.write(data);
        }
    }

    private static String cookie(HttpExchange ex, String name) {
        for (String header : ex.getRequestHeaders().getOrDefault("Cookie", List.of())) {
            for (String part : header.split(";")) {
                String p = part.strip();
                if (p.startsWith(name + "=")) return p.substring(name.length() + 1);
            }
        }
        return null;
    }

    private static final Map<String, String> TYPES = Map.of(
            "html", "text/html; charset=utf-8",
            "css", "text/css; charset=utf-8",
            "js", "text/javascript; charset=utf-8",
            "png", "image/png",
            "svg", "image/svg+xml",
            "ico", "image/x-icon",
            "webmanifest", "application/manifest+json");

    private void serveStatic(HttpExchange ex, String path) throws IOException {
        String method = ex.getRequestMethod();
        if (!method.equals("GET") && !method.equals("HEAD")) {
            ex.sendResponseHeaders(405, -1);
            return;
        }
        String name = path.equals("/") ? "index.html" : path.substring(1);
        // Yalnızca düz dosya adları: klasör gezme (../) mümkün değil.
        if (!name.matches("[a-z0-9][a-z0-9_.-]{0,60}") || name.contains("..")) name = "index.html";
        String ext = name.substring(name.lastIndexOf('.') + 1);
        String type = TYPES.get(ext);
        InputStream in = type == null ? null : WebServer.class.getResourceAsStream(STATIC_ROOT + name);
        if (in == null) {
            // Bilinmeyen sayfa: tek sayfalık uygulamaya yönlendir.
            name = "index.html";
            type = TYPES.get("html");
            in = WebServer.class.getResourceAsStream(STATIC_ROOT + name);
            if (in == null) {
                ex.sendResponseHeaders(404, -1);
                return;
            }
        }
        byte[] data;
        try (InputStream res = in) {
            data = res.readAllBytes();
        }
        ex.getResponseHeaders().set("Content-Type", type);
        boolean fresh = name.endsWith(".html") || name.equals("sw.js") || name.endsWith(".webmanifest");
        ex.getResponseHeaders().set("Cache-Control", fresh ? "no-cache" : "public, max-age=300");
        if (method.equals("HEAD")) {
            ex.sendResponseHeaders(200, -1);
            return;
        }
        ex.sendResponseHeaders(200, data.length);
        try (OutputStream out = ex.getResponseBody()) {
            out.write(data);
        }
    }
}
