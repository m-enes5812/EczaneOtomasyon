package com.eczane.ui.components;

import com.eczane.ui.Icons;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import org.kordamp.ikonli.Ikon;

/** "Henüz kayıt yok" türü boş ekran. */
public final class EmptyState extends VBox {

    private final Label title = new Label();
    private final Label detail = new Label();

    public EmptyState(Ikon ikon, String title, String detail) {
        super(10);
        setAlignment(Pos.CENTER);
        getStyleClass().add("empty-state");
        StackPane circle = new StackPane(Icons.of(ikon, 32));
        circle.getStyleClass().add("empty-icon");
        this.title.getStyleClass().add("empty-title");
        this.detail.getStyleClass().add("empty-detail");
        this.detail.setWrapText(true);
        this.detail.setMaxWidth(320);
        this.detail.setAlignment(Pos.CENTER);
        this.detail.setTextAlignment(javafx.scene.text.TextAlignment.CENTER);
        set(title, detail);
        getChildren().addAll(circle, this.title, this.detail);
    }

    public void set(String t, String d) {
        title.setText(t);
        detail.setText(d);
        detail.setVisible(d != null && !d.isBlank());
        detail.setManaged(detail.isVisible());
    }
}
