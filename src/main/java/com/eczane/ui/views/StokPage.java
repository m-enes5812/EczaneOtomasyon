package com.eczane.ui.views;

import atlantafx.base.controls.CustomTextField;
import atlantafx.base.controls.ToggleSwitch;
import com.eczane.model.StokKalemi;
import com.eczane.service.StokService;
import com.eczane.ui.Fmt;
import com.eczane.ui.Fx;
import com.eczane.ui.Icons;
import com.eczane.ui.Toasts;
import com.eczane.ui.components.AutoComplete;
import com.eczane.ui.components.Badges;
import com.eczane.ui.components.EmptyState;
import com.eczane.ui.components.Forms;
import com.eczane.ui.components.StatCard;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.collections.transformation.SortedList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.Spinner;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import org.kordamp.ikonli.materialdesign2.MaterialDesignA;
import org.kordamp.ikonli.materialdesign2.MaterialDesignC;
import org.kordamp.ikonli.materialdesign2.MaterialDesignM;
import org.kordamp.ikonli.materialdesign2.MaterialDesignP;

import java.util.List;
import java.util.Locale;

/** Eczane stok listesi; giriş/çıkış ve kritik seviye ayarı. */
final class StokPage implements Page {

    private static final Locale TR = Locale.forLanguageTag("tr");

    private final MainView.Context ctx;
    private final ObservableList<StokKalemi> all = FXCollections.observableArrayList();
    private final FilteredList<StokKalemi> filtered = new FilteredList<>(all);
    private final TableView<StokKalemi> table = new TableView<>();
    private final CustomTextField search = Forms.text("İlaç ara…", MaterialDesignM.MAGNIFY);
    private final ToggleSwitch onlyCritical = new ToggleSwitch("Yalnızca kritik");
    private final StatCard cesit = new StatCard(MaterialDesignP.PILL, "İlaç çeşidi", "accent");
    private final StatCard toplam = new StatCard(MaterialDesignP.PACKAGE_VARIANT, "Toplam kutu", "info");
    private final StatCard kritik = new StatCard(MaterialDesignA.ALERT_OUTLINE, "Kritik seviyede", "warning");
    private final StatCard tukenen = new StatCard(MaterialDesignC.CLOSE_CIRCLE_OUTLINE, "Tükenen", "danger");
    private final VBox root;

    StokPage(MainView.Context ctx) {
        this.ctx = ctx;

        search.textProperty().addListener((o, a, b) -> applyFilter());
        onlyCritical.selectedProperty().addListener((o, a, b) -> applyFilter());
        search.setPrefWidth(300);
        Region sp = new Region();
        HBox.setHgrow(sp, Priority.ALWAYS);
        Button in = new Button("Stok Girişi", Icons.of(MaterialDesignP.PLUS, 16));
        in.getStyleClass().add("accent");
        in.setOnAction(e -> openDialog(null, true));
        Button out = new Button("Stok Çıkışı", Icons.of(MaterialDesignM.MINUS, 16));
        out.getStyleClass().add("button-outlined");
        out.setOnAction(e -> openDialog(table.getSelectionModel().getSelectedItem(), false));
        HBox toolbar = new HBox(12, search, onlyCritical, sp, out, in);
        toolbar.setAlignment(Pos.CENTER_LEFT);

        buildTable();
        VBox.setVgrow(table, Priority.ALWAYS);
        VBox card = new VBox(14, toolbar, table);
        card.getStyleClass().add("card");
        VBox.setVgrow(card, Priority.ALWAYS);

        root = new VBox(20, new HBox(16, cesit, toplam, kritik, tukenen), card);
    }

