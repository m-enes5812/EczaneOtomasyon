package com.eczane.ui.views;

import com.eczane.config.AppPaths;
import com.eczane.config.Logging;
import com.eczane.db.Database;
import com.eczane.model.ReceteOzet;
import com.eczane.security.PasswordHasher;
import com.eczane.service.Services;
import com.eczane.ui.App;
import com.eczane.ui.AppShell;
import com.eczane.ui.Dialogs;
import com.eczane.ui.Theme;
import javafx.animation.KeyFrame;
import javafx.animation.PauseTransition;
import javafx.animation.Timeline;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.embed.swing.SwingFXUtils;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Control;
import javafx.scene.control.DialogPane;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.control.Spinner;
import javafx.scene.control.TextField;
import javafx.scene.control.TextInputControl;
import javafx.scene.layout.StackPane;
import javafx.stage.Stage;
import javafx.stage.Window;
import javafx.util.Duration;

import javax.imageio.ImageIO;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

/**
 * Canlı demo: gerçek arayüzde hesap açar, giriş yapar ve tüm özellikleri sırayla kullanır.
 * Her adım ekranın üstünde açıklanır. Kullanım: DemoTour [ekran-görüntüsü-klasörü]
 */
public class DemoTour extends Application {

    private record Step(long waitMs, Runnable action) {
    }

    private static Path shots;
    private final List<Step> steps = new ArrayList<>();
    private final Label caption = new Label();
    private Scene scene;
    private int shotNo;
    private Services s;

    public static void main(String[] args) {
        shots = args.length > 0 ? Path.of(args[0]) : null;
        launch(args);
    }

    @Override
    public void start(Stage stage) throws Exception {
        AppPaths paths = AppPaths.detect();
        Logging.init(paths);
        if (shots != null) Files.createDirectories(shots);
        s = Services.create(paths, Database.open(paths.database()), new PasswordHasher());

        Theme.darkProperty().set(false);
        Theme.apply();
        StackPane root = new StackPane();
        root.getStyleClass().add("app-bg");
        scene = Theme.style(new Scene(root, 1320, 840));
        stage.setScene(scene);
        stage.setTitle(App.TITLE + " — Canlı Demo");
        stage.getIcons().add(App.ICON);
        stage.show();
        Dialogs.setOwner(stage);
        AppShell shell = new AppShell(s, stage, root);

        caption.setStyle("-fx-background-color: rgba(15,23,42,0.88); -fx-text-fill: white; -fx-font-size: 15px;"
                + "-fx-font-weight: bold; -fx-background-radius: 0 0 12 12; -fx-padding: 10 22 10 22;");
        caption.setMouseTransparent(true);
        caption.setWrapText(true);
        caption.setMaxWidth(900);
        StackPane.setAlignment(caption, Pos.TOP_CENTER);
        StackPane.setMargin(caption, new Insets(0));
        root.getChildren().add(caption);

        script(shell);
        run(0);
    }

    // ================================================================= senaryo

