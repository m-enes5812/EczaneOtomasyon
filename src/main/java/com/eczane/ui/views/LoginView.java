package com.eczane.ui.views;

import atlantafx.base.controls.CustomTextField;
import atlantafx.base.controls.PasswordTextField;
import com.eczane.model.User;
import com.eczane.ui.AppShell;
import com.eczane.ui.Fx;
import com.eczane.ui.Icons;
import com.eczane.ui.Toasts;
import com.eczane.ui.components.Forms;
import javafx.application.Platform;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Hyperlink;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import org.kordamp.ikonli.materialdesign2.MaterialDesignA;
import org.kordamp.ikonli.materialdesign2.MaterialDesignL;

public final class LoginView extends StackPane {

    private final AppShell shell;
    private final CustomTextField tc = Forms.tc("11 haneli T.C. Kimlik No", MaterialDesignA.ACCOUNT_OUTLINE);
    private final PasswordTextField password = Forms.password("Şifren");
    private final Label error = AuthLayout.errorBox();
    private final Button submit = new Button("Giriş Yap", Icons.of(MaterialDesignL.LOGIN, 18));

    public LoginView(AppShell shell) {
        this.shell = shell;

        Label title = new Label("Tekrar hoş geldin");
        title.getStyleClass().add("auth-title");
        Label subtitle = new Label("Devam etmek için T.C. Kimlik No ve şifrenle giriş yap.");
        subtitle.getStyleClass().add("muted");
        subtitle.setWrapText(true);

        Hyperlink forgot = new Hyperlink("Şifremi unuttum");
        forgot.setOnAction(e -> new ForgotPasswordDialog(shell, tc.getText()).show());

        submit.getStyleClass().addAll("accent", "large");
        submit.setMaxWidth(Double.MAX_VALUE);
        submit.setDefaultButton(true);
        submit.setOnAction(e -> login());

        Label noAccount = new Label("Hesabın yok mu?");
        noAccount.getStyleClass().add("muted");
        Hyperlink register = new Hyperlink("Kayıt ol");
        register.setOnAction(e -> shell.showRegister());
        HBox registerRow = new HBox(4, noAccount, register);
        registerRow.setAlignment(Pos.CENTER);

        VBox pwField = Forms.field("Şifre", password);
        VBox form = new VBox(18,
                new VBox(6, title, subtitle),
                error,
                Forms.field("T.C. Kimlik No", tc),
                new VBox(2, pwField, forgotRow(forgot)),
                submit,
                registerRow);
        form.setMaxWidth(400);
        form.setFillWidth(true);
        form.setAlignment(Pos.CENTER_LEFT);

        getChildren().add(AuthLayout.wrap(form));

        tc.textProperty().addListener((o, a, b) -> AuthLayout.showError(error, null));
        password.passwordProperty().addListener((o, a, b) -> AuthLayout.showError(error, null));
        Platform.runLater(tc::requestFocus);
    }

    private static HBox forgotRow(Hyperlink forgot) {
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        HBox row = new HBox(spacer, forgot);
        row.setAlignment(Pos.CENTER_RIGHT);
        return row;
    }

    private void login() {
        String tcValue = tc.getText();
        String pw = password.getPassword();
        if (tcValue.length() != 11) {
            AuthLayout.showError(error, "T.C. Kimlik No 11 haneli olmalıdır.");
            tc.requestFocus();
            return;
        }
        if (pw.isEmpty()) {
            AuthLayout.showError(error, "Şifreni gir.");
            password.requestFocus();
            return;
        }
        submit.setText("Giriş yapılıyor…");
        Fx.run(() -> shell.services().auth().login(tcValue, pw), (User user) -> {
            Toasts.success("Hoş geldin, " + user.fullName() + ".");
            shell.showMain(user);
        }, submit, ex -> {
            submit.setText("Giriş Yap");
            AuthLayout.showError(error, ex.getMessage());
            password.setText("");
            password.requestFocus();
        });
    }
}
