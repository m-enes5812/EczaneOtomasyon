package com.eczane.ui.views;

import com.eczane.config.AppPaths;
import com.eczane.db.Database;
import com.eczane.model.Role;
import com.eczane.model.User;
import com.eczane.security.PasswordHasher;
import com.eczane.service.AuthService.RegisterRequest;
import com.eczane.service.ReceteService.KalemInput;
import com.eczane.service.Services;
import com.eczane.ui.AppShell;
import com.eczane.ui.Theme;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.embed.swing.SwingFXUtils;
import javafx.scene.Scene;
import javafx.scene.image.WritableImage;
import javafx.scene.layout.StackPane;
import javafx.stage.Stage;

import javax.imageio.ImageIO;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Geliştirme aracı: örnek verilerle uygulamayı açar ve ekranların görüntüsünü PNG olarak kaydeder.
 * Kullanım: java ... ScreenshotTool <çıktı-klasörü>
 */
public class ScreenshotTool extends Application {

    private static Path out;
    private final List<Runnable> steps = new ArrayList<>();
    private Stage stage;
    private Scene scene;

    public static void main(String[] args) {
        out = Path.of(args.length > 0 ? args[0] : "screenshots");
        launch(args);
    }

    @Override
    public void start(Stage stage) throws Exception {
        this.stage = stage;
        Files.createDirectories(out);
        Path home = Files.createTempDirectory("eczane-shot");
        System.setProperty("eczane.home", home.toString());
        AppPaths paths = AppPaths.of(home);
        Services s = Services.create(paths, Database.open(paths.database()), new PasswordHasher(1000));
        Demo d = seed(s);

        Theme.darkProperty().set(false);
        Theme.apply();
        StackPane root = new StackPane();
        root.getStyleClass().add("app-bg");
        scene = Theme.style(new Scene(root, 1320, 840));
        stage.setScene(scene);
        stage.show();
        AppShell shell = new AppShell(s, stage, root);

        step(shell::showLogin);
        shot("01-giris");
        step(shell::showRegister);
        shot("02-kayit");
        step(() -> shell.showMain(d.doktor));
        shot("03-doktor-genel");
        step(() -> shell.main().navigate(MainView.Nav.YENI_RECETE));
        shot("04-doktor-yeni-recete");
        step(() -> shell.main().openRecete(d.r1));
        shot("05-doktor-receteler");
        step(() -> shell.showMain(d.eczaci));
        shot("06-eczaci-genel");
        step(() -> shell.main().openRecete(d.r2));
        shot("07-eczaci-recete-detay");
        step(() -> shell.main().navigate(MainView.Nav.STOK));
        shot("08-eczaci-stok");
        step(() -> shell.main().navigate(MainView.Nav.HAREKETLER));
        shot("09-eczaci-hareketler");
        step(() -> shell.main().navigate(MainView.Nav.HESAP));
        shot("10-eczaci-hesap");
        step(() -> shell.showMain(d.hasta));
        step(() -> shell.main().openRecete(d.r1));
        shot("11-hasta-receteler");
        step(() -> Theme.darkProperty().set(true));
        step(() -> shell.showMain(d.eczaci));
        step(() -> shell.main().openRecete(d.r2));
        shot("12-koyu-eczaci-recete");
        step(shell::showLogin);
        shot("13-koyu-giris");
        step(() -> {
            Theme.darkProperty().set(false);
            Platform.exit();
        });
        runSteps(0);
    }

    private void step(Runnable r) { steps.add(r); }

    private void shot(String name) {
        steps.add(() -> {
            WritableImage img = scene.snapshot(null);
            try {
                ImageIO.write(SwingFXUtils.fromFXImage(img, null), "png", new File(out.toFile(), name + ".png"));
                System.out.println("saved " + name);
            } catch (Exception e) {
                e.printStackTrace();
            }
        });
    }