    private void script(AppShell shell) {
        say("Merhaba! Eczane Otomasyon'u baştan sona birlikte deneyelim.", 2500);
        act(shell::showLogin, 1200);
        shot("giris");

        // ---------- 1) Hesaplar
        say("1/8 · Doktor hesabı açılıyor…", 1500);
        click("Kayıt ol", 1200);
        click("Doktor", 600);
        type("Ad Soyad", "Ayşe Yılmaz");
        type("11 haneli T.C. Kimlik No", "10000000146");
        type("En az 8 karakter, harf ve rakam", "Doktor2026");
        type("Şifreni tekrar yaz", "Doktor2026");
        type("Cevabın", "İzmir");
        shot("kayit-doktor");
        click("Hesabı Oluştur", 3000);

        say("1/8 · Hasta hesabı açılıyor…", 1200);
        click("Kayıt ol", 1200);
        click("Hasta", 500);
        type("Ad Soyad", "Mehmet Demir");
        type("11 haneli T.C. Kimlik No", "12345678950");
        type("En az 8 karakter, harf ve rakam", "Hasta2026");
        type("Şifreni tekrar yaz", "Hasta2026");
        type("Cevabın", "Ankara");
        click("Hesabı Oluştur", 3000);

        say("1/8 · Eczacı hesabı açılıyor ve yeni bir eczane ekleniyor: Şifa Eczanesi", 1500);
        click("Kayıt ol", 1200);
        click("Eczacı", 700);
        type("Ad Soyad", "Zeynep Kaya");
        type("11 haneli T.C. Kimlik No", "11111111110");
        type("En az 8 karakter, harf ve rakam", "Eczaci2026");
        type("Şifreni tekrar yaz", "Eczaci2026");
        click("Eczanem listede yok, yeni ekle", 600);
        type("Yeni eczanenin adı", "Şifa Eczanesi");
        type("Cevabın", "Bursa");
        shot("kayit-eczaci");
        click("Hesabı Oluştur", 3000);

        // ---------- 2) Güvenlik
        say("2/8 · Güvenlik: yanlış şifreyle giriş denemesi reddedilir", 1500);
        type("11 haneli T.C. Kimlik No", "11111111110");
        type("Şifren", "yanlis123");
        click("Giriş Yap", 2500);
        shot("yanlis-sifre");

        // ---------- 3) Eczacı stok
        say("3/8 · Eczacı doğru şifreyle giriş yapıyor", 1200);
        type("Şifren", "Eczaci2026");
        click("Giriş Yap", 3500);
        say("3/8 · Stok ekranı: ilaç girişleri yapılıyor", 1200);
        click("Stok", 1500);
        stokGiris("Parol 500 mg", 50);
        stokGiris("Augmentin BID 1000 mg", 20);
        stokGiris("Coraspin 100 mg", 2);
        stokGiris("Nexium 40 mg", 15);
        say("3/8 · Augmentin için kritik seviye 25 yapılıyor (altına düşünce uyarı verir)", 1500);
        act(() -> fireTooltip("Kritik seviyeyi değiştir"), 1200);
        act(() -> setSpinner(25), 600);
        click("Kaydet", 2000);
        shot("stok");
        say("3/8 · Eczacı çıkış yapıyor", 1000);
        act(() -> fireTooltip("Çıkış yap"), 1800);

        // ---------- 4) Doktor reçete
        say("4/8 · Doktor giriş yapıp reçete yazıyor", 1200);
        type("11 haneli T.C. Kimlik No", "10000000146");
        type("Şifren", "Doktor2026");
        click("Giriş Yap", 3500);
        click("Yeni Reçete", 1500);
        say("4/8 · Hastanın T.C.'si yazılınca adı otomatik doğrulanır", 1000);
        type("Hastanın T.C. Kimlik No'su", "12345678950");
        pause(1200);
        act(() -> selectCombo("Eczane seç", "Şifa Eczanesi"), 800);
        ilacEkle("Parol 500 mg", 2, "Günde 3 kez 1 adet");
        ilacEkle("Augmentin BID 1000 mg", 1, "Günde 2 kez 1 adet");
        ilacEkle("Coraspin 100 mg", 4, "Günde 1 kez 1 adet");
        type("Hastaya ya da eczacıya not (isteğe bağlı). Ör: Kontrol 1 hafta sonra.", "Kontrol 1 hafta sonra. Bol sıvı tüketilmeli.");
        shot("yeni-recete");
        click("Reçeteyi Oluştur", 3500);
        say("4/8 · Reçete eczaneye gitti. Doktor hastaya mesaj yazıyor", 1200);
        message("Augmentin'i yemekle birlikte almayı unutmayın.");
        say("4/8 · İkinci reçete (sonra iptal edilecek)", 1200);
        click("Yeni Reçete", 1500);
        type("Hastanın T.C. Kimlik No'su", "12345678950");
        pause(1000);
        act(() -> selectCombo("Eczane seç", "Şifa Eczanesi"), 600);
        ilacEkle("Nexium 40 mg", 1, "Sabah aç karnına 1 adet");
        click("Reçeteyi Oluştur", 3500);
        shot("doktor-receteler");
        act(() -> fireTooltip("Çıkış yap"), 1800);

        // ---------- 5) Hasta
        say("5/8 · Hasta giriş yapıyor: reçetelerini ve doktorun mesajını görüyor", 1200);
        type("11 haneli T.C. Kimlik No", "12345678950");
        type("Şifren", "Hasta2026");
        click("Giriş Yap", 3500);
        shot("hasta-genel");
        click("Reçetelerim", 1500);
        act(() -> selectRecete(1), 2500);
        message("Teşekkürler hocam. Yarın sabah eczaneye uğrarım, ilaçlar hazır mı?");
        shot("hasta-sohbet");
        act(() -> fireTooltip("Çıkış yap"), 1800);

        // ---------- 6) Eczacı teslim
        say("6/8 · Eczacı reçeteyi açıyor: Coraspin stokta yetersiz, teslim butonu kapalı", 1500);
        type("11 haneli T.C. Kimlik No", "11111111110");
        type("Şifren", "Eczaci2026");
        click("Giriş Yap", 3500);
        shot("eczaci-genel");
        click("Gelen Reçeteler", 1500);
        act(() -> selectRecete(1), 3000);
        shot("eczaci-stok-yetersiz");
        message("Merhaba, Coraspin'i depodan istedim, yarın sabah hepsi hazır olacak.");
        say("6/8 · Depodan Coraspin geldi: stok girişi yapılıyor", 1200);
        click("Stok", 1500);
        stokGiris("Coraspin 100 mg", 10);
        say("6/8 · Şimdi tüm ilaçlar stokta. Teslim ediliyor…", 1200);
        click("Gelen Reçeteler", 1500);
        act(() -> selectFilter("Tümü"), 800);
        act(() -> selectRecete(1), 2500);
        click("İlaçları Teslim Et", 1800);
        shot("teslim-onay");
        act(() -> fireDialogButton("Teslim Et"), 3000);
        shot("teslim-edildi");
        say("6/8 · İkinci reçete hastanın isteğiyle iptal ediliyor", 1200);
        act(() -> selectRecete(2), 2500);
        click("Reçeteyi İptal Et", 1500);
        act(() -> typeInDialog("Hasta ilacı başka eczaneden aldı"), 1500);
        act(() -> fireDialogButton(null), 3000);

        // ---------- 7) Raporlar
        say("7/8 · Stok hareketleri: kim, ne zaman, neden değiştirdi", 1500);
        click("Stok Hareketleri", 2500);
        shot("hareketler");
        click("Stok", 2000);
        say("7/8 · Augmentin 20 adet, kritik seviyesi 25: listede uyarı olarak görünüyor", 2500);
        click("Genel Bakış", 2500);
        shot("eczaci-ozet");
        say("7/8 · Koyu tema", 1000);
        act(() -> fireTooltip("Açık / koyu tema"), 2500);
        click("Gelen Reçeteler", 1200);
        act(() -> selectFilter("Tümü"), 2500);
        shot("koyu-tema");
        act(() -> fireTooltip("Açık / koyu tema"), 1500);
        click("Hesabım", 2500);
        shot("hesabim");
        act(() -> fireTooltip("Çıkış yap"), 1800);

        // ---------- 8) Şifremi unuttum
        say("8/8 · Hasta şifresini unuttu: güvenlik sorusuyla sıfırlıyor", 1500);
        type("11 haneli T.C. Kimlik No", "12345678950");
        click("Şifremi unuttum", 1500);
        click("Devam", 2000);
        type("Güvenlik sorusunun cevabı", "ankara");
        type("En az 8 karakter, harf ve rakam", "YeniSifre2026");
        type("Yeni şifre (tekrar)", "YeniSifre2026");
        shot("sifre-sifirla");
        click("Şifreyi Sıfırla", 2500);
        say("8/8 · Yeni şifreyle giriş: reçete teslim edildi, eczacının mesajı geldi", 1200);
        type("Şifren", "YeniSifre2026");
        click("Giriş Yap", 3500);
        click("Reçetelerim", 1500);
        act(() -> selectFilter("Tümü"), 800);
        act(() -> selectRecete(1), 3000);
        shot("hasta-son");
        say("Demo tamamlandı! Tüm hesaplar programında kayıtlı. Pencereyi kapatabilirsin.", 6000);
        act(() -> {
            caption.setText("Demo tamamlandı. Bu pencereyi kapatabilirsin.");
            System.out.println("DEMO_DONE");
        }, 0);
    }

