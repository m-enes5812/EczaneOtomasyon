package com.eczane.ui;

import javafx.animation.FadeTransition;
import javafx.animation.PauseTransition;
import javafx.animation.SequentialTransition;
import javafx.animation.TranslateTransition;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.util.Duration;
import org.kordamp.ikonli.Ikon;
import org.kordamp.ikonli.materialdesign2.MaterialDesignA;
import org.kordamp.ikonli.materialdesign2.MaterialDesignC;
import org.kordamp.ikonli.materialdesign2.MaterialDesignI;

/** Sağ altta beliren, kendiliğinden kaybolan bildirimler. */
public final class Toasts {

    private static VBox host;

    private Toasts() {
    }

    /** Ana penceredeki katman. Uygulama başlarken bir kez çağrılır. */
    public static void install(StackPane root) {
        host = new VBox(10);
        host.setAlignment(Pos.BOTTOM_RIGHT);
        host.setPadding(new Insets(24));
        host.setPickOnBounds(false);
        host.setMouseTransparent(false);
        host.setMaxSize(Region.USE_PREF_SIZE, Region.USE_PREF_SIZE);
        StackPane.setAlignment(host, Pos.BOTTOM_RIGHT);
        root.getChildren().add(host);
    }

    public static void success(String msg) { show(msg, "success", MaterialDesignC.CHECK_CIRCLE_OUTLINE); }

    public static void info(String msg) { show(msg, "info", MaterialDesignI.INFORMATION_OUTLINE); }

    public static void error(String msg) { show(msg, "error", MaterialDesignA.ALERT_CIRCLE_OUTLINE); }

    private static void show(String msg, String kind, Ikon ikon) {
        if (host == null) {
            if ("error".equals(kind)) Dialogs.warn("Uyarı", msg);
            return;
        }
        Label text = new Label(msg);
        text.setWrapText(true);
        text.setMaxWidth(360);
        text.getStyleClass().add("toast-text");
        HBox toast = new HBox(12, Icons.of(ikon, 20, "toast-icon"), text);
        toast.setAlignment(Pos.CENTER_LEFT);
        toast.getStyleClass().addAll("toast", "toast-" + kind);
        toast.setMaxWidth(Region.USE_PREF_SIZE);
        toast.setOpacity(0);
        toast.setOnMouseClicked(e -> host.getChildren().remove(toast));

        if (host.getChildren().size() >= 4) host.getChildren().removeFirst();
        host.getChildren().add(toast);

        FadeTransition in = new FadeTransition(Duration.millis(180), toast);
        in.setToValue(1);
        TranslateTransition slide = new TranslateTransition(Duration.millis(180), toast);
        slide.setFromY(16);
        slide.setToY(0);
        PauseTransition stay = new PauseTransition(Duration.seconds("error".equals(kind) ? 6 : 3.5));
        FadeTransition out = new FadeTransition(Duration.millis(250), toast);
        out.setToValue(0);
        out.setOnFinished(e -> host.getChildren().remove(toast));
        slide.play();
        new SequentialTransition(in, stay, out).play();
    }
}
