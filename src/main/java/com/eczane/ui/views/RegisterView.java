package com.eczane.ui.views;

import atlantafx.base.controls.CustomTextField;
import atlantafx.base.controls.PasswordTextField;
import com.eczane.model.Eczane;
import com.eczane.model.Role;
import com.eczane.security.PasswordPolicy;
import com.eczane.security.TcKimlik;
import com.eczane.service.AuthService;
import com.eczane.service.AuthService.RegisterRequest;
import com.eczane.ui.AppShell;
import com.eczane.ui.Fx;
import com.eczane.ui.Icons;
import com.eczane.ui.Toasts;
import com.eczane.ui.components.Forms;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Hyperlink;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import org.kordamp.ikonli.Ikon;
import org.kordamp.ikonli.materialdesign2.MaterialDesignA;
import org.kordamp.ikonli.materialdesign2.MaterialDesignC;
import org.kordamp.ikonli.materialdesign2.MaterialDesignD;
import org.kordamp.ikonli.materialdesign2.MaterialDesignM;
import org.kordamp.ikonli.materialdesign2.MaterialDesignS;

public final class RegisterView extends StackPane {

    private final AppShell shell;
    private final ToggleGroup roleGroup = new ToggleGroup();
    private final CustomTextField name = Forms.text("Ad Soyad", MaterialDesignA.ACCOUNT_OUTLINE);
    private final CustomTextField tc = Forms.tc("11 haneli T.C. Kimlik No", MaterialDesignC.CARD_ACCOUNT_DETAILS_OUTLINE);
    private final Label tcStatus = Forms.status();
    private final PasswordTextField password = Forms.password("En az 8 karakter, harf ve rakam");
    private final PasswordTextField password2 = Forms.password("Şifreni tekrar yaz");
    private final ProgressBar strength = new ProgressBar(0);
    private final Label strengthText = Forms.hint("");
    private final ComboBox<Eczane> eczane = new ComboBox<>();
    private final CheckBox newEczane = new CheckBox("Eczanem listede yok, yeni ekle");
    private final CustomTextField newEczaneName = Forms.text("Yeni eczanenin adı", MaterialDesignS.STORE_OUTLINE);
    private final ComboBox<String> question = new ComboBox<>();
    private final PasswordTextField answer = Forms.password("Cevabın");
    private final Label error = AuthLayout.errorBox();
    private final Button submit = new Button("Hesabı Oluştur", Icons.of(MaterialDesignA.ACCOUNT_PLUS_OUTLINE, 18));

