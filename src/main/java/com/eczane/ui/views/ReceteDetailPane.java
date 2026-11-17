package com.eczane.ui.views;

import com.eczane.model.ReceteDetay;
import com.eczane.model.ReceteDurum;
import com.eczane.model.ReceteKalemi;
import com.eczane.model.Role;
import com.eczane.model.User;
import com.eczane.service.ReceteService.KalemStok;
import com.eczane.ui.Dialogs;
import com.eczane.ui.Fmt;
import com.eczane.ui.Fx;
import com.eczane.ui.Icons;
import com.eczane.ui.Toasts;
import com.eczane.ui.components.Badges;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import org.kordamp.ikonli.materialdesign2.MaterialDesignA;
import org.kordamp.ikonli.materialdesign2.MaterialDesignC;
import org.kordamp.ikonli.materialdesign2.MaterialDesignN;
import org.kordamp.ikonli.materialdesign2.MaterialDesignP;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Seçili reçetenin ayrıntısı: bilgiler, ilaç tablosu, işlemler ve sohbet. */
final class ReceteDetailPane extends StackPane {

    private final MainView.Context ctx;
    private final long receteId;
    private final Runnable onChanged;
    private final VBox body = new VBox(16);
    private ChatPane chat;

    ReceteDetailPane(MainView.Context ctx, long receteId, Runnable onChanged) {
        this.ctx = ctx;
        this.receteId = receteId;
        this.onChanged = onChanged;
        body.setPadding(new Insets(20));
        ProgressIndicator pi = new ProgressIndicator();
        pi.setPrefSize(32, 32);
        getChildren().setAll(pi);
        load();
    }

    long receteId() { return receteId; }

    void stop() {
        if (chat != null) chat.stop();
    }

    private void load() {
        User u = ctx.user();
        Fx.run(() -> {
            ReceteDetay d = ctx.services().recete().detay(u, receteId);
            List<KalemStok> stok = u.is(Role.ECZACI) ? ctx.services().recete().stokDurumu(u, receteId) : List.of();
            return new Loaded(d, stok);
        }, this::render, null, ex -> {
            Toasts.error(ex.getMessage());
            getChildren().setAll(new Label(ex.getMessage()));
        });
    }

    private record Loaded(ReceteDetay d, List<KalemStok> stok) {
    }

    private void render(Loaded data) {
        ReceteDetay d = data.d();
        User u = ctx.user();

        // ---- başlık
        Label id = new Label("Reçete #" + d.id());
        id.getStyleClass().add("detail-id");
        Label date = new Label(Fmt.full(d.tarih()));
        date.getStyleClass().add("muted");
        Region sp = new Region();
        HBox.setHgrow(sp, Priority.ALWAYS);
        HBox header = new HBox(12, new VBox(2, id, date), sp, Badges.durum(d.durum()));
        header.setAlignment(Pos.CENTER_LEFT);
        header.getStyleClass().add("detail-header");

        // ---- bilgiler
        GridPane info = new GridPane();
        info.setHgap(32);
        info.setVgap(4);
        addInfo(info, 0, "Hasta", d.hastaAdi() + (u.is(Role.HASTA) ? "" : "  ·  " + mask(d.hastaTc())));
        addInfo(info, 1, "Doktor", "Dr. " + d.doktorAdi());
        addInfo(info, 2, "Eczane", d.eczaneAdi() == null ? "—" : d.eczaneAdi());

        VBox top = new VBox(14, header, info);
        if (d.aciklama() != null && !d.aciklama().isBlank()) {
            Label note = new Label(d.aciklama());
            note.setWrapText(true);
            note.setGraphic(Icons.of(MaterialDesignN.NOTE_TEXT_OUTLINE, 16));
            note.getStyleClass().add("note-box");
            note.setMaxWidth(Double.MAX_VALUE);
            top.getChildren().add(note);
        }

        // ---- ilaç tablosu
        TableView<ReceteKalemi> table = itemsTable(d, data.stok());

        // ---- işlemler
        HBox actions = actions(d, data.stok());

        // ---- sohbet
        chat = new ChatPane(ctx, receteId, onChanged);
        VBox.setVgrow(chat, Priority.ALWAYS);

        Label medsTitle = new Label("İLAÇLAR (" + d.kalemler().size() + ")");
        medsTitle.getStyleClass().add("section-title");
        Label chatTitle = new Label("MESAJLAR");
        chatTitle.getStyleClass().add("section-title");

        body.getChildren().setAll(top, new VBox(8, medsTitle, table));
        if (actions != null) body.getChildren().add(actions);
        body.getChildren().addAll(chatTitle, chat);
        // Küçük ekranlarda kaydırılabilir; yer varsa sohbet kalan alanı doldurur.
        javafx.scene.control.ScrollPane sp2 = new javafx.scene.control.ScrollPane(body);
        sp2.setFitToWidth(true);
        sp2.setFitToHeight(true);
        sp2.getStyleClass().add("page-scroll");
        getChildren().setAll(sp2);
    }