    private void buildTable() {
        SortedList<StokKalemi> sorted = new SortedList<>(filtered);
        sorted.comparatorProperty().bind(table.comparatorProperty());
        table.setItems(sorted);
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        table.getStyleClass().addAll("striped");
        table.setPlaceholder(new EmptyState(MaterialDesignP.PACKAGE_VARIANT, "Stok kaydı yok",
                "\"Stok Girişi\" ile eczanene ilaç ekleyebilirsin."));

        TableColumn<StokKalemi, String> ad = new TableColumn<>("İlaç");
        ad.setCellValueFactory(c -> new ReadOnlyObjectWrapper<>(c.getValue().ilacAdi()));
        ad.setPrefWidth(300);
        ad.setComparator(String.CASE_INSENSITIVE_ORDER);

        TableColumn<StokKalemi, StokKalemi> miktar = new TableColumn<>("Miktar");
        miktar.setCellValueFactory(c -> new ReadOnlyObjectWrapper<>(c.getValue()));
        miktar.setComparator((a, b) -> Integer.compare(a.miktar(), b.miktar()));
        miktar.setPrefWidth(110);
        miktar.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(StokKalemi k, boolean empty) {
                super.updateItem(k, empty);
                setGraphic(empty || k == null ? null : Badges.stok(k));
            }
        });

        TableColumn<StokKalemi, String> durum = new TableColumn<>("Durum");
        durum.setCellValueFactory(c -> new ReadOnlyObjectWrapper<>(
                c.getValue().tukendi() ? "Tükendi" : c.getValue().kritik() ? "Kritik" : "Yeterli"));
        durum.setPrefWidth(100);

        TableColumn<StokKalemi, StokKalemi> min = new TableColumn<>("Kritik seviye");
        min.setCellValueFactory(c -> new ReadOnlyObjectWrapper<>(c.getValue()));
        min.setComparator((a, b) -> Integer.compare(a.minMiktar(), b.minMiktar()));
        min.setPrefWidth(150);
        min.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(StokKalemi k, boolean empty) {
                super.updateItem(k, empty);
                if (empty || k == null) {
                    setGraphic(null);
                    return;
                }
                Label v = new Label(String.valueOf(k.minMiktar()));
                Button edit = new Button(null, Icons.of(MaterialDesignP.PENCIL_OUTLINE, 14));
                edit.getStyleClass().addAll("button-icon", "flat", "small");
                edit.setTooltip(new Tooltip("Kritik seviyeyi değiştir"));
                edit.setOnAction(e -> editMin(k));
                HBox h = new HBox(6, v, edit);
                h.setAlignment(Pos.CENTER_LEFT);
                setGraphic(h);
            }
        });

        TableColumn<StokKalemi, String> guncel = new TableColumn<>("Son güncelleme");
        guncel.setCellValueFactory(c -> new ReadOnlyObjectWrapper<>(Fmt.relative(c.getValue().sonGuncelleme())));
        guncel.setPrefWidth(150);

        TableColumn<StokKalemi, StokKalemi> act = new TableColumn<>("");
        act.setCellValueFactory(c -> new ReadOnlyObjectWrapper<>(c.getValue()));
        act.setSortable(false);
        act.setPrefWidth(100);
        act.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(StokKalemi k, boolean empty) {
                super.updateItem(k, empty);
                if (empty || k == null) {
                    setGraphic(null);
                    return;
                }
                Button plus = new Button(null, Icons.of(MaterialDesignP.PLUS, 14));
                plus.getStyleClass().addAll("button-icon", "flat", "success");
                plus.setTooltip(new Tooltip("Giriş"));
                plus.setOnAction(e -> openDialog(k, true));
                Button minus = new Button(null, Icons.of(MaterialDesignM.MINUS, 14));
                minus.getStyleClass().addAll("button-icon", "flat", "danger");
                minus.setTooltip(new Tooltip("Çıkış"));
                minus.setOnAction(e -> openDialog(k, false));
                minus.setDisable(k.miktar() <= 0);
                setGraphic(new HBox(4, plus, minus));
            }
        });

        table.getColumns().add(ad);
        table.getColumns().add(miktar);
        table.getColumns().add(durum);
        table.getColumns().add(min);
        table.getColumns().add(guncel);
        table.getColumns().add(act);
        table.setRowFactory(tv -> {
            var row = new javafx.scene.control.TableRow<StokKalemi>();
            row.setOnMouseClicked(e -> {
                if (e.getClickCount() == 2 && !row.isEmpty()) openDialog(row.getItem(), true);
            });
            return row;
        });
    }

    private void applyFilter() {
        String q = search.getText() == null ? "" : search.getText().strip().toLowerCase(TR);
        boolean crit = onlyCritical.isSelected();
        filtered.setPredicate(k -> (!crit || k.kritik()) && (q.isEmpty() || k.ilacAdi().toLowerCase(TR).contains(q)));
    }

    @Override public String title() { return "Stok"; }

    @Override public String subtitle() { return "Eczanenin ilaç stokları ve kritik seviyeler. Hızlı giriş için satıra çift tıkla."; }

    @Override public Node view() { return root; }

    @Override
    public void refresh() {
        Fx.run(() -> ctx.services().stok().list(ctx.user()), (List<StokKalemi> list) -> {
            all.setAll(list);
            applyFilter();
            cesit.setValue(list.size());
            toplam.setValue(list.stream().mapToInt(StokKalemi::miktar).sum());
            kritik.setValue((int) list.stream().filter(k -> k.kritik() && !k.tukendi()).count());
            tukenen.setValue((int) list.stream().filter(StokKalemi::tukendi).count());
        });
    }

    private void editMin(StokKalemi k) {
        Spinner<Integer> s = new Spinner<>(0, StokService.MAX_MIKTAR, k.minMiktar());
        s.setEditable(true);
        Label info = Forms.hint("Miktar bu değere ya da altına düştüğünde ilaç \"kritik\" olarak işaretlenir.");
        Button save = new Button("Kaydet");
        save.getStyleClass().add("accent");
        save.setDefaultButton(true);
        Button cancel = new Button("Vazgeç");
        cancel.setCancelButton(true);
        Region sp = new Region();
        HBox.setHgrow(sp, Priority.ALWAYS);
        Label title = new Label(k.ilacAdi());
        title.getStyleClass().add("card-title");
        VBox box = new VBox(14, title, Forms.field("Kritik seviye", s, info), new HBox(10, sp, cancel, save));
        box.setPadding(new Insets(22));
        Stage st = ctx.shell().modal("Kritik seviye", box, 380, Region.USE_COMPUTED_SIZE);
        cancel.setOnAction(e -> st.close());
        save.setOnAction(e -> {
            s.increment(0);
            int v = s.getValue();
            Fx.run(() -> {
                ctx.services().stok().setMin(ctx.user(), k.ilacId(), v);
                return null;
            }, x -> {
                st.close();
                Toasts.success(k.ilacAdi() + " için kritik seviye " + v + " olarak ayarlandı.");
                refresh();
            }, save, null);
        });
        st.show();
    }

    /** Stok giriş/çıkış penceresi. */
    private void openDialog(StokKalemi preset, boolean giris) {
        ToggleGroup tg = new ToggleGroup();
        ToggleButton bIn = new ToggleButton("Giriş (+)", Icons.of(MaterialDesignP.PLUS, 14));
        ToggleButton bOut = new ToggleButton("Çıkış (−)", Icons.of(MaterialDesignM.MINUS, 14));
        bIn.getStyleClass().add("left-pill");
        bOut.getStyleClass().add("right-pill");
        bIn.setToggleGroup(tg);
        bOut.setToggleGroup(tg);
        (giris ? bIn : bOut).setSelected(true);
        tg.selectedToggleProperty().addListener((o, old, sel) -> {
            if (sel == null && old != null) old.setSelected(true);
        });

        CustomTextField ilac = Forms.text("İlaç adı", MaterialDesignP.PILL);
        AutoComplete.attach(ilac, () -> all.stream().map(StokKalemi::ilacAdi).toList());
        if (preset != null) ilac.setText(preset.ilacAdi());
        Label mevcut = Forms.hint("");
        Runnable updateMevcut = () -> {
            String q = ilac.getText() == null ? "" : ilac.getText().strip();
            all.stream().filter(k -> k.ilacAdi().equalsIgnoreCase(q)).findFirst().ifPresentOrElse(
                    k -> mevcut.setText("Stokta şu an " + k.miktar() + " adet var."),
                    () -> mevcut.setText(q.isEmpty() ? "" : "Yeni ilaç — stoğa ilk kez eklenecek."));
        };
        ilac.textProperty().addListener((o, a, b) -> updateMevcut.run());
        updateMevcut.run();

        Spinner<Integer> miktar = new Spinner<>(1, StokService.MAX_MIKTAR, 10);
        miktar.setEditable(true);
        miktar.setMaxWidth(Double.MAX_VALUE);

        ComboBox<String> sebep = new ComboBox<>();
        sebep.setEditable(true);
        sebep.setMaxWidth(Double.MAX_VALUE);
        Runnable updateSebep = () -> {
            boolean in = tg.getSelectedToggle() == bIn;
            sebep.getItems().setAll(in ? StokService.GIRIS_SEBEPLERI : StokService.CIKIS_SEBEPLERI);
            sebep.getSelectionModel().selectFirst();
        };
        tg.selectedToggleProperty().addListener((o, a, b) -> updateSebep.run());
        updateSebep.run();

        Label title = new Label("Stok hareketi");
        title.getStyleClass().add("card-title");
        Button save = new Button("Kaydet", Icons.of(MaterialDesignC.CHECK, 16));
        save.getStyleClass().add("accent");
        save.setDefaultButton(true);
        Button cancel = new Button("Vazgeç");
        cancel.setCancelButton(true);
        Region sp = new Region();
        HBox.setHgrow(sp, Priority.ALWAYS);

        VBox box = new VBox(16, title, new HBox(0, bIn, bOut), Forms.field("İlaç", ilac, mevcut),
                Forms.field("Miktar (kutu)", miktar), Forms.field("Sebep", sebep), new HBox(10, sp, cancel, save));
        box.setPadding(new Insets(22));
        Stage st = ctx.shell().modal("Stok hareketi", box, 420, Region.USE_COMPUTED_SIZE);
        cancel.setOnAction(e -> st.close());
        save.setOnAction(e -> {
            miktar.increment(0);
            int delta = (tg.getSelectedToggle() == bIn ? 1 : -1) * miktar.getValue();
            String ad = ilac.getText();
            String neden = sebep.getEditor().getText();
            Fx.run(() -> ctx.services().stok().adjust(ctx.user(), ad, delta, neden), yeni -> {
                st.close();
                Toasts.success(ad.strip() + ": " + (delta > 0 ? "+" : "") + delta + " → stokta " + yeni + " adet.");
                refresh();
            }, save, ex -> mevcut.setText(ex.getMessage()));
        });
        st.show();
        if (preset == null) ilac.requestFocus();
        else miktar.requestFocus();
    }

}
