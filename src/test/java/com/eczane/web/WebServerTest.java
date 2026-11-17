package com.eczane.web;

import com.eczane.config.AppPaths;
import com.eczane.db.Database;
import com.eczane.security.PasswordHasher;
import com.eczane.service.Services;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class WebServerTest {

    @TempDir
    Path tmp;
    Services s;
    WebServer web;
    String base;

    @BeforeEach
    void setUp() throws Exception {
        AppPaths paths = AppPaths.of(tmp.resolve("data"));
        s = Services.create(paths, Database.open(paths.database()), new PasswordHasher(1000));
        web = new WebServer(s);
        web.start("127.0.0.1", 0);
        base = "http://127.0.0.1:" + web.port();
    }

    @AfterEach
    void tearDown() {
        web.close();
        s.close();
    }

    /** Kendi çerez kutusu olan bir tarayıcı gibi davranır. */
    final class Browser {
        final HttpClient http = HttpClient.newHttpClient();
        String cookie;

        HttpResponse<String> send(String method, String path, Object body, boolean csrf) throws Exception {
            HttpRequest.Builder b = HttpRequest.newBuilder(URI.create(base + path));
            if (cookie != null) b.header("Cookie", cookie);
            if (method.equals("GET")) b.GET();
            else {
                b.header("Content-Type", "application/json");
                if (csrf) b.header("X-Eczane", "1");
                b.method(method, HttpRequest.BodyPublishers.ofString(Json.write(body == null ? Map.of() : body)));
            }
            HttpResponse<String> r = http.send(b.build(), HttpResponse.BodyHandlers.ofString());
            r.headers().firstValue("Set-Cookie").ifPresent(c -> {
                String v = c.substring(0, c.indexOf(';'));
                cookie = v.endsWith("=") ? null : v;
            });
            return r;
        }

        Object ok(String method, String path, Object body) throws Exception {
            HttpResponse<String> r = send(method, path, body, true);
            assertEquals(200, r.statusCode(), () -> method + " " + path + " → " + r.body());
            return Json.parse(r.body());
        }

        String error(String method, String path, Object body, int status) throws Exception {
            HttpResponse<String> r = send(method, path, body, true);
            assertEquals(status, r.statusCode(), r::body);
            return (String) ((Map<?, ?>) Json.parse(r.body())).get("error");
        }

        Map<?, ?> register(String role, String name, String tc, Long eczaneId) throws Exception {
            Map<String, Object> body = new java.util.HashMap<>(Map.of("role", role, "fullName", name, "tc", tc,
                    "password", "Eczane2026", "passwordAgain", "Eczane2026",
                    "securityQuestion", "Doğduğun şehir neresidir?", "securityAnswer", "İzmir"));
            if (eczaneId != null) body.put("eczaneId", eczaneId);
            return (Map<?, ?>) ((Map<?, ?>) ok("POST", "/api/auth/register", body)).get("user");
        }
    }

    @Test
    void tamAkis() throws Exception {
        Browser doktor = new Browser(), hasta = new Browser(), eczaci = new Browser(), yabanci = new Browser();

        Map<?, ?> meta = (Map<?, ?>) doktor.ok("GET", "/api/meta", null);
        long merkez = ((BigDecimal) ((Map<?, ?>) ((List<?>) meta.get("eczaneler")).getFirst()).get("id")).longValue();

        assertEquals("DOKTOR", doktor.register("doktor", "Ayşe Yılmaz", "10000000146", null).get("role"));
        hasta.register("hasta", "Mehmet Demir", "12345678950", null);
        Map<?, ?> ecz = eczaci.register("eczaci", "Zeynep Kaya", "11111111110", merkez);
        assertEquals("Merkez Eczanesi", ecz.get("eczaneAdi"));
        yabanci.register("hasta", "Ali Can", "22222222220", null);

        // Oturum çerezi çalışıyor
        assertEquals("Ayşe Yılmaz", ((Map<?, ?>) ((Map<?, ?>) doktor.ok("GET", "/api/auth/me", null)).get("user")).get("fullName"));

        // Hasta doğrulama ve reçete yazma
        Map<?, ?> h = (Map<?, ?>) ((Map<?, ?>) doktor.ok("GET", "/api/hasta?tc=12345678950", null)).get("hasta");
        assertEquals("Mehmet Demir", h.get("fullName"));
        long id = ((BigDecimal) ((Map<?, ?>) doktor.ok("POST", "/api/receteler", Map.of(
                "hastaTc", "12345678950", "eczaneId", merkez, "aciklama", "Kontrol",
                "kalemler", List.of(Map.of("ilacAdi", "Parol 500 mg", "adet", 2, "kullanim", "Günde 2 kez 1 adet"))))).get("id")).longValue();

        // Yetki: başka hasta göremez, hasta yazamaz
        assertTrue(yabanci.error("GET", "/api/receteler/" + id, null, 400).contains("yetkin yok"));
        assertTrue(hasta.error("POST", "/api/receteler", Map.of("hastaTc", "12345678950"), 400).contains("Doktor"));

        // Eczacı: stok yok → teslim edilemez
        Map<?, ?> det = (Map<?, ?>) eczaci.ok("GET", "/api/receteler/" + id, null);
        assertEquals(Boolean.FALSE, ((Map<?, ?>) ((List<?>) det.get("stok")).getFirst()).get("yeterli"));
        assertTrue(eczaci.error("POST", "/api/receteler/" + id + "/ver", null, 400).contains("Stok yetersiz"));

        eczaci.ok("POST", "/api/stok/hareket", Map.of("ilacAdi", "Parol 500 mg", "delta", 5, "sebep", "Depodan alım"));
        eczaci.ok("POST", "/api/receteler/" + id + "/ver", null);
        Map<?, ?> stok = (Map<?, ?>) ((List<?>) eczaci.ok("GET", "/api/stok", null)).getFirst();
        assertEquals(new BigDecimal(3), stok.get("miktar"));

        // Mesajlaşma ve okunmamış sayısı
        hasta.ok("POST", "/api/receteler/" + id + "/mesajlar", Map.of("metin", "Teşekkürler <b>!</b>"));
        Map<?, ?> unread = (Map<?, ?>) doktor.ok("GET", "/api/okunmamis", null);
        assertTrue(((BigDecimal) unread.get("sayi")).intValue() >= 1);
        List<?> msgs = (List<?>) doktor.ok("GET", "/api/receteler/" + id + "/mesajlar", null);
        assertEquals("Teşekkürler <b>!</b>", ((Map<?, ?>) msgs.getLast()).get("metin"));

        Map<?, ?> rec = (Map<?, ?>) ((Map<?, ?>) hasta.ok("GET", "/api/receteler/" + id, null)).get("detay");
        assertEquals("VERILDI", rec.get("durum"));

        // Çıkış sonrası oturum geçersiz
        hasta.ok("POST", "/api/auth/logout", null);
        assertEquals(401, hasta.send("GET", "/api/receteler", null, true).statusCode());
    }

    @Test
    void guvenlik() throws Exception {
        Browser b = new Browser();
        // Oturumsuz erişim yok
        assertEquals(401, b.send("GET", "/api/receteler", null, false).statusCode());
        // CSRF başlığı olmadan yazma isteği reddedilir
        assertEquals(403, b.send("POST", "/api/auth/login", Map.of("tc", "10000000146", "password", "x"), false).statusCode());
        // Hatalı giriş
        b.register("hasta", "Mehmet Demir", "12345678950", null);
        Browser other = new Browser();
        assertTrue(other.error("POST", "/api/auth/login", Map.of("tc", "12345678950", "password", "yanlis1"), 400).contains("hatalı"));
        // Bozuk JSON
        HttpResponse<String> r = b.http.send(HttpRequest.newBuilder(URI.create(base + "/api/auth/login"))
                .header("X-Eczane", "1").POST(HttpRequest.BodyPublishers.ofString("{bozuk")).build(), HttpResponse.BodyHandlers.ofString());
        assertEquals(400, r.statusCode());
        // Bilinmeyen uç
        assertEquals(404, b.send("GET", "/api/yok", null, false).statusCode());
    }

    @Test
    void statikDosyalar() throws Exception {
        Browser b = new Browser();
        HttpResponse<String> index = b.send("GET", "/", null, false);
        assertEquals(200, index.statusCode());
        assertTrue(index.body().contains("<title>Eczane Otomasyon</title>"));
        assertTrue(index.headers().firstValue("Content-Security-Policy").orElse("").contains("default-src 'self'"));
        assertTrue(b.send("GET", "/app.js", null, false).headers().firstValue("Content-Type").orElse("").startsWith("text/javascript"));
        // Yüklenebilir uygulama (PWA) dosyaları
        HttpResponse<String> manifest = b.send("GET", "/manifest.webmanifest", null, false);
        assertTrue(manifest.headers().firstValue("Content-Type").orElse("").startsWith("application/manifest+json"));
        assertTrue(((Map<?, ?>) Json.parse(manifest.body())).get("display").equals("standalone"));
        HttpResponse<String> sw = b.send("GET", "/sw.js", null, false);
        assertEquals("no-cache", sw.headers().firstValue("Cache-Control").orElse(""));
        assertTrue(sw.body().contains("/api/"));
        assertEquals(200, b.send("GET", "/icon-512.png", null, false).statusCode());
        // Sunucu bilgisayarından açana telefon adresi listesi gönderilir (liste boş olabilir)
        assertNotNull(((Map<?, ?>) b.ok("GET", "/api/meta", null)).get("agAdresleri"));
        // Klasör gezme denemesi index'e düşer, dosya sızdırmaz
        HttpResponse<String> trav = b.send("GET", "/..%2f..%2fpom.xml", null, false);
        assertTrue(trav.body().contains("<title>Eczane Otomasyon</title>"));
    }

    @Test
    void json() {
        Object o = Json.parse("{\"a\":[1,2.5,true,null,\"x\\u00e7\\n\"],\"b\":{}}");
        assertEquals("{\"a\":[1,2.5,true,null,\"xç\\n\"],\"b\":{}}", Json.write(o));
        assertEquals("\"\\u003cscript\\u003e\"", Json.write("<script>"));
        assertThrows(IllegalArgumentException.class, () -> Json.parse("[1,"));
    }
}
