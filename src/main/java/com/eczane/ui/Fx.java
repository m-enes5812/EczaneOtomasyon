package com.eczane.ui;

import com.eczane.service.ServiceException;
import javafx.application.Platform;
import javafx.scene.Node;

import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Veritabanı ve şifre özetleme işlerini arayüz iş parçacığının dışında çalıştırır; sonucu arayüz
 * iş parçacığına geri getirir. Böylece pencere hiçbir zaman donmaz.
 */
public final class Fx {

    private static final Logger LOG = Logger.getLogger(Fx.class.getName());
    private static final ExecutorService WORKER = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "eczane-worker");
        t.setDaemon(true);
        return t;
    });

    private Fx() {
    }

    /** Arka planda çalıştırır; iş kuralı hataları bildirim olarak, beklenmeyenler hata penceresi olarak gösterilir. */
    public static <T> void run(Callable<T> work, Consumer<T> onSuccess) {
        run(work, onSuccess, null, null);
    }

    /**
     * @param busy    iş sürerken devre dışı bırakılacak düğüm (ör. buton), null olabilir
     * @param onError iş kuralı hatası için özel işleyici; null ise bildirim gösterilir
     */
    public static <T> void run(Callable<T> work, Consumer<T> onSuccess, Node busy, Consumer<ServiceException> onError) {
        if (busy != null) busy.setDisable(true);
        WORKER.submit(() -> {
            try {
                T result = work.call();
                Platform.runLater(() -> {
                    if (busy != null) busy.setDisable(false);
                    if (onSuccess != null) onSuccess.accept(result);
                });
            } catch (ServiceException e) {
                Platform.runLater(() -> {
                    if (busy != null) busy.setDisable(false);
                    if (onError != null) onError.accept(e);
                    else Toasts.error(e.getMessage());
                });
            } catch (Throwable e) {
                LOG.log(Level.SEVERE, "Beklenmeyen hata", e);
                Platform.runLater(() -> {
                    if (busy != null) busy.setDisable(false);
                    Dialogs.unexpected(e);
                });
            }
        });
    }

    /** Sonuç beklemeyen arka plan işi. */
    public static void run(ThrowingRunnable work, Runnable onSuccess) {
        run(() -> {
            work.run();
            return null;
        }, v -> {
            if (onSuccess != null) onSuccess.run();
        });
    }

    @FunctionalInterface
    public interface ThrowingRunnable {
        void run() throws Exception;
    }
}
