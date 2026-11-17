package com.eczane.ui.components;

import com.eczane.ui.Icons;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import org.kordamp.ikonli.Ikon;

/** Genel bakış ekranındaki sayaç kartı. */
public final class StatCard extends HBox {

    private final Label value = new Label("–");
    private final Label caption = new Label();

    /** @param tone "accent", "success", "warning", "danger" ya da "info" */
    public StatCard(Ikon ikon, String title, String tone) {
        super(16);
        getStyleClass().addAll("card", "stat-card", "tone-" + tone);
        setAlignment(Pos.CENTER_LEFT);

        StackPane icon = new StackPane(Icons.of(ikon, 24));
        icon.getStyleClass().add("stat-icon");

        Label t = new Label(title);
        t.getStyleClass().add("stat-title");
        value.getStyleClass().add("stat-value");
        caption.getStyleClass().add("stat-caption");
        caption.setManaged(false);
        caption.setVisible(false);

        VBox text = new VBox(2, t, value, caption);
        getChildren().addAll(icon, text);
        // Eşit genişlik: tercih edilen genişlik 0, boş alan kartlar arasında eşit paylaşılır.
        HBox.setHgrow(this, Priority.ALWAYS);
        setPrefWidth(0);
        setMaxWidth(Double.MAX_VALUE);
    }

    public void setValue(int v) { value.setText(String.format(java.util.Locale.forLanguageTag("tr"), "%,d", v)); }

    public void setCaption(String text) {
        boolean has = text != null && !text.isBlank();
        caption.setText(text);
        caption.setManaged(has);
        caption.setVisible(has);
    }
}
