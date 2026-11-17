package com.eczane.ui;

import org.kordamp.ikonli.Ikon;
import org.kordamp.ikonli.javafx.FontIcon;

/** İkon oluşturma kısayolu. */
public final class Icons {

    private Icons() {
    }

    public static FontIcon of(Ikon ikon) {
        return new FontIcon(ikon);
    }

    public static FontIcon of(Ikon ikon, int size) {
        FontIcon i = new FontIcon(ikon);
        i.setIconSize(size);
        return i;
    }

    public static FontIcon of(Ikon ikon, int size, String styleClass) {
        FontIcon i = of(ikon, size);
        i.getStyleClass().add(styleClass);
        return i;
    }
}
