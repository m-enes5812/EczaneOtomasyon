package com.eczane.ui.views;

import javafx.scene.Node;

/** Ana ekrandaki bir sayfa (Genel Bakış, Reçeteler, Stok…). */
interface Page {

    String title();

    String subtitle();

    Node view();

    /** Sayfa her gösterildiğinde verileri yeniler. */
    void refresh();

    /** Sayfa gizlendiğinde (zamanlayıcıları durdurmak için). */
    default void onHide() {
    }

    /** Üst çubukta sayfaya özel butonlar. */
    default Node actions() {
        return null;
    }
}
