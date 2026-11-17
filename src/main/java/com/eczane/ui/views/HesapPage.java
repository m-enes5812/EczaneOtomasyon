package com.eczane.ui.views;

import atlantafx.base.controls.CustomTextField;
import atlantafx.base.controls.PasswordTextField;
import atlantafx.base.controls.ToggleSwitch;
import com.eczane.model.Role;
import com.eczane.model.User;
import com.eczane.service.AuthService;
import com.eczane.ui.Fmt;
import com.eczane.ui.Fx;
import com.eczane.ui.Icons;
import com.eczane.ui.Theme;
import com.eczane.ui.Toasts;
import com.eczane.ui.components.Avatar;
import com.eczane.ui.components.Forms;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import org.kordamp.ikonli.materialdesign2.MaterialDesignD;
import org.kordamp.ikonli.materialdesign2.MaterialDesignF;
import org.kordamp.ikonli.materialdesign2.MaterialDesignL;
import org.kordamp.ikonli.materialdesign2.MaterialDesignS;

import java.awt.Desktop;
import java.io.File;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/** Profil, şifre, güvenlik sorusu, görünüm ve (eczacı için) yedekleme. */
final class HesapPage implements Page {

    private final MainView.Context ctx;
    private final ScrollPane root;
    private final Label lastBackup = Forms.hint("");

    HesapPage(MainView.Context ctx) {
        this.ctx = ctx;
        VBox left = new VBox(20, profileCard(), passwordCard());
        VBox right = new VBox(20, securityCard(), appearanceCard(), systemCard());
        HBox.setHgrow(left, Priority.ALWAYS);
        HBox.setHgrow(right, Priority.ALWAYS);
        left.setPrefWidth(480);
        right.setPrefWidth(480);
        HBox cols = new HBox(20, left, right);
        root = new ScrollPane(cols);
        root.setFitToWidth(true);
        root.getStyleClass().add("page-scroll");
    }

    private static VBox card(String title, String subtitle, Node... content) {
        Label t = new Label(title);
        t.getStyleClass().add("card-title");
        VBox head = new VBox(2, t);
        if (subtitle != null) {
            Label s = new Label(subtitle);
            s.getStyleClass().add("muted");
            s.setWrapText(true);
            head.getChildren().add(s);
        }
        VBox card = new VBox(16, head);
        card.getChildren().addAll(content);
        card.getStyleClass().add("card");
        return card;
    }

    private static HBox right(Node n) {
        Region sp = new Region();
        HBox.setHgrow(sp, Priority.ALWAYS);
        HBox h = new HBox(sp, n);
        h.setAlignment(Pos.CENTER_RIGHT);
        return h;
    }

    private VBox profileCard() {
        User u = ctx.user();
        Label name = new Label(u.fullName());
        name.getStyleClass().add("card-title");
        Label role = new Label(u.role().label());
        role.getStyleClass().addAll("badge", "badge-accent");
        HBox head = new HBox(16, new Avatar(u.initials(), u.tc(), 64), new VBox(6, name, role));
        head.setAlignment(Pos.CENTER_LEFT);

        GridPane g = new GridPane();
        g.getStyleClass().add("kv-grid");
        g.setHgap(16);
        g.setVgap(10);
        int r = 0;
        r = kv(g, r, "T.C. Kimlik No", u.maskedTc());
        r = kv(g, r, "Kayıt tarihi", Fmt.full(u.createdAt()));
        if (u.is(Role.ECZACI)) {
            Label ecz = new Label("…");
            ecz.getStyleClass().add("info-value");
            Label l = new Label("Eczane");
            l.getStyleClass().add("info-label");
            g.add(l, 0, r);
            g.add(ecz, 1, r);
            Fx.run(() -> ctx.services().stok().eczaneAdi(u.eczaneId()), ad -> ecz.setText(ad == null ? "—" : ad));
        }

        CustomTextField nameField = Forms.text("Ad Soyad", null);
        nameField.setText(u.fullName());
        Button save = new Button("Adı güncelle");
        save.getStyleClass().add("button-outlined");
        save.setOnAction(e -> {
            String v = nameField.getText();
            Fx.run(() -> ctx.services().auth().changeName(ctx.user(), v), nu -> {
                Toasts.success("Adın güncellendi.");
                ctx.userChanged(nu);
            }, save, null);
        });
        HBox nameRow = new HBox(10, nameField, save);
        HBox.setHgrow(nameField, Priority.ALWAYS);

        return card("Profil", null, head, g, Forms.field("Ad Soyad", nameRow));
    }

    private static int kv(GridPane g, int row, String k, String v) {
        Label l = new Label(k);
        l.getStyleClass().add("info-label");
        Label val = new Label(v);
        val.getStyleClass().add("info-value");
        g.add(l, 0, row);
        g.add(val, 1, row);
        return row + 1;
    }

