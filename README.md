# Eczane Otomasyon 2.0 (Java)

Doktor, hasta ve eczacı için reçete, stok ve mesajlaşma uygulaması. Eski Python/Tkinter sürümünün
Java 25 + JavaFX ile baştan yazılmış, modernleştirilmiş hâlidir.

## Çalıştırma

| Ne yapmak istiyorsun?              | Komut                                                     |
|------------------------------------|-----------------------------------------------------------|
| Hazır programı aç                  | `target\dist\EczaneOtomasyon\EczaneOtomasyon.exe`         |
| `.exe` paketini yeniden oluştur    | `paketle.cmd`                                             |
| Geliştirirken çalıştır             | `mvnw javafx:run`                                         |
| Testleri çalıştır                  | `mvnw test`                                               |

IntelliJ IDEA'da: **File → Open → `pom.xml`**, ardından `com.eczane.Launcher` sınıfını çalıştır.
Maven kurmana gerek yok; `mvnw` kendini indirir. JDK 25 veya üstü gerekir (`%USERPROFILE%\.jdks\openjdk-27` otomatik bulunur).

`EczaneOtomasyon` klasörünün tamamı Java'yı içinde taşır; başka bir bilgisayara kopyalayıp doğrudan açabilirsin.

## Web sunucusu ve web sitesi

Programı tarayıcıdan da kullanabilirsin: telefon, tablet ya da başka bir bilgisayardan.

| Ne yapmak istiyorsun?              | Komut                                                     |
|------------------------------------|-----------------------------------------------------------|
| Sunucuyu başlat (hazır paket)      | `target\dist\EczaneOtomasyon\EczaneSunucu.exe`            |
| Sunucuyu başlat (geliştirirken)    | `sunucu.cmd`                                              |
| Farklı port                        | `EczaneSunucu.exe --port 8081`                            |
| Yalnızca bu bilgisayardan erişim   | `EczaneSunucu.exe --host 127.0.0.1`                       |

Sunucu açılınca pencerede adresleri yazar:

- Bu bilgisayardan: **http://localhost:8080**
- Aynı ağdaki (Wi-Fi) cihazlardan: **http://192.168.x.x:8080**

İlk açılışta Windows Güvenlik Duvarı izin isterse **"Özel ağlar"** için izin ver.

**Masaüstü uygulamasıyla aynı veritabanını kullanır** (`%LOCALAPPDATA%\EczaneOtomasyon\eczane.db`):
masaüstünde yazılan reçete web'de, web'de gönderilen mesaj masaüstünde görünür. İkisi aynı anda açık olabilir.
Sunucu her saat başı günlük yedeği kontrol eder.

Web sitesinde: tanıtım sayfası, giriş / kayıt / şifremi unuttum, role göre genel bakış, reçete listesi + detay +
sohbet, yeni reçete (doktor), stok ve stok hareketleri (eczacı), hesap ayarları, açık/koyu tema, mobil uyumlu düzen.

Güvenlik: oturum bilgisi sunucuda tutulur (tarayıcıda yalnızca rastgele bir anahtar, `HttpOnly` + `SameSite=Strict`
çerez), CSRF koruması, sıkı içerik güvenlik politikası (CSP), 2 saat işlem yapılmazsa oturum kapanır, şifre
değişince diğer cihazlardaki oturumlar kapanır.

> **Not:** Sunucu düz HTTP ile çalışır; yalnızca güvendiğin yerel ağda kullan. İnternete açmak istersen önüne
> HTTPS sağlayan bir ters vekil (ör. Caddy, nginx) ya da Cloudflare Tunnel koy; çerez otomatik olarak `Secure` olur.

Kod: `src/main/java/com/eczane/web` (sunucu, ek kütüphane gerektirmez: JDK'nın yerleşik HTTP sunucusu),
`src/main/resources/com/eczane/web` (site: HTML, CSS, JS).

## Eski sürümden geçiş

İlk açılışta veritabanı yoksa program **"Eski veritabanını aktar…"** seçeneği sunar. Python sürümünün
`eczane.db` dosyasını seçmen yeterli:

- Tüm kullanıcılar, reçeteler, stoklar ve mesajlar korunur.
- Hesaplar **aynı şifreyle** giriş yapar (PBKDF2 biçimi birebir uyumlu). Düz metin kalmış eski şifreler
  ilk girişte otomatik olarak güvenli biçime çevrilir.
- Şema yalnızca genişletilir (yeni tablo/sütun); hiçbir veri silinmez.

Veriler `%LOCALAPPDATA%\EczaneOtomasyon` altında tutulur (`eczane.db`, `logs\`, `yedekler\`).
Farklı bir klasör için `ECZANE_HOME` ortam değişkenini ayarla.

## Neler yeni?

**Arayüz**
- Modern, sade tasarım (AtlantaFX), **açık/koyu tema**, kenar menüsü, ikonlar, bildirimler.
- Role özel **Genel Bakış** sayfası: sayaçlar, son reçeteler, kritik stoklar.
- Reçeteler **ana-detay** düzeninde: arama, durum filtresi, ilaç tablosu ve **sohbet balonları** aynı ekranda.
- Sohbet 5 saniyede bir, okunmamış mesaj sayısı 10 saniyede bir kendiliğinden yenilenir.
- Pencere hiç donmaz: tüm veritabanı ve şifre işlemleri arka planda çalışır.

**Doktor**
- Hasta T.C. girilince hasta adı anında doğrulanır.
- İlaç adı otomatik tamamlama, hazır kullanım talimatları, aynı ilacın tekrarını birleştirme.
- Bekleyen reçeteyi sebep belirterek iptal edebilir.

**Eczacı**
- Teslim ekranında her ilacın stokta olup olmadığı tek bakışta görünür.
- Teslim işlemi **tek bir işlem** (transaction) hâlinde yapılır: ya hepsi ya hiçbiri. Aynı reçete iki kez verilemez.
- Stok giriş/çıkışı sebebiyle kaydedilir, stok sıfırın altına düşemez.
- İlaç başına **kritik seviye**, **stok hareketleri** geçmişi (kim, ne zaman, neden).
- Kayıtta yeni eczane ekleyebilir. Elle ya da her gün otomatik **yedek** alınır.

**Güvenlik**
- PBKDF2-HMAC-SHA256 (600.000 tekrar), sabit süreli karşılaştırma.
- Resmi **T.C. Kimlik No algoritmasıyla** doğrulama.
- Hatalı denemede artan süreli kilit (3 deneme → 1 dk, sonra 2 dk, 4 dk…).
- Güçlü şifre kuralı (en az 8 karakter, harf + rakam) ve güç göstergesi.
- Tüm yetki kontrolleri servis katmanında: başka hastanın/eczanenin reçetesine erişilemez.
- Parametreli SQL, döner günlük dosyaları.

## Proje yapısı

```
src/main/java/com/eczane
├── Launcher.java          giriş noktası
├── config/                veri klasörü, günlük ayarı
├── db/                    SQLite bağlantısı, şema sürümleri (migrations)
├── model/                 kayıt (record) sınıfları
├── repo/                  SQL sorguları
├── security/              şifre özeti, T.C. doğrulama, deneme sınırı
├── service/               iş kuralları ve yetki kontrolleri
├── ui/                    JavaFX arayüzü (views/, components/, app.css)
└── web/                   web sunucusu (API, oturumlar, JSON)
src/test/java              birim, akış ve sunucu testleri (22 test)
```

Kullanılan kütüphaneler: JavaFX 27, AtlantaFX 2.1, Ikonli (Material Design 2), SQLite JDBC 3.54, JUnit 6.
