package com.eczane;

import com.eczane.ui.App;
import javafx.application.Application;

/** Giriş noktası. JavaFX'in sınıf yolundan sorunsuz başlaması için ayrı tutulur. */
public final class Launcher {

    private Launcher() {
    }

    public static void main(String[] args) {
        Application.launch(App.class, args);
    }
}
