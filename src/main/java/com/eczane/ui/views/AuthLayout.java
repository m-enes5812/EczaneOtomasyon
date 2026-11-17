package com.eczane.ui.views;

import com.eczane.ui.Icons;
import com.eczane.ui.Theme;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.Tooltip;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import org.kordamp.ikonli.Ikon;
import org.kordamp.ikonli.javafx.FontIcon;
import org.kordamp.ikonli.materialdesign2.MaterialDesignF;
import org.kordamp.ikonli.materialdesign2.MaterialDesignM;
import org.kordamp.ikonli.materialdesign2.MaterialDesignP;
import org.kordamp.ikonli.materialdesign2.MaterialDesignW;

import java.util.Objects;

/** Giriş ve kayıt ekranlarının ortak iki sütunlu düzeni. */
final class AuthLayout {

    private static final Image LOGO = new Image(Objects.requireNonNull(
            AuthLayout.class.getResourceAsStream("/com/eczane/images/logo.png")), 112, 112, true, true);

    private AuthLayout() {
    }

    static HBox wrap(Node form) {
        VBox brand = brand();
        brand.setPrefWidth(460);
        brand.setMinWidth(380);

        StackPane formHost = new StackPane(form);
        formHost.getStyleClass().add("auth-form");
        formHost.setPadding(new Insets(56, 32, 32, 32));
        HBox.setHgrow(formHost, Priority.ALWAYS);

        StackPane right = new StackPane(formHost, themeButton());
        HBox.setHgrow(right, Priority.ALWAYS);
        return new HBox(brand, right);
    }

    private static Button themeButton() {
        FontIcon icon = Icons.of(Theme.isDark() ? MaterialDesignW.WHITE_BALANCE_SUNNY : MaterialDesignW.WEATHER_NIGHT, 18);
        Button b = new Button(null, icon);
        b.getStyleClass().addAll("button-icon", "flat");
        b.setTooltip(new Tooltip("Açık / koyu tema"));
        b.setOnAction(e -> {
            Theme.toggle();
            icon.setIconCode(Theme.isDark() ? MaterialDesignW.WHITE_BALANCE_SUNNY : MaterialDesignW.WEATHER_NIGHT);
        });
        StackPane.setAlignment(b, Pos.TOP_RIGHT);
        StackPane.setMargin(b, new Insets(16));
        return b;
    }

    private static VBox brand() {
        ImageView logo = new ImageView(LOGO);
        logo.setFitWidth(96);
        logo.setFitHeight(96);
        StackPane logoCircle = new StackPane(logo);
        logoCircle.getStyleClass().add("logo-circle");
        logoCircle.setMaxSize(Region.USE_PREF_SIZE, Region.USE_PREF_SIZE);

        Label title = new Label("Eczane\nOtomasyon");
        title.getStyleClass().add("brand-title");
        Label subtitle = new Label("Reçete, stok ve hasta iletişimi tek bir yerde.");
        subtitle.getStyleClass().add("brand-subtitle");
        subtitle.setWrapText(true);

        VBox features = new VBox(18,
                feature(MaterialDesignF.FILE_DOCUMENT_EDIT_OUTLINE, "Dijital reçete", "Doktor yazar, eczane anında görür."),
                feature(MaterialDesignP.PACKAGE_VARIANT_CLOSED, "Akıllı stok", "Kritik seviye uyarıları ve tam hareket geçmişi."),
                feature(MaterialDesignM.MESSAGE_TEXT_OUTLINE, "Reçete sohbeti", "Doktor, hasta ve eczacı aynı yerde konuşur."));

        Region spacer = new Region();
        VBox.setVgrow(spacer, Priority.ALWAYS);
        Label foot = new Label("Veriler bu bilgisayarda şifreli özetlerle saklanır.");
        foot.getStyleClass().add("footnote");

        VBox box = new VBox(22, logoCircle, title, subtitle, new Region(), features, spacer, foot);
        box.getStyleClass().add("auth-brand");
        return box;
    }

    private static HBox feature(Ikon ikon, String title, String text) {
        StackPane icon = new StackPane(Icons.of(ikon, 20));
        icon.getStyleClass().add("feature-icon");
        Label t = new Label(title);
        t.getStyleClass().add("feature-title");
        Label d = new Label(text);
        d.getStyleClass().add("feature-text");
        d.setWrapText(true);
        HBox row = new HBox(14, icon, new VBox(2, t, d));
        row.setAlignment(Pos.CENTER_LEFT);
        return row;
    }

    /** Formun üstündeki hata kutusu. */
    static Label errorBox() {
        Label l = new Label();
        l.getStyleClass().add("form-error");
        l.setGraphic(Icons.of(org.kordamp.ikonli.materialdesign2.MaterialDesignA.ALERT_CIRCLE_OUTLINE, 16));
        l.setWrapText(true);
        l.setMaxWidth(Double.MAX_VALUE);
        l.setManaged(false);
        l.setVisible(false);
        return l;
    }

    static void showError(Label box, String msg) {
        boolean show = msg != null && !msg.isBlank();
        box.setText(msg);
        box.setManaged(show);
        box.setVisible(show);
    }
}
