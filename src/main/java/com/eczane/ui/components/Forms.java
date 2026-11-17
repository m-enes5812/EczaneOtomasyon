package com.eczane.ui.components;

import atlantafx.base.controls.CustomTextField;
import atlantafx.base.controls.PasswordTextField;
import com.eczane.ui.Icons;
import javafx.scene.Cursor;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.control.TextFormatter;
import javafx.scene.layout.VBox;
import org.kordamp.ikonli.Ikon;
import org.kordamp.ikonli.javafx.FontIcon;
import org.kordamp.ikonli.materialdesign2.MaterialDesignE;
import org.kordamp.ikonli.materialdesign2.MaterialDesignL;

/** Form alanı yardımcıları. */
public final class Forms {

    private Forms() {
    }

    /** Etiket + alan + (isteğe bağlı) yardım metni. */
    public static VBox field(String label, Node input) {
        return field(label, input, null);
    }

    public static VBox field(String label, Node input, Node hint) {
        Label l = new Label(label);
        l.getStyleClass().add("field-label");
        l.setLabelFor(input);
        VBox box = new VBox(6, l, input);
        if (hint != null) box.getChildren().add(hint);
        box.getStyleClass().add("field");
        return box;
    }

    public static CustomTextField text(String prompt, Ikon left) {
        CustomTextField f = new CustomTextField();
        f.setPromptText(prompt);
        if (left != null) f.setLeft(Icons.of(left, 16, "field-icon"));
        return f;
    }

    /** Sadece rakam kabul eden, en fazla 11 haneli T.C. Kimlik alanı. */
    public static CustomTextField tc(String prompt, Ikon left) {
        CustomTextField f = text(prompt, left);
        f.setTextFormatter(new TextFormatter<String>(ch -> {
            String next = ch.getControlNewText();
            return next.matches("\\d{0,11}") ? ch : null;
        }));
        return f;
    }

    /** Göster/gizle düğmeli şifre alanı. Değer için {@link PasswordTextField#getPassword()} kullanılır. */
    public static PasswordTextField password(String prompt) {
        PasswordTextField f = new PasswordTextField();
        f.setPromptText(prompt);
        f.setLeft(Icons.of(MaterialDesignL.LOCK_OUTLINE, 16, "field-icon"));
        FontIcon eye = Icons.of(MaterialDesignE.EYE_OFF_OUTLINE, 16, "field-icon");
        eye.setCursor(Cursor.HAND);
        eye.setOnMouseClicked(e -> {
            f.setRevealPassword(!f.getRevealPassword());
            eye.setIconCode(f.getRevealPassword() ? MaterialDesignE.EYE_OUTLINE : MaterialDesignE.EYE_OFF_OUTLINE);
        });
        f.setRight(eye);
        return f;
    }

    /** Sadece tam sayı kabul eden alan. */
    public static void integerOnly(TextField f, boolean allowNegative) {
        String re = allowNegative ? "-?\\d{0,6}" : "\\d{0,6}";
        f.setTextFormatter(new TextFormatter<String>(ch -> ch.getControlNewText().matches(re) ? ch : null));
    }

    public static Label hint(String text) {
        Label l = new Label(text);
        l.getStyleClass().add("field-hint");
        l.setWrapText(true);
        return l;
    }

    /** Alanın altında gösterilen hata/başarı mesajı. */
    public static Label status() {
        Label l = new Label();
        l.getStyleClass().add("field-status");
        l.setWrapText(true);
        l.setManaged(false);
        l.setVisible(false);
        return l;
    }

    public static void setStatus(Label l, String text, boolean ok) {
        boolean show = text != null && !text.isBlank();
        l.setText(text);
        l.getStyleClass().removeAll("ok", "bad");
        if (show) l.getStyleClass().add(ok ? "ok" : "bad");
        l.setManaged(show);
        l.setVisible(show);
    }

    public static void markInvalid(Node n, boolean invalid) {
        n.pseudoClassStateChanged(javafx.css.PseudoClass.getPseudoClass("danger"), invalid);
    }
}