    private void runSteps(int i) {
        if (i >= steps.size()) return;
        steps.get(i).run();
        // asenkron yüklemelerin ve animasyonların bitmesi için bekle
        javafx.animation.PauseTransition p = new javafx.animation.PauseTransition(javafx.util.Duration.millis(1300));
        p.setOnFinished(e -> runSteps(i + 1));
        p.play();
    }

    record Demo(User doktor, User hasta, User eczaci, long r1, long r2) {
    }

    static Demo seed(Services s) {
        long merkez = s.stok().eczaneler().getFirst().id();
        User doktor = reg(s, Role.DOKTOR, "Ayşe Yılmaz", "10000000146", merkez);
        User hasta = reg(s, Role.HASTA, "Mehmet Demir", "12345678950", null);
        User hasta2 = reg(s, Role.HASTA, "Elif Şahin", "22222222220", null);
        User hasta3 = reg(s, Role.HASTA, "Can Öztürk", "33333333330", null);
        User eczaci = reg(s, Role.ECZACI, "Zeynep Kaya", "11111111110", merkez);

        s.stok().adjust(eczaci, "Parol 500 mg", 48, "Depodan alım");
        s.stok().adjust(eczaci, "Augmentin BID 1000 mg", 6, "Depodan alım");
        s.stok().adjust(eczaci, "Majezik 100 mg", 22, "Depodan alım");
        s.stok().adjust(eczaci, "Coraspin 100 mg", 3, "Depodan alım");
        s.stok().adjust(eczaci, "Nexium 40 mg", 15, "Depodan alım");
        s.stok().adjust(eczaci, "Ventolin İnhaler", 0 + 2, "Depodan alım");
        s.stok().adjust(eczaci, "Ventolin İnhaler", -2, "Son kullanma tarihi geçti");
        s.stok().adjust(eczaci, "Dolorex 50 mg", 30, "Depodan alım");

        long r1 = s.recete().create(doktor, hasta.tc(), merkez, "Kontrol 1 hafta sonra. Bol sıvı tüketilmeli.", List.of(
                new KalemInput("Augmentin BID 1000 mg", 1, "Günde 2 kez 1 adet"),
                new KalemInput("Parol 500 mg", 2, "Gerektiğinde 1 adet")));
        long r2 = s.recete().create(doktor, hasta2.tc(), merkez, null, List.of(
                new KalemInput("Nexium 40 mg", 1, "Sabah aç karnına 1 adet"),
                new KalemInput("Coraspin 100 mg", 4, "Günde 1 kez 1 adet"),
                new KalemInput("Majezik 100 mg", 1, "Günde 2 kez 1 adet")));
        long r3 = s.recete().create(doktor, hasta3.tc(), merkez, "Alerji kontrolü", List.of(
                new KalemInput("Dolorex 50 mg", 1, "Günde 2 kez 1 adet")));
        long r4 = s.recete().create(doktor, hasta.tc(), merkez, null, List.of(
                new KalemInput("Majezik 100 mg", 1, "Tok karnına")));
        s.recete().ver(eczaci, r3);
        s.recete().iptal(doktor, r4, "Hatalı doz");

        s.mesaj().send(hasta, r1, "Merhaba, ilacımı yarın sabah alabilir miyim?");
        s.mesaj().send(eczaci, r1, "Tabii, ilaçlarınız hazır. 08:30'dan itibaren bekliyoruz.");
        s.mesaj().send(doktor, r1, "Augmentin'i yemekle birlikte almayı unutmayın.");
        s.mesaj().send(hasta2, r2, "Coraspin yerine eşdeğeri olur mu?");
        return new Demo(doktor, hasta, eczaci, r1, r2);
    }

    private static User reg(Services s, Role role, String name, String tc, Long eczaneId) {
        return s.auth().register(new RegisterRequest(role, name, tc, "Eczane2026", "Eczane2026",
                role == Role.ECZACI ? eczaneId : null, null, "Doğduğun şehir neresidir?", "İzmir"));
    }
}