    // ================================================================= hazır adımlar

    private void stokGiris(String ilac, int miktar) {
        click("Stok Girişi", 1200);
        type("İlaç adı", ilac);
        act(() -> setSpinner(miktar), 500);
        click("Kaydet", 2000);
    }

    private void ilacEkle(String ilac, int adet, String kullanim) {
        type("İlaç adı (ör. Parol 500 mg)", ilac);
        act(() -> setSpinner(adet), 400);
        act(() -> {
            ComboBox<?> cb = (ComboBox<?>) find(n -> n instanceof ComboBox<?> c && "Kullanım talimatı".equals(c.getPromptText()));
            cb.getEditor().setText(kullanim);
        }, 500);
        click("Ekle", 900);
    }

    private void message(String text) {
        type("Mesaj yaz… (Enter ile gönder)", text);
        act(() -> {
            TextField f = (TextField) findField("Mesaj yaz… (Enter ile gönder)");
            f.getOnAction().handle(new javafx.event.ActionEvent());
        }, 2200);
    }

    // ================================================================= robot

    private void say(String text, long wait) {
        act(() -> caption.setText(text), wait);
    }

    private void pause(long ms) {
        act(() -> { }, ms);
    }

    private void act(Runnable r, long waitAfter) {
        steps.add(new Step(waitAfter, r));
    }

