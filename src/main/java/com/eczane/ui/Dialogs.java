package com.eczane.ui;

import javafx.scene.control.Alert;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextInputDialog;
import javafx.stage.Window;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.Optional;

/** Standart onay/uyarı pencereleri. */
public final class Dialogs {

    private static Window owner;

    private Dialogs() {
    }

    public static void setOwner(Window w) { owner = w; }

    public static Window owner() { return owner; }

    public static boolean confirm(String title, String header, String content, String okText) {
        ButtonType ok = new ButtonType(okText, ButtonBar.ButtonData.OK_DONE);
        ButtonType cancel = new ButtonType("Vazgeç", ButtonBar.ButtonData.CANCEL_CLOSE);
        Alert a = alert(Alert.AlertType.CONFIRMATION, title, header, content, ok, cancel);
        return a.showAndWait().filter(b -> b == ok).isPresent();
    }

    public static void warn(String title, String content) {
        alert(Alert.AlertType.WARNING, title, null, content, ButtonType.OK).showAndWait();
    }

    public static void info(String title, String header, String content) {
        alert(Alert.AlertType.INFORMATION, title, header, content, ButtonType.OK).showAndWait();
    }

    public static Optional<String> prompt(String title, String header, String label, String initial) {
        TextInputDialog d = new TextInputDialog(initial);
        d.setTitle(title);
        d.setHeaderText(header);
        d.setContentText(label);
        prepare(d);
        return d.showAndWait();
    }

    public static void unexpected(Throwable e) {
        Alert a = alert(Alert.AlertType.ERROR, "Beklenmeyen hata", "Bir şeyler ters gitti",
                "İşlem tamamlanamadı. Ayrıntılar günlük dosyasına yazıldı.\n\n" + e.getClass().getSimpleName()
                        + (e.getMessage() == null ? "" : ": " + e.getMessage()), ButtonType.OK);
        StringWriter sw = new StringWriter();
        e.printStackTrace(new PrintWriter(sw));
        TextArea area = new TextArea(sw.toString());
        area.setEditable(false);
        area.setPrefRowCount(12);
        a.getDialogPane().setExpandableContent(area);
        a.showAndWait();
    }

    private static Alert alert(Alert.AlertType type, String title, String header, String content, ButtonType... buttons) {
        Alert a = new Alert(type, "", buttons);
        a.setTitle(title);
        a.setHeaderText(header);
        Label l = new Label(content);
        l.setWrapText(true);
        l.setMaxWidth(440);
        a.getDialogPane().setContent(l);
        prepare(a);
        return a;
    }

    private static void prepare(javafx.scene.control.Dialog<?> d) {
        if (owner != null) d.initOwner(owner);
        if (d.getDialogPane().getScene() != null) Theme.style(d.getDialogPane().getScene());
        d.getDialogPane().getStyleClass().add("app-dialog");
    }
}
