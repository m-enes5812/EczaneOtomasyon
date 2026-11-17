package com.eczane.ui.components;

import javafx.scene.control.Label;
import javafx.scene.layout.StackPane;

/** Baş harflerden oluşan yuvarlak profil rozeti. */
public final class Avatar extends StackPane {

    private static final String[] PALETTE = {"#0d9488", "#2563eb", "#7c3aed", "#db2777", "#ea580c", "#16a34a", "#0891b2"};

    public Avatar(String initials, String seed, double size) {
        Label l = new Label(initials);
        l.getStyleClass().add("avatar-text");
        l.setStyle("-fx-font-size: " + Math.round(size * 0.38) + "px;");
        getChildren().add(l);
        getStyleClass().add("avatar");
        setMinSize(size, size);
        setPrefSize(size, size);
        setMaxSize(size, size);
        int idx = Math.floorMod(seed == null ? 0 : seed.hashCode(), PALETTE.length);
        setStyle("-fx-background-color: " + PALETTE[idx] + "; -fx-background-radius: " + size + ";");
    }
}
