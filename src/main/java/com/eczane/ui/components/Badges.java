package com.eczane.ui.components;

import com.eczane.model.ReceteDurum;
import com.eczane.model.StokKalemi;
import com.eczane.ui.Icons;
import javafx.scene.control.Label;
import org.kordamp.ikonli.materialdesign2.MaterialDesignC;
import org.kordamp.ikonli.materialdesign2.MaterialDesignP;

/** Durum etiketleri (renkli küçük "hap"lar). */
public final class Badges {

    private Badges() {
    }

    public static Label durum(ReceteDurum d) {
        Label l = new Label(d.label());
        l.getStyleClass().addAll("badge", switch (d) {
            case AKTIF -> "badge-warning";
            case VERILDI -> "badge-success";
            case IPTAL -> "badge-danger";
        });
        l.setGraphic(Icons.of(switch (d) {
            case AKTIF -> MaterialDesignC.CLOCK_OUTLINE;
            case VERILDI -> MaterialDesignC.CHECK_CIRCLE_OUTLINE;
            case IPTAL -> MaterialDesignC.CLOSE_CIRCLE_OUTLINE;
        }, 13));
        return l;
    }

    public static Label stok(StokKalemi k) {
        return stok(k.miktar(), k.minMiktar());
    }

    public static Label stok(int miktar, int min) {
        Label l = new Label(String.valueOf(miktar));
        l.getStyleClass().addAll("badge", miktar <= 0 ? "badge-danger" : miktar <= min ? "badge-warning" : "badge-neutral");
        return l;
    }

    public static Label count(int n) {
        Label l = new Label(n > 99 ? "99+" : String.valueOf(n));
        l.getStyleClass().add("count-badge");
        l.setVisible(n > 0);
        l.setManaged(n > 0);
        return l;
    }

    public static Label neutral(String text) {
        Label l = new Label(text);
        l.getStyleClass().addAll("badge", "badge-neutral");
        return l;
    }

    public static Label pill(String text) {
        Label l = new Label(text, Icons.of(MaterialDesignP.PILL, 13));
        l.getStyleClass().addAll("badge", "badge-accent");
        return l;
    }
}
