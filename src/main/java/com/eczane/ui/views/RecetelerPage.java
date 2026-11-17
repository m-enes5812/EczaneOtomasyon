package com.eczane.ui.views;

import atlantafx.base.controls.CustomTextField;
import com.eczane.model.ReceteDurum;
import com.eczane.model.ReceteOzet;
import com.eczane.model.Role;
import com.eczane.ui.Fmt;
import com.eczane.ui.Fx;
import com.eczane.ui.components.Badges;
import com.eczane.ui.components.EmptyState;
import com.eczane.ui.components.Forms;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import org.kordamp.ikonli.materialdesign2.MaterialDesignF;
import org.kordamp.ikonli.materialdesign2.MaterialDesignM;

import java.util.List;
import java.util.Locale;

/** Ana-detay düzeni: solda filtrelenebilir reçete listesi, sağda seçili reçetenin ayrıntısı ve sohbeti. */
final class RecetelerPage implements Page {

    private static final Locale TR = Locale.forLanguageTag("tr");

    private final MainView.Context ctx;
    private final ObservableList<ReceteOzet> all = FXCollections.observableArrayList();
    private final FilteredList<ReceteOzet> filtered = new FilteredList<>(all);
    private final ListView<ReceteOzet> list = new ListView<>(filtered);
    private final CustomTextField search = Forms.text("Ara: hasta, doktor, reçete no…", MaterialDesignM.MAGNIFY);
    private final ToggleGroup filterGroup = new ToggleGroup();
    private final Label countLabel = new Label();
    private final StackPane detailHost = new StackPane();
    private final EmptyState noSelection = new EmptyState(MaterialDesignF.FILE_DOCUMENT_OUTLINE, "Bir reçete seç",
            "Ayrıntıları, ilaçları ve mesajları görmek için soldaki listeden bir reçeteye tıkla.");
    private final EmptyState emptyList;
    private final HBox root;
    private ReceteDetailPane detail;
    private Long pendingSelect;
    /** Liste yenilenirken seçim olaylarını yok saymak için (detay paneli baştan kurulmasın). */
    private boolean updating;

    RecetelerPage(MainView.Context ctx) {
        this.ctx = ctx;
        Role role = ctx.user().role();

        emptyList = new EmptyState(MaterialDesignF.FILE_DOCUMENT_OUTLINE, "Reçete bulunamadı",
                role == Role.DOKTOR ? "Yazdığın reçeteler burada listelenir." : role == Role.HASTA
                        ? "Doktorunun yazdığı reçeteler burada görünür." : "Eczanene gönderilen reçeteler burada görünür.");
        list.setPlaceholder(emptyList);
        list.getStyleClass().add("recete-list");
        list.setCellFactory(v -> new ReceteCell());
        VBox.setVgrow(list, Priority.ALWAYS);

        HBox filters = new HBox(0,
                filter("Tümü", null, "left-pill"),
                filter("Bekleyen", ReceteDurum.AKTIF, "center-pill"),
                filter("Verildi", ReceteDurum.VERILDI, "center-pill"),
                filter("İptal", ReceteDurum.IPTAL, "right-pill"));
        filters.getStyleClass().add("filter-bar");
        // eczacı için varsayılan: bekleyenler
        filterGroup.getToggles().get(role == Role.ECZACI ? 1 : 0).setSelected(true);
        filterGroup.selectedToggleProperty().addListener((o, old, sel) -> {
            if (sel == null && old != null) old.setSelected(true);
            applyFilter();
        });
        search.textProperty().addListener((o, a, b) -> applyFilter());

        countLabel.getStyleClass().add("muted");
        countLabel.setStyle("-fx-font-size: 12px;");

        VBox left = new VBox(12, search, filters, countLabel, list);
        left.setPrefWidth(400);
        left.setMinWidth(340);
        left.setMaxWidth(440);

        detailHost.getChildren().setAll(noSelection);
        detailHost.getStyleClass().add("card");
        detailHost.setStyle("-fx-padding: 0;");
        HBox.setHgrow(detailHost, Priority.ALWAYS);

        list.getSelectionModel().selectedItemProperty().addListener((o, a, sel) -> {
            if (!updating) showDetail(sel);
        });

        root = new HBox(20, left, detailHost);
        applyFilter();
    }

    private ToggleButton filter(String text, ReceteDurum durum, String pill) {
        ToggleButton b = new ToggleButton(text);
        b.setUserData(durum);
        b.setToggleGroup(filterGroup);
        b.getStyleClass().add(pill);
        return b;
    }

    private void applyFilter() {
        ReceteDurum d = filterGroup.getSelectedToggle() == null ? null : (ReceteDurum) filterGroup.getSelectedToggle().getUserData();
        String q = search.getText() == null ? "" : search.getText().strip().toLowerCase(TR).replace("#", "");
        filtered.setPredicate(r -> (d == null || r.durum() == d) && (q.isEmpty() || matches(r, q)));
        countLabel.setText(filtered.size() + " reçete" + (all.size() != filtered.size() ? " (toplam " + all.size() + ")" : ""));
    }

