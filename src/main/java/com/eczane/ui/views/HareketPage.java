package com.eczane.ui.views;

import atlantafx.base.controls.CustomTextField;
import com.eczane.model.StokHareketi;
import com.eczane.ui.Fmt;
import com.eczane.ui.Fx;
import com.eczane.ui.components.EmptyState;
import com.eczane.ui.components.Forms;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.scene.Node;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import org.kordamp.ikonli.materialdesign2.MaterialDesignH;
import org.kordamp.ikonli.materialdesign2.MaterialDesignM;

import java.util.List;
import java.util.Locale;

/** Stok değişikliklerinin geçmişi (kim, ne zaman, neden). */
final class HareketPage implements Page {

    private static final Locale TR = Locale.forLanguageTag("tr");
    private static final int LIMIT = 1000;

    private final MainView.Context ctx;
    private final ObservableList<StokHareketi> all = FXCollections.observableArrayList();
    private final FilteredList<StokHareketi> filtered = new FilteredList<>(all);
    private final TableView<StokHareketi> table = new TableView<>(filtered);
    private final CustomTextField search = Forms.text("İlaç, sebep ya da kullanıcı ara…", MaterialDesignM.MAGNIFY);
    private final VBox root;

    HareketPage(MainView.Context ctx) {
        this.ctx = ctx;
        search.setMaxWidth(360);
        search.textProperty().addListener((o, a, q) -> {
            String s = q == null ? "" : q.strip().toLowerCase(TR);
            filtered.setPredicate(h -> s.isEmpty() || h.ilacAdi().toLowerCase(TR).contains(s)
                    || h.sebep().toLowerCase(TR).contains(s)
                    || (h.kullaniciAdi() != null && h.kullaniciAdi().toLowerCase(TR).contains(s)));
        });

        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        table.getStyleClass().add("striped");
        table.setPlaceholder(new EmptyState(MaterialDesignH.HISTORY, "Henüz stok hareketi yok",
                "Stok girişleri, çıkışları ve teslim edilen reçeteler burada listelenir."));

        TableColumn<StokHareketi, String> tarih = new TableColumn<>("Tarih");
        tarih.setCellValueFactory(c -> new ReadOnlyObjectWrapper<>(Fmt.full(c.getValue().tarih())));
        tarih.setPrefWidth(190);
        TableColumn<StokHareketi, String> ilac = new TableColumn<>("İlaç");
        ilac.setCellValueFactory(c -> new ReadOnlyObjectWrapper<>(c.getValue().ilacAdi()));
        ilac.setPrefWidth(240);
        TableColumn<StokHareketi, Number> degisim = new TableColumn<>("Değişim");
        degisim.setCellValueFactory(c -> new ReadOnlyObjectWrapper<>(c.getValue().degisim()));
        degisim.setPrefWidth(100);
        degisim.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(Number n, boolean empty) {
                super.updateItem(n, empty);
                getStyleClass().removeAll("delta-plus", "delta-minus");
                if (empty || n == null) {
                    setText(null);
                    return;
                }
                setText((n.intValue() > 0 ? "+" : "") + n.intValue());
                getStyleClass().add(n.intValue() > 0 ? "delta-plus" : "delta-minus");
            }
        });
        TableColumn<StokHareketi, Number> yeni = new TableColumn<>("Sonraki stok");
        yeni.setCellValueFactory(c -> new ReadOnlyObjectWrapper<>(c.getValue().yeniMiktar()));
        yeni.setPrefWidth(120);
        TableColumn<StokHareketi, String> sebep = new TableColumn<>("Sebep");
        sebep.setCellValueFactory(c -> new ReadOnlyObjectWrapper<>(c.getValue().sebep()));
        sebep.setPrefWidth(220);
        TableColumn<StokHareketi, String> kim = new TableColumn<>("İşlemi yapan");
        kim.setCellValueFactory(c -> new ReadOnlyObjectWrapper<>(
                c.getValue().kullaniciAdi() == null ? "—" : c.getValue().kullaniciAdi()));
        kim.setPrefWidth(180);
        table.getColumns().add(tarih);
        table.getColumns().add(ilac);
        table.getColumns().add(degisim);
        table.getColumns().add(yeni);
        table.getColumns().add(sebep);
        table.getColumns().add(kim);
        table.setRowFactory(tv -> {
            var row = new javafx.scene.control.TableRow<StokHareketi>();
            row.setOnMouseClicked(e -> {
                if (e.getClickCount() == 2 && !row.isEmpty() && row.getItem().receteId() != null) {
                    ctx.openRecete(row.getItem().receteId());
                }
            });
            return row;
        });

        VBox.setVgrow(table, Priority.ALWAYS);
        VBox card = new VBox(14, search, table);
        card.getStyleClass().add("card");
        VBox.setVgrow(card, Priority.ALWAYS);
        root = new VBox(card);
    }

    @Override public String title() { return "Stok Hareketleri"; }

    @Override public String subtitle() { return "Son " + LIMIT + " stok değişikliği. Reçeteye gitmek için satıra çift tıkla."; }

    @Override public Node view() { return root; }

    @Override
    public void refresh() {
        Fx.run(() -> ctx.services().stok().hareketler(ctx.user(), LIMIT), (List<StokHareketi> l) -> all.setAll(l));
    }
}
