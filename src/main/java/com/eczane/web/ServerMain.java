package com.eczane.web;

import com.eczane.config.AppPaths;
import com.eczane.config.Logging;
import com.eczane.db.Database;
import com.eczane.security.PasswordHasher;
import com.eczane.service.Services;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

/**
 * Sunucu giriş noktası.
 *
 * <pre>
 *   EczaneSunucu.exe [--port 8080] [--host 0.0.0.0]
 * </pre>
 * Ortam değişkenleri: {@code ECZANE_PORT}, {@code ECZANE_HOST}, {@code ECZANE_HOME} (veri klasörü).
 */
public final class ServerMain {

    private static final Logger LOG = Logger.getLogger(ServerMain.class.getName());

    private ServerMain() {
    }

    public static void main(String[] args) throws Exception {
        String host = env("ECZANE_HOST", "0.0.0.0");
        int port = Integer.parseInt(env("ECZANE_PORT", "8080"));
        for (int i = 0; i < args.length; i++) {
            switch (args[i]) {
                case "--port", "-p" -> port = Integer.parseInt(args[++i]);
                case "--host" -> host = args[++i];
                case "--help", "-h" -> {
                    System.out.println("Kullanım: EczaneSunucu [--port 8080] [--host 0.0.0.0]");
                    return;
                }
                default -> {
                    System.err.println("Bilinmeyen seçenek: " + args[i]);
                    System.exit(2);
                }
            }
        }

        AppPaths paths = AppPaths.detect();
        Logging.init(paths);
        LOG.info("Sunucu başlatılıyor. Veri klasörü: " + paths.home());

        Services services = Services.create(paths, Database.open(paths.database()), new PasswordHasher());
        services.backup().dailyBackup();
        ScheduledExecutorService timer = Executors.newSingleThreadScheduledExecutor(Thread.ofVirtual().factory());
        timer.scheduleAtFixedRate(services.backup()::dailyBackup, 1, 1, TimeUnit.HOURS);

        WebServer web = new WebServer(services);
        try {
            web.start(host, port);
        } catch (java.net.BindException e) {
            System.err.println("[HATA] " + port + " numaralı port kullanımda. Başka bir port deneyin: --port 8081");
            services.close();
            System.exit(1);
        }

        CountDownLatch stopped = new CountDownLatch(1);
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            System.out.println("Sunucu kapatılıyor…");
            web.close();
            timer.shutdownNow();
            services.close();
            stopped.countDown();
        }));

        System.out.println();
        System.out.println("  Eczane Otomasyon sunucusu çalışıyor");
        System.out.println("  -----------------------------------");
        System.out.println("  Bu bilgisayardan : http://localhost:" + web.port());
        if (host.equals("0.0.0.0")) {
            for (String ip : WebServer.lanAddresses()) System.out.println("  Ağdaki cihazlardan: http://" + ip + ":" + web.port());
        }
        System.out.println("  Veri klasörü      : " + paths.home());
        System.out.println();
        System.out.println("  Kapatmak için bu pencerede Ctrl+C'ye basın.");
        stopped.await();
    }

    private static String env(String key, String def) {
        String v = System.getenv(key);
        return v == null || v.isBlank() ? def : v.strip();
    }
}