    private static String mask(String tc) {
        if (tc == null || tc.length() < 5) return tc == null ? "" : tc;
        return tc.substring(0, 3) + "******" + tc.substring(tc.length() - 2);
    }

    private static void addInfo(GridPane g, int col, String label, String value) {
        Label l = new Label(label);
        l.getStyleClass().add("info-label");
        Label v = new Label(value);
        v.getStyleClass().add("info-value");
        g.add(l, col, 0);
        g.add(v, col, 1);
    }

    private TableView<ReceteKalemi> itemsTable(ReceteDetay d, List<KalemStok> stok) {
        Map<Long, Integer> stokMap = new HashMap<>();
        for (KalemStok k : stok) stokMap.put(k.kalem().ilacId(), k.stok());

        TableView<ReceteKalemi> t = new TableView<>();
        t.getItems().setAll(d.kalemler());
        t.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        t.getStyleClass().addAll("bordered", "striped");

        TableColumn<ReceteKalemi, String> ilac = new TableColumn<>("İlaç");
        ilac.setCellValueFactory(c -> new ReadOnlyObjectWrapper<>(c.getValue().ilacAdi()));
        ilac.setPrefWidth(220);
        TableColumn<ReceteKalemi, Number> adet = new TableColumn<>("Adet");
        adet.setCellValueFactory(c -> new ReadOnlyObjectWrapper<>(c.getValue().adet()));
        adet.setPrefWidth(70);
        adet.setStyle("-fx-alignment: CENTER;");
        TableColumn<ReceteKalemi, String> kullanim = new TableColumn<>("Kullanım");
        kullanim.setCellValueFactory(c -> new ReadOnlyObjectWrapper<>(
                c.getValue().kullanim() == null ? "—" : c.getValue().kullanim()));
        kullanim.setPrefWidth(200);
        t.getColumns().add(ilac);
        t.getColumns().add(adet);

        if (ctx.user().is(Role.ECZACI) && d.durum() == ReceteDurum.AKTIF) {
            TableColumn<ReceteKalemi, Number> st = new TableColumn<>("Stokta");
            st.setCellValueFactory(c -> new ReadOnlyObjectWrapper<>(stokMap.getOrDefault(c.getValue().ilacId(), 0)));
            st.setPrefWidth(110);
            st.setCellFactory(col -> new TableCell<>() {
                @Override
                protected void updateItem(Number n, boolean empty) {
                    super.updateItem(n, empty);
                    getStyleClass().removeAll("stock-ok", "stock-bad");
                    if (empty || n == null || getTableRow() == null || getTableRow().getItem() == null) {
                        setText(null);
                        setGraphic(null);
                        return;
                    }
                    boolean ok = n.intValue() >= getTableRow().getItem().adet();
                    setText(String.valueOf(n.intValue()));
                    setGraphic(Icons.of(ok ? MaterialDesignC.CHECK_CIRCLE : MaterialDesignA.ALERT_CIRCLE, 14));
                    getStyleClass().add(ok ? "stock-ok" : "stock-bad");
                }
            });
            t.getColumns().add(st);
        }
        t.getColumns().add(kullanim);

        double rows = Math.min(4, Math.max(1, d.kalemler().size()));
        t.setFixedCellSize(36);
        t.setPrefHeight(38 + rows * 36 + 4);
        t.setMinHeight(t.getPrefHeight());
        return t;
    }

