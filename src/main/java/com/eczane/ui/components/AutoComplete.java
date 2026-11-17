package com.eczane.ui.components;

import javafx.geometry.Side;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.CustomMenuItem;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;

import java.util.List;
import java.util.Locale;
import java.util.function.Supplier;

/** Bir metin alanına yazarken öneri listesi ekler (ör. ilaç adları). */
public final class AutoComplete {

    private static final Locale TR = Locale.forLanguageTag("tr");
    private static final int MAX = 8;

    private AutoComplete() {
    }

    public static void attach(TextField field, Supplier<List<String>> source) {
        ContextMenu menu = new ContextMenu();
        menu.getStyleClass().add("autocomplete");
        final boolean[] picking = {false};

        field.textProperty().addListener((obs, old, text) -> {
            if (picking[0]) return;
            if (text == null || text.isBlank() || !field.isFocused()) {
                menu.hide();
                return;
            }
            String q = text.strip().toLowerCase(TR);
            List<String> matches = source.get().stream()
                    .filter(s -> s.toLowerCase(TR).contains(q) && !s.equalsIgnoreCase(text.strip()))
                    .sorted((a, b) -> Boolean.compare(!a.toLowerCase(TR).startsWith(q), !b.toLowerCase(TR).startsWith(q)))
                    .limit(MAX)
                    .toList();
            if (matches.isEmpty()) {
                menu.hide();
                return;
            }
            menu.getItems().setAll(matches.stream().map(m -> {
                Label l = new Label(m);
                l.setPrefWidth(Math.max(200, field.getWidth() - 24));
                CustomMenuItem item = new CustomMenuItem(l, true);
                item.setOnAction(e -> {
                    picking[0] = true;
                    field.setText(m);
                    field.positionCaret(m.length());
                    picking[0] = false;
                    menu.hide();
                });
                return item;
            }).toList());
            if (!menu.isShowing()) menu.show(field, Side.BOTTOM, 0, 2);
        });
        field.focusedProperty().addListener((obs, was, is) -> {
            if (!is) menu.hide();
        });
    }
}