    public RegisterView(AppShell shell) {
        this.shell = shell;

        Hyperlink back = new Hyperlink("Girişe dön", Icons.of(MaterialDesignA.ARROW_LEFT, 16));
        back.setOnAction(e -> shell.showLogin());

        Label title = new Label("Hesap oluştur");
        title.getStyleClass().add("auth-title");
        Label subtitle = new Label("Rolünü seç ve bilgilerini gir. T.C. Kimlik No giriş için kullanılır.");
        subtitle.getStyleClass().add("muted");
        subtitle.setWrapText(true);

        HBox roles = new HBox(10,
                roleButton(Role.HASTA, MaterialDesignA.ACCOUNT_HEART_OUTLINE),
                roleButton(Role.DOKTOR, MaterialDesignD.DOCTOR),
                roleButton(Role.ECZACI, MaterialDesignM.MORTAR_PESTLE_PLUS));
        roleGroup.getToggles().getFirst().setSelected(true);
        // seçim boş bırakılamasın
        roleGroup.selectedToggleProperty().addListener((o, old, sel) -> {
            if (sel == null && old != null) old.setSelected(true);
        });

        // ---- eczane bölümü (yalnız eczacı)
        eczane.setMaxWidth(Double.MAX_VALUE);
        eczane.setPromptText("Eczane seç");
        newEczaneName.setVisible(false);
        newEczaneName.setManaged(false);
        newEczane.selectedProperty().addListener((o, a, on) -> {
            newEczaneName.setVisible(on);
            newEczaneName.setManaged(on);
            eczane.setDisable(on);
            if (on) newEczaneName.requestFocus();
        });
        VBox eczaneBox = new VBox(8, Forms.field("Çalıştığın eczane", eczane), newEczane, newEczaneName);
        eczaneBox.visibleProperty().bind(roleGroup.selectedToggleProperty().isEqualTo(roleGroup.getToggles().get(2)));
        eczaneBox.managedProperty().bind(eczaneBox.visibleProperty());
        Fx.run(() -> shell.services().stok().eczaneler(), list -> {
            eczane.getItems().setAll(list);
            if (!list.isEmpty()) eczane.getSelectionModel().selectFirst();
        });

        // ---- şifre gücü
        strength.setMaxWidth(Double.MAX_VALUE);
        strength.getStyleClass().addAll("strength-bar", "strength-0");
        password.passwordProperty().addListener((o, a, pw) -> updateStrength(pw));

        // ---- TC doğrulama (anlık)
        tc.textProperty().addListener((o, a, v) -> {
            if (v.length() < 11) Forms.setStatus(tcStatus, null, true);
            else if (TcKimlik.isValid(v)) Forms.setStatus(tcStatus, "Geçerli T.C. Kimlik No", true);
            else Forms.setStatus(tcStatus, "Bu numara geçerli bir T.C. Kimlik No değil.", false);
        });

        question.getItems().setAll(AuthService.SECURITY_QUESTIONS);
        question.getSelectionModel().selectFirst();
        question.setMaxWidth(Double.MAX_VALUE);

        submit.getStyleClass().addAll("accent", "large");
        submit.setMaxWidth(Double.MAX_VALUE);
        submit.setDefaultButton(true);
        submit.setOnAction(e -> register());

        HBox pwRow = new HBox(12, grow(Forms.field("Şifre", password, new VBox(4, strength, strengthText))),
                grow(Forms.field("Şifre (tekrar)", password2)));

        Label secTitle = new Label("HESAP KURTARMA");
        secTitle.getStyleClass().add("section-title");

        VBox form = new VBox(16,
                back,
                new VBox(6, title, subtitle),
                error,
                Forms.field("Rolün", roles),
                Forms.field("Ad Soyad", name),
                Forms.field("T.C. Kimlik No", tc, tcStatus),
                pwRow,
                eczaneBox,
                secTitle,
                Forms.field("Güvenlik sorusu", question, Forms.hint("Şifreni unutursan bu soruyla sıfırlayabilirsin.")),
                Forms.field("Cevap", answer),
                submit);
        form.setMaxWidth(520);
        form.setAlignment(Pos.TOP_LEFT);

        StackPane center = new StackPane(form);
        center.setAlignment(Pos.TOP_CENTER);
        center.setStyle("-fx-padding: 24 0 24 0;");
        ScrollPane scroll = new ScrollPane(center);
        scroll.setFitToWidth(true);
        scroll.getStyleClass().add("page-scroll");

        getChildren().add(AuthLayout.wrap(scroll));
    }

    private ToggleButton roleButton(Role role, Ikon icon) {
        ToggleButton b = new ToggleButton(role.label(), Icons.of(icon, 22));
        b.setUserData(role);
        b.setToggleGroup(roleGroup);
        b.getStyleClass().add("role-toggle");
        b.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(b, Priority.ALWAYS);
        return b;
    }

    private static VBox grow(VBox box) {
        HBox.setHgrow(box, Priority.ALWAYS);
        box.setMaxWidth(Double.MAX_VALUE);
        return box;
    }

    private void updateStrength(String pw) {
        int s = PasswordPolicy.strength(pw);
        strength.setProgress(pw.isEmpty() ? 0 : (s + 1) / 5.0);
        strength.getStyleClass().removeIf(c -> c.startsWith("strength-") && !c.equals("strength-bar"));
        strength.getStyleClass().add("strength-" + s);
        strengthText.setText(pw.isEmpty() ? "" : switch (s) {
            case 0, 1 -> "Zayıf şifre";
            case 2 -> "Orta";
            case 3 -> "Güçlü";
            default -> "Çok güçlü";
        });
    }

    private void register() {
        Role role = (Role) roleGroup.getSelectedToggle().getUserData();
        Eczane e = eczane.getValue();
        RegisterRequest req = new RegisterRequest(role, name.getText(), tc.getText(), password.getPassword(),
                password2.getPassword(),
                role == Role.ECZACI && !newEczane.isSelected() && e != null ? e.id() : null,
                role == Role.ECZACI && newEczane.isSelected() ? newEczaneName.getText() : null,
                question.getValue(), answer.getPassword());
        submit.setText("Hesap oluşturuluyor…");
        Fx.run(() -> shell.services().auth().register(req), user -> {
            Toasts.success("Hesabın oluşturuldu. Şimdi giriş yapabilirsin.");
            shell.showLogin();
        }, submit, ex -> {
            submit.setText("Hesabı Oluştur");
            AuthLayout.showError(error, ex.getMessage());
            ScrollPane sp = (ScrollPane) lookup(".page-scroll");
            if (sp != null) sp.setVvalue(0);
        });
    }
}