    private HBox actions(ReceteDetay d, List<KalemStok> stok) {
        if (d.durum() != ReceteDurum.AKTIF) return null;
        User u = ctx.user();
        HBox box = new HBox(10);
        box.setAlignment(Pos.CENTER_LEFT);

        if (u.is(Role.ECZACI)) {
            boolean yeterli = stok.stream().allMatch(KalemStok::yeterli);
            Button ver = new Button("İlaçları Teslim Et", Icons.of(MaterialDesignP.PACKAGE_VARIANT_CLOSED_CHECK, 18));
            ver.getStyleClass().add("success");
            ver.setOnAction(e -> ver(d, ver));
            ver.setDisable(!yeterli);
            Label warn = new Label(yeterli ? "Tüm ilaçlar stokta mevcut." : "Bazı ilaçlar için stok yetersiz.");
            warn.getStyleClass().add(yeterli ? "success-text" : "danger-text");
            warn.setGraphic(Icons.of(yeterli ? MaterialDesignC.CHECK_CIRCLE_OUTLINE : MaterialDesignA.ALERT_OUTLINE, 16));
            box.getChildren().addAll(ver, cancelButton(), warn);
        } else if (u.is(Role.DOKTOR)) {
            Label info = new Label("Reçete eczanede teslim edilmeyi bekliyor.");
            info.getStyleClass().add("muted");
            box.getChildren().addAll(cancelButton(), info);
        } else {
            Label info = new Label("İlaçlarını " + (d.eczaneAdi() == null ? "eczaneden" : d.eczaneAdi() + " üzerinden")
                    + " T.C. Kimlik No'nla teslim alabilirsin.");
            info.getStyleClass().add("note-box");
            info.setGraphic(Icons.of(org.kordamp.ikonli.materialdesign2.MaterialDesignI.INFORMATION_OUTLINE, 16));
            info.setWrapText(true);
            box.getChildren().add(info);
        }
        return box;
    }

    private Button cancelButton() {
        Button b = new Button("Reçeteyi İptal Et", Icons.of(MaterialDesignC.CLOSE_CIRCLE_OUTLINE, 18));
        b.getStyleClass().addAll("danger", "button-outlined");
        b.setOnAction(e -> {
            var reason = Dialogs.prompt("Reçeteyi iptal et", "Reçete #" + receteId + " iptal edilecek",
                    "İptal sebebi (isteğe bağlı):", "");
            if (reason.isEmpty()) return;
            Fx.run(() -> {
                ctx.services().recete().iptal(ctx.user(), receteId, reason.get());
                return null;
            }, v -> {
                Toasts.success("Reçete #" + receteId + " iptal edildi.");
                changed();
            }, b, null);
        });
        return b;
    }

    private void ver(ReceteDetay d, Button btn) {
        StringBuilder sb = new StringBuilder();
        for (ReceteKalemi k : d.kalemler()) sb.append("• ").append(k.ilacAdi()).append(" × ").append(k.adet()).append('\n');
        if (!Dialogs.confirm("İlaçları teslim et", "Reçete #" + d.id() + " — " + d.hastaAdi(),
                sb + "\nİlaçlar hastaya verilecek ve stoktan düşülecek.", "Teslim Et")) return;
        Fx.run(() -> {
            ctx.services().recete().ver(ctx.user(), receteId);
            return null;
        }, v -> {
            Toasts.success("Reçete #" + receteId + " teslim edildi, stok güncellendi.");
            changed();
        }, btn, ex -> Dialogs.warn("Teslim edilemedi", ex.getMessage()));
    }

    private void changed() {
        stop();
        load();
        onChanged.run();
    }
}