    private void click(String text, long waitAfter) {
        act(() -> {
            Node n = find(x -> x instanceof javafx.scene.control.ButtonBase b && text.equals(b.getText()) && x.isVisible() && !x.isDisabled());
            if (n instanceof javafx.scene.control.ButtonBase b) b.fire();
            else throw new IllegalStateException("Tıklanamaz: " + text);
        }, waitAfter);
    }

    /** Harf harf yazar (izleyen görebilsin diye). */
    private void type(String prompt, String text) {
        long per = 35;
        steps.add(new Step(text.length() * per + 450, () -> {
            TextInputControl f = findField(prompt);
            f.requestFocus();
            f.clear();
            Timeline t = new Timeline();
            for (int i = 0; i < text.length(); i++) {
                String ch = String.valueOf(text.charAt(i));
                t.getKeyFrames().add(new KeyFrame(Duration.millis(per * (i + 1)), e -> f.appendText(ch)));
            }
            t.play();
        }));
    }

    private void shot(String name) {
        act(() -> {
            if (shots == null) return;
            try {
                Window top = topWindow();
                Scene sc = top != null && top.getScene() != null ? top.getScene() : scene;
                // modal pencerelerde ana pencereyi de göster: ana sahne + üstte modal ayrı kaydedilir
                ImageIO.write(SwingFXUtils.fromFXImage(scene.snapshot(null), null), "png",
                        new File(shots.toFile(), String.format("%02d-%s.png", ++shotNo, name)));
                if (sc != scene) {
                    ImageIO.write(SwingFXUtils.fromFXImage(sc.snapshot(null), null), "png",
                            new File(shots.toFile(), String.format("%02d-%s-pencere.png", shotNo, name)));
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }, 200);
    }

    private void run(int i) {
        if (i >= steps.size()) return;
        Step s = steps.get(i);
        try {
            s.action().run();
        } catch (RuntimeException e) {
            System.err.println("ADIM " + i + " HATA: " + e.getMessage());
        }
        PauseTransition p = new PauseTransition(Duration.millis(Math.max(1, s.waitMs())));
        p.setOnFinished(e -> run(i + 1));
        p.play();
    }

    private List<Window> windowsTopFirst() {
        List<Window> ws = new ArrayList<>(Window.getWindows().filtered(Window::isShowing));
        java.util.Collections.reverse(ws);
        ws.removeIf(w -> w instanceof javafx.stage.PopupWindow);
        return ws;
    }

    private Window topWindow() {
        List<Window> ws = windowsTopFirst();
        return ws.isEmpty() ? null : ws.getFirst();
    }

    private Node find(Predicate<Node> p) {
        for (Window w : windowsTopFirst()) {
            if (w.getScene() == null) continue;
            Node n = search(w.getScene().getRoot(), p);
            if (n != null) return n;
        }
        throw new IllegalStateException("Bulunamadı");
    }

    private static Node search(Node n, Predicate<Node> p) {
        if (p.test(n)) return n;
        if (n instanceof javafx.scene.control.ScrollPane sp && sp.getContent() != null) {
            Node r = search(sp.getContent(), p);
            if (r != null) return r;
        }
        if (n instanceof Parent parent) {
            for (Node c : parent.getChildrenUnmodifiable()) {
                Node r = search(c, p);
                if (r != null) return r;
            }
        }
        return null;
    }

    private TextInputControl findField(String prompt) {
        return (TextInputControl) find(n -> n instanceof TextInputControl t && prompt.equals(t.getPromptText()) && n.isVisible());
    }

    private void setSpinner(int v) {
        Spinner<?> s = (Spinner<?>) find(n -> n instanceof Spinner<?>);
        @SuppressWarnings("unchecked") Spinner<Integer> si = (Spinner<Integer>) s;
        si.getValueFactory().setValue(v);
    }

    private void selectCombo(String prompt, String item) {
        ComboBox<?> cb = (ComboBox<?>) find(n -> n instanceof ComboBox<?> c && prompt.equals(c.getPromptText()));
        for (int i = 0; i < cb.getItems().size(); i++) {
            if (String.valueOf(cb.getItems().get(i)).equals(item)) cb.getSelectionModel().select(i);
        }
    }

    private void fireTooltip(String tooltip) {
        Node n = find(x -> x instanceof Control c && c.getTooltip() != null && tooltip.equals(c.getTooltip().getText())
                && x.isVisible() && !x.isDisabled());
        ((Button) n).fire();
    }

    @SuppressWarnings("unchecked")
    private void selectRecete(long id) {
        ListView<ReceteOzet> lv = (ListView<ReceteOzet>) find(n -> n instanceof ListView<?> l && l.getStyleClass().contains("recete-list"));
        for (ReceteOzet r : lv.getItems()) {
            if (r.id() == id) {
                lv.getSelectionModel().select(r);
                lv.scrollTo(r);
                return;
            }
        }
        throw new IllegalStateException("Reçete yok: " + id);
    }

    private void selectFilter(String text) {
        Node n = find(x -> x instanceof javafx.scene.control.ToggleButton t && text.equals(t.getText()));
        ((javafx.scene.control.ToggleButton) n).setSelected(true);
    }

    /** Açık onay/girdi penceresindeki düğmeye basar (text null ise OK düğmesi). */
    private void fireDialogButton(String text) {
        for (Window w : windowsTopFirst()) {
            if (w.getScene() != null && w.getScene().getRoot() instanceof DialogPane dp) {
                for (ButtonType bt : dp.getButtonTypes()) {
                    boolean match = text == null ? bt.getButtonData() == ButtonBar.ButtonData.OK_DONE : text.equals(bt.getText());
                    if (match) {
                        ((Button) dp.lookupButton(bt)).fire();
                        return;
                    }
                }
            }
        }
        throw new IllegalStateException("Diyalog düğmesi yok: " + text);
    }

    private void typeInDialog(String text) {
        for (Window w : windowsTopFirst()) {
            if (w.getScene() != null && w.getScene().getRoot() instanceof DialogPane dp) {
                TextField f = (TextField) search(dp, n -> n instanceof TextField);
                if (f != null) {
                    f.setText(text);
                    return;
                }
            }
        }
    }

    @Override
    public void stop() {
        if (s != null) s.close();
    }
}
