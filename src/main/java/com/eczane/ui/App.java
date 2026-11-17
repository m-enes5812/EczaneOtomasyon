package com.eczane.ui;

import com.eczane.config.AppPaths;
import com.eczane.config.Logging;
import com.eczane.db.Database;
import com.eczane.security.PasswordHasher;
import com.eczane.service.Services;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.image.Image;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import javafx.stage.Stage;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.util.Objects;
import java.util.logging.Level;
import java.util.logging.Logger;

public final class App extends Application {

    private static final Logger LOG = Logger.getLogger(App.class.getName());
    public static final String TITLE = "Eczane Otomasyon";
    public static final Image ICON = new Image(Objects.requireNonNull(
            App.class.getResourceAsStream("/com/eczane/images/logo-64.png")));

    private Services services;

    @Override
    public void start(Stage stage) {
        AppPaths paths = AppPaths.detect();
        Logging.init(paths);
        LOG.info("Uygulama başlatılıyor. Veri klasörü: " + paths.home());
        Thread.setDefaultUncaughtExceptionHandler((t, e) -> {
            LOG.log(Level.SEVERE, "Yakalanmamış hata (" + t.getName() + ")", e);
            if (Platform.isFxApplicationThread()) Dialogs.unexpected(e);
        });

        Theme.apply();
        StackPane root = new StackPane();
        root.getStyleClass().add("app-bg");
        Scene scene = Theme.style(new Scene(root, 1280, 800));
        stage.setScene(scene);
        stage.setTitle(TITLE);
        stage.getIcons().add(ICON);
        stage.setMinWidth(1080);
        stage.setMinHeight(700);
        Dialogs.setOwner(stage);

        ProgressIndicator spinner = new ProgressIndicator();
        spinner.setPrefSize(42, 42);
        Label loading = new Label("Hazırlanıyor…");
        loading.getStyleClass().add("muted");
        VBox splash = new VBox(14, spinner, loading);
        splash.setAlignment(Pos.CENTER);
        splash.getStyleClass().add("loading-pane");
        root.getChildren().add(splash);
        stage.show();

        if (!offerLegacyImport(paths)) {
            Platform.exit();
            return;
        }

        Fx.run(() -> {
            Services s = Services.create(paths, Database.open(paths.database()), new PasswordHasher());
            s.backup().dailyBackup();
            return s;
        }, s -> {
            services = s;
            AppShell shell = new AppShell(s, stage, root);
            shell.showLogin();
        });
    }

    /**
     * İlk çalıştırmada (veritabanı yoksa) eski Python sürümünün veritabanını aktarmayı önerir.
     *
     * @return devam edilecekse true
     */
    private boolean offerLegacyImport(AppPaths paths) {
        Path db = paths.database();
        if (Files.exists(db)) return true;

        ButtonType fresh = new ButtonType("Yeni başla", ButtonBar.ButtonData.OK_DONE);
        ButtonType importOld = new ButtonType("Eski veritabanını aktar…", ButtonBar.ButtonData.OTHER);
        Alert a = new Alert(Alert.AlertType.INFORMATION, "", fresh, importOld);
        a.initOwner(Dialogs.owner());
        a.setTitle(TITLE);
        a.setHeaderText("Eczane Otomasyon'a hoş geldin");
        Label l = new Label("""
                Bu bilgisayarda henüz bir veritabanı yok.

                Önceki (Python) sürümde kullandığın eczane.db dosyasını aktarabilirsin; tüm kullanıcılar, \
                reçeteler ve stoklar korunur ve aynı şifrelerle giriş yapılabilir.""");
        l.setWrapText(true);
        l.setMaxWidth(440);
        a.getDialogPane().setContent(l);
        Theme.style(a.getDialogPane().getScene());

        var choice = a.showAndWait();
        if (choice.isEmpty()) return true;
        if (choice.get() != importOld) return true;

        FileChooser fc = new FileChooser();
        fc.setTitle("Eski veritabanını seç");
        fc.getExtensionFilters().add(new FileChooser.ExtensionFilter("SQLite veritabanı (*.db)", "*.db"));
        File f = fc.showOpenDialog(Dialogs.owner());
        if (f == null) return true;
        try {
            if (!looksLikeEczaneDb(f.toPath())) {
                Dialogs.warn("Geçersiz dosya", "Seçilen dosya bir Eczane Otomasyon veritabanı değil. Yeni veritabanı ile devam edilecek.");
                return true;
            }
            Files.copy(f.toPath(), db);
            LOG.info("Eski veritabanı aktarıldı: " + f);
            Toasts.success("Eski veritabanı aktarıldı.");
        } catch (Exception e) {
            LOG.log(Level.WARNING, "Veritabanı aktarılamadı", e);
            Dialogs.warn("Aktarılamadı", "Dosya kopyalanamadı: " + e.getMessage());
        }
        return true;
    }

    private static boolean looksLikeEczaneDb(Path p) {
        try (Connection c = DriverManager.getConnection("jdbc:sqlite:file:" + p.toAbsolutePath() + "?mode=ro");
             var rs = c.createStatement().executeQuery(
                     "SELECT COUNT(*) FROM sqlite_master WHERE type='table' AND name IN ('users','receteler')")) {
            return rs.next() && rs.getInt(1) == 2;
        } catch (Exception e) {
            return false;
        }
    }

    @Override
    public void stop() {
        LOG.info("Uygulama kapatılıyor");
        if (services != null) services.close();
    }
}
