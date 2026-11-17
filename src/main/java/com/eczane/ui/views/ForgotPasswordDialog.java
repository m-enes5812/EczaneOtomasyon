package com.eczane.ui.views;

import atlantafx.base.controls.CustomTextField;
import atlantafx.base.controls.PasswordTextField;
import com.eczane.ui.AppShell;
import com.eczane.ui.Fx;
import com.eczane.ui.Icons;
import com.eczane.ui.Toasts;
import com.eczane.ui.components.Forms;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import org.kordamp.ikonli.materialdesign2.MaterialDesignA;
import org.kordamp.ikonli.materialdesign2.MaterialDesignH;
import org.kordamp.ikonli.materialdesign2.MaterialDesignL;

/** İki adımlı şifre sıfırlama: önce T.C. ile soru getirilir, sonra cevap + yeni şifre. */
final class ForgotPasswordDialog {

    private final AppShell shell;
    private final Stage stage;
    private final CustomTextField tc = Forms.tc("11 haneli T.C. Kimlik No", MaterialDesignA.ACCOUNT_OUTLINE);
    private final Label question = new Label();
    private final PasswordTextField answer = Forms.password("Güvenlik sorusunun cevabı");
    private final PasswordTextField pw1 = Forms.password("En az 8 karakter, harf ve rakam");
    private final PasswordTextField pw2 = Forms.password("Yeni şifre (tekrar)");
    private final Label error = AuthLayout.errorBox();
    private final Button next = new Button("Devam", Icons.of(MaterialDesignA.ARROW_RIGHT, 16));
    private final Button reset = new Button("Şifreyi Sıfırla", Icons.of(MaterialDesignL.LOCK_RESET, 16));
    private final VBox step2;
    private String loadedTc;

    ForgotPasswordDialog(AppShell shell, String initialTc) {
        this.shell = shell;
        tc.setText(initialTc == null ? "" : initialTc);

        Label title = new Label("Şifremi unuttum");
        title.getStyleClass().add("card-title");
        Label sub = new Label("Kayıtta seçtiğin güvenlik sorusunu cevaplayarak yeni şifre belirleyebilirsin.");
        sub.getStyleClass().add("muted");
        sub.setWrapText(true);

        question.getStyleClass().addAll("note-box", "info-value");
        question.setGraphic(Icons.of(MaterialDesignH.HELP_CIRCLE_OUTLINE, 16));
        question.setMaxWidth(Double.MAX_VALUE);
        question.setWrapText(true);

        next.getStyleClass().add("accent");
        next.setDefaultButton(true);
        next.setOnAction(e -> loadQuestion());
        HBox tcRow = new HBox(10, tc, next);
        HBox.setHgrow(tc, Priority.ALWAYS);

        step2 = new VBox(14, Forms.field("Güvenlik sorusu", question), Forms.field("Cevap", answer),
                Forms.field("Yeni şifre", pw1), Forms.field("Yeni şifre (tekrar)", pw2));
        step2.setVisible(false);
        step2.setManaged(false);

        Button cancel = new Button("Vazgeç");
        cancel.setCancelButton(true);
        cancel.setOnAction(e -> close());
        reset.getStyleClass().add("accent");
        reset.setDisable(true);
        reset.setOnAction(e -> doReset());
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        HBox buttons = new HBox(10, spacer, cancel, reset);
        buttons.setAlignment(Pos.CENTER_RIGHT);

        VBox root = new VBox(16, new VBox(4, title, sub), error, Forms.field("T.C. Kimlik No", tcRow), step2,
                new Region(), buttons);
        root.setPadding(new Insets(24));
        root.setPrefWidth(480);

        stage = shell.modal("Şifre sıfırlama", root, 480, Region.USE_COMPUTED_SIZE);
        stage.sizeToScene();
        tc.textProperty().addListener((o, a, b) -> {
            if (loadedTc != null && !loadedTc.equals(b)) hideStep2();
        });
    }

    private void close() {
        stage.close();
    }

    void show() {
        stage.show();
        tc.requestFocus();
    }

    private void loadQuestion() {
        AuthLayout.showError(error, null);
        String key = tc.getText();
        Fx.run(() -> shell.services().auth().securityQuestion(key), q -> {
            loadedTc = key;
            question.setText(q);
            step2.setVisible(true);
            step2.setManaged(true);
            reset.setDisable(false);
            next.setDefaultButton(false);
            reset.setDefaultButton(true);
            stage.sizeToScene();
            answer.requestFocus();
        }, next, ex -> AuthLayout.showError(error, ex.getMessage()));
    }

    private void hideStep2() {
        loadedTc = null;
        step2.setVisible(false);
        step2.setManaged(false);
        reset.setDisable(true);
        reset.setDefaultButton(false);
        next.setDefaultButton(true);
        stage.sizeToScene();
    }

    private void doReset() {
        AuthLayout.showError(error, null);
        String key = loadedTc;
        String a = answer.getPassword(), p1 = pw1.getPassword(), p2 = pw2.getPassword();
        Fx.run(() -> {
            shell.services().auth().resetPassword(key, a, p1, p2);
            return null;
        }, v -> {
            stage.close();
            Toasts.success("Şifren güncellendi. Yeni şifrenle giriş yapabilirsin.");
        }, reset, ex -> {
            AuthLayout.showError(error, ex.getMessage());
            stage.sizeToScene();
        });
    }
}