    private VBox passwordCard() {
        PasswordTextField current = Forms.password("Mevcut şifren");
        PasswordTextField p1 = Forms.password("En az 8 karakter, harf ve rakam");
        PasswordTextField p2 = Forms.password("Yeni şifre (tekrar)");
        Button save = new Button("Şifreyi değiştir", Icons.of(MaterialDesignL.LOCK_RESET, 16));
        save.getStyleClass().add("accent");
        save.setOnAction(e -> {
            String c = current.getPassword(), a = p1.getPassword(), b = p2.getPassword();
            Fx.run(() -> {
                ctx.services().auth().changePassword(ctx.user(), c, a, b);
                return null;
            }, v -> {
                current.setText("");
                p1.setText("");
                p2.setText("");
                Toasts.success("Şifren değiştirildi.");
            }, save, null);
        });
        return card("Şifre", "Güvenliğin için şifreni düzenli olarak değiştir.",
                Forms.field("Mevcut şifre", current), Forms.field("Yeni şifre", p1), Forms.field("Yeni şifre (tekrar)", p2),
                right(save));
    }

    private VBox securityCard() {
        ComboBox<String> q = new ComboBox<>();
        q.getItems().setAll(AuthService.SECURITY_QUESTIONS);
        q.getSelectionModel().selectFirst();
        q.setMaxWidth(Double.MAX_VALUE);
        PasswordTextField answer = Forms.password("Yeni cevap");
        PasswordTextField pw = Forms.password("Onay için mevcut şifren");
        Button save = new Button("Kaydet", Icons.of(MaterialDesignS.SHIELD_CHECK_OUTLINE, 16));
        save.getStyleClass().add("button-outlined");
        save.setOnAction(e -> {
            String qq = q.getValue(), a = answer.getPassword(), p = pw.getPassword();
            Fx.run(() -> {
                ctx.services().auth().changeSecurity(ctx.user(), p, qq, a);
                return null;
            }, v -> {
                answer.setText("");
                pw.setText("");
                Toasts.success("Güvenlik sorun güncellendi.");
            }, save, null);
        });
        return card("Güvenlik sorusu", "Şifreni unutursan hesabını bu soruyla kurtarırsın.",
                Forms.field("Soru", q), Forms.field("Cevap", answer), Forms.field("Mevcut şifre", pw), right(save));
    }

    private VBox appearanceCard() {
        ToggleSwitch dark = new ToggleSwitch("Koyu tema");
        dark.setSelected(Theme.isDark());
        dark.selectedProperty().addListener((o, a, on) -> Theme.darkProperty().set(on));
        Theme.darkProperty().addListener((o, a, on) -> dark.setSelected(on));
        return card("Görünüm", null, dark);
    }

    private VBox systemCard() {
        Path home = ctx.services().paths().home();
        Label path = new Label(ctx.services().db().file().toString());
        path.getStyleClass().add("note-box");
        path.setWrapText(true);
        path.setMaxWidth(Double.MAX_VALUE);

        Button open = new Button("Veri klasörünü aç", Icons.of(MaterialDesignF.FOLDER_OPEN_OUTLINE, 16));
        open.getStyleClass().add("button-outlined");
        open.setOnAction(e -> openFolder(home.toFile()));

        VBox card = card("Sistem", "Veriler bu bilgisayarda saklanır. Her gün açılışta otomatik yedek alınır (son 14 gün).",
                Forms.field("Veritabanı", path));

        if (ctx.user().is(Role.ECZACI)) {
            Button backup = new Button("Şimdi yedek al", Icons.of(MaterialDesignD.DATABASE_EXPORT_OUTLINE, 16));
            backup.getStyleClass().add("accent");
            backup.setOnAction(e -> {
                FileChooser fc = new FileChooser();
                fc.setTitle("Yedeği kaydet");
                fc.setInitialFileName("eczane_yedek_" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmm")) + ".db");
                fc.getExtensionFilters().add(new FileChooser.ExtensionFilter("SQLite veritabanı (*.db)", "*.db"));
                File f = fc.showSaveDialog(ctx.shell().stage());
                if (f == null) return;
                Fx.run(() -> ctx.services().backup().backupTo(f.toPath()), p -> {
                    Toasts.success("Yedek kaydedildi: " + p.getFileName());
                    refresh();
                }, backup, null);
            });
            Button folder = new Button("Yedek klasörü", Icons.of(MaterialDesignF.FOLDER_OUTLINE, 16));
            folder.getStyleClass().add("flat");
            folder.setOnAction(e -> openFolder(ctx.services().backup().directory().toFile()));
            card.getChildren().addAll(lastBackup, new HBox(10, backup, folder, open));
        } else {
            card.getChildren().add(new HBox(10, open));
        }
        return card;
    }

    private static void openFolder(File dir) {
        Fx.run(() -> {
            if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.OPEN)) {
                Desktop.getDesktop().open(dir);
            } else {
                new ProcessBuilder("explorer.exe", dir.getAbsolutePath()).start();
            }
            return null;
        }, v -> { });
    }

    @Override public String title() { return "Hesabım"; }

    @Override public String subtitle() { return "Profil bilgilerin, şifren ve uygulama ayarları"; }

    @Override public Node view() { return root; }

    @Override
    public void refresh() {
        if (!ctx.user().is(Role.ECZACI)) return;
        Fx.run(() -> ctx.services().backup().list(), list -> lastBackup.setText(list.isEmpty()
                        ? "Henüz otomatik yedek yok."
                        : "Son otomatik yedek: " + list.getFirst().getFileName() + "  ·  toplam " + list.size() + " yedek"));
    }
}