    private static boolean matches(ReceteOzet r, String q) {
        return String.valueOf(r.id()).equals(q)
                || contains(r.hastaAdi(), q) || contains(r.doktorAdi(), q) || contains(r.eczaneAdi(), q) || contains(r.aciklama(), q);
    }

    private static boolean contains(String s, String q) {
        return s != null && s.toLowerCase(TR).contains(q);
    }

    @Override public String title() {
        return switch (ctx.user().role()) {
            case DOKTOR -> "Yazdığım Reçeteler";
            case HASTA -> "Reçetelerim";
            case ECZACI -> "Gelen Reçeteler";
        };
    }

    @Override public String subtitle() {
        return switch (ctx.user().role()) {
            case DOKTOR -> "Reçetelerin durumu ve hasta/eczane ile mesajlaşma";
            case HASTA -> "Reçetelerin, ilaçların ve eczaneyle mesajlaşma";
            case ECZACI -> "Reçeteleri kontrol et, ilaçları teslim et";
        };
    }

    @Override public Node view() { return root; }

    @Override
    public void refresh() {
        Fx.run(() -> ctx.services().recete().list(ctx.user()), this::setItems);
    }

    /** Başka bir sayfadan belirli bir reçeteye gitmek için. */
    void select(long id) {
        pendingSelect = id;
        trySelectPending();
    }

    @Override
    public void onHide() {
        if (detail != null) detail.stop();
    }

    private void setItems(List<ReceteOzet> items) {
        ReceteOzet sel = list.getSelectionModel().getSelectedItem();
        Long selId = sel == null ? null : sel.id();
        updating = true;
        try {
            all.setAll(items);
            applyFilter();
            if (selId != null) {
                filtered.stream().filter(r -> r.id() == selId).findFirst()
                        .ifPresent(r -> list.getSelectionModel().select(r));
            }
        } finally {
            updating = false;
        }
        ReceteOzet now = list.getSelectionModel().getSelectedItem();
        if (now == null || detail == null || now.id() != detail.receteId()) showDetail(now);
        trySelectPending();
    }

    private void trySelectPending() {
        if (pendingSelect == null) return;
        for (ReceteOzet r : all) {
            if (r.id() == pendingSelect) {
                if (!filtered.contains(r)) {
                    filterGroup.getToggles().getFirst().setSelected(true);
                    search.clear();
                }
                list.getSelectionModel().select(r);
                list.scrollTo(r);
                pendingSelect = null;
                return;
            }
        }
    }

    private void showDetail(ReceteOzet sel) {
        if (sel == null) {
            if (detail != null) detail.stop();
            detail = null;
            detailHost.getChildren().setAll(noSelection);
            return;
        }
        if (detail != null && detail.receteId() == sel.id()) return;
        if (detail != null) detail.stop();
        detail = new ReceteDetailPane(ctx, sel.id(), this::refresh);
        detailHost.getChildren().setAll(detail);
    }

    // ---------------------------------------------------------------- liste hücresi

    private final class ReceteCell extends ListCell<ReceteOzet> {
        @Override
        protected void updateItem(ReceteOzet r, boolean empty) {
            super.updateItem(r, empty);
            if (empty || r == null) {
                setGraphic(null);
                setText(null);
                return;
            }
            Role role = ctx.user().role();
            Label id = new Label("#" + r.id());
            id.getStyleClass().add("row-id");
            Label who = new Label(role == Role.HASTA ? "Dr. " + r.doktorAdi() : r.hastaAdi());
            who.getStyleClass().add("row-title");
            Region sp = new Region();
            HBox.setHgrow(sp, Priority.ALWAYS);
            HBox top = new HBox(8, id, who, sp);
            top.setAlignment(Pos.CENTER_LEFT);
            if (r.okunmamis() > 0) top.getChildren().add(Badges.count(r.okunmamis()));
            top.getChildren().add(Badges.durum(r.durum()));

            String metaText = switch (role) {
                case DOKTOR, HASTA -> r.eczaneAdi() == null ? "Eczane yok" : r.eczaneAdi();
                case ECZACI -> "Dr. " + r.doktorAdi();
            } + "  ·  " + r.kalemSayisi() + " ilaç  ·  " + Fmt.relative(r.tarih());
            Label meta = new Label(metaText);
            meta.getStyleClass().add("row-meta");

            VBox box = new VBox(6, top, meta);
            if (r.aciklama() != null && !r.aciklama().isBlank()) {
                Label note = new Label(r.aciklama().replace('\n', ' '));
                note.getStyleClass().add("row-meta");
                note.setMaxWidth(320);
                box.getChildren().add(note);
            }
            box.getStyleClass().add("recete-row");
            if (r.okunmamis() > 0) box.getStyleClass().add("unread");
            box.setMaxWidth(Double.MAX_VALUE);
            box.prefWidthProperty().bind(list.widthProperty().subtract(22));
            setText(null);
            setGraphic(box);
        }
    }
}
