package com.eczane.ui;

import com.eczane.model.User;
import com.eczane.service.Services;
import com.eczane.ui.views.LoginView;
import com.eczane.ui.views.MainView;
import com.eczane.ui.views.RegisterView;
import javafx.animation.FadeTransition;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.layout.StackPane;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.util.Duration;

/** Ekranlar arası geçiş: giriş → kayıt → ana ekran. */
public final class AppShell {

    private final Services services;
    private final Stage stage;
    private final StackPane screen = new StackPane();
    private MainView main;

    public AppShell(Services services, Stage stage, StackPane root) {
        this.services = services;
        this.stage = stage;
        root.getChildren().setAll(screen);
        Toasts.install(root);
    }

    public Services services() { return services; }

    /** Açık ana ekran (yoksa null). */
    public MainView main() { return main; }

    public Stage stage() { return stage; }

    public void showLogin() {
        disposeMain();
        stage.setTitle(App.TITLE);
        swap(new LoginView(this));
    }

    public void showRegister() {
        swap(new RegisterView(this));
    }

    public void showMain(User user) {
        disposeMain();
        main = new MainView(this, user);
        stage.setTitle(App.TITLE + " — " + user.fullName() + " (" + user.role().label() + ")");
        swap(main);
    }

    public void logout() {
        showLogin();
        Toasts.info("Oturum kapatıldı.");
    }

    /** Ana pencereye bağlı, temaya uygun modal pencere. */
    public Stage modal(String title, javafx.scene.Parent content, double w, double h) {
        Stage s = new Stage();
        s.initOwner(stage);
        s.initModality(Modality.WINDOW_MODAL);
        s.setTitle(title);
        s.getIcons().add(App.ICON);
        s.setScene(Theme.style(new Scene(content, w, h)));
        s.setResizable(false);
        return s;
    }

    private void disposeMain() {
        if (main != null) {
            main.dispose();
            main = null;
        }
    }

    private void swap(Node next) {
        next.setOpacity(0);
        screen.getChildren().setAll(next);
        FadeTransition ft = new FadeTransition(Duration.millis(180), next);
        ft.setToValue(1);
        ft.play();
    }
}
