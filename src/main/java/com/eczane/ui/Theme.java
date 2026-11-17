package com.eczane.ui;

import atlantafx.base.theme.PrimerDark;
import atlantafx.base.theme.PrimerLight;
import javafx.application.Application;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.scene.Scene;
import javafx.stage.Window;

import java.util.Objects;
import java.util.prefs.Preferences;

/** Açık/koyu tema. Tercih kullanıcının bilgisayarında saklanır. */
public final class Theme {

    private static final Preferences PREFS = Preferences.userNodeForPackage(Theme.class);
    private static final String KEY_DARK = "theme.dark";
    private static final String CSS = Objects.requireNonNull(Theme.class.getResource("/com/eczane/styles/app.css")).toExternalForm();
    private static final BooleanProperty DARK = new SimpleBooleanProperty(PREFS.getBoolean(KEY_DARK, false));

    static {
        DARK.addListener((obs, was, is) -> {
            PREFS.putBoolean(KEY_DARK, is);
            apply();
        });
    }

    private Theme() {
    }

    public static BooleanProperty darkProperty() { return DARK; }

    public static boolean isDark() { return DARK.get(); }

    public static void toggle() { DARK.set(!DARK.get()); }

    /** Temayı tüm açık pencerelere uygular. */
    public static void apply() {
        Application.setUserAgentStylesheet(isDark()
                ? new PrimerDark().getUserAgentStylesheet()
                : new PrimerLight().getUserAgentStylesheet());
        for (Window w : Window.getWindows()) {
            if (w.getScene() != null) style(w.getScene());
        }
    }

    /** Yeni oluşturulan sahnelere uygulama stilini ve tema sınıfını ekler. */
    public static Scene style(Scene scene) {
        if (!scene.getStylesheets().contains(CSS)) scene.getStylesheets().add(CSS);
        if (scene.getRoot() != null) {
            scene.getRoot().getStyleClass().remove("dark");
            if (isDark()) scene.getRoot().getStyleClass().add("dark");
        }
        return scene;
    }
}
