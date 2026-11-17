package com.eczane.ui.views;

import atlantafx.base.controls.CustomTextField;
import com.eczane.model.Eczane;
import com.eczane.model.Ilac;
import com.eczane.service.ReceteService;
import com.eczane.service.ReceteService.KalemInput;
import com.eczane.ui.Dialogs;
import com.eczane.ui.Fx;
import com.eczane.ui.Icons;
import com.eczane.ui.Toasts;
import com.eczane.ui.components.AutoComplete;
import com.eczane.ui.components.EmptyState;
import com.eczane.ui.components.Forms;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Spinner;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextArea;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import org.kordamp.ikonli.materialdesign2.MaterialDesignA;
import org.kordamp.ikonli.materialdesign2.MaterialDesignC;
import org.kordamp.ikonli.materialdesign2.MaterialDesignD;
import org.kordamp.ikonli.materialdesign2.MaterialDesignP;

import java.util.ArrayList;
import java.util.List;

/** Doktorun reçete yazdığı form. */
final class YeniRecetePage implements Page {

    private static final List<String> KULLANIM = List.of(
            "Günde 1 kez 1 adet", "Günde 2 kez 1 adet", "Günde 3 kez 1 adet",
            "Sabah aç karnına 1 adet", "Akşam tok karnına 1 adet", "Gerektiğinde 1 adet",
            "Haftada 1 kez", "Günde 2 kez sürülecek");

    private final MainView.Context ctx;
    private final CustomTextField hastaTc = Forms.tc("Hastanın T.C. Kimlik No'su", MaterialDesignA.ACCOUNT_SEARCH_OUTLINE);
    private final Label hastaStatus = Forms.status();
    private final ComboBox<Eczane> eczane = new ComboBox<>();
    private final CustomTextField ilac = Forms.text("İlaç adı (ör. Parol 500 mg)", MaterialDesignP.PILL);
    private final Spinner<Integer> adet = new Spinner<>(1, ReceteService.MAX_ADET, 1);
    private final ComboBox<String> kullanim = new ComboBox<>(FXCollections.observableArrayList(KULLANIM));
    private final ObservableList<KalemInput> kalemler = FXCollections.observableArrayList();
    private final TableView<KalemInput> table = new TableView<>(kalemler);
    private final TextArea aciklama = new TextArea();
    private final Button submit = new Button("Reçeteyi Oluştur", Icons.of(MaterialDesignC.CHECK, 18));
    private final List<String> ilacAdlari = new ArrayList<>();
    private final ScrollPane root;
    private boolean hastaOk;

    YeniRecetePage(MainView.Context ctx) {
        this.ctx = ctx;

        // ---- hasta & eczane
        hastaTc.textProperty().addListener((o, a, v) -> lookupHasta(v));
        eczane.setMaxWidth(Double.MAX_VALUE);
        eczane.setPromptText("Eczane seç");
        VBox hastaField = Forms.field("Hasta", hastaTc, hastaStatus);
        VBox eczaneField = Forms.field("Gönderilecek eczane", eczane);
        HBox.setHgrow(hastaField, Priority.ALWAYS);
        HBox.setHgrow(eczaneField, Priority.ALWAYS);
        hastaField.setMaxWidth(Double.MAX_VALUE);
        eczaneField.setMaxWidth(Double.MAX_VALUE);
        HBox row1 = new HBox(16, hastaField, eczaneField);

        VBox step1 = section("1", "Hasta ve eczane", row1);

        // ---- ilaç ekleme
        AutoComplete.attach(ilac, () -> ilacAdlari);
        adet.setEditable(true);
        adet.setPrefWidth(100);
        kullanim.setEditable(true);
        kullanim.setPromptText("Kullanım talimatı");
        kullanim.setMaxWidth(Double.MAX_VALUE);
        Button add = new Button("Ekle", Icons.of(MaterialDesignP.PLUS, 16));
        add.getStyleClass().add("button-outlined");
        add.setOnAction(e -> addKalem());
        ilac.setOnAction(e -> addKalem());

        VBox ilacField = Forms.field("İlaç", ilac);
        VBox kullanimField = Forms.field("Kullanım", kullanim);
        HBox.setHgrow(ilacField, Priority.ALWAYS);
        HBox.setHgrow(kullanimField, Priority.ALWAYS);
        ilacField.setMaxWidth(Double.MAX_VALUE);
        kullanimField.setMaxWidth(Double.MAX_VALUE);
        VBox addBox = new VBox(add);
        addBox.setAlignment(Pos.BOTTOM_LEFT);
        HBox addRow = new HBox(12, ilacField, Forms.field("Adet", adet), kullanimField, addBox);
        addRow.setAlignment(Pos.BOTTOM_LEFT);

        buildTable();
        VBox step2 = section("2", "İlaçlar", new VBox(14, addRow, table));

        // ---- açıklama
        aciklama.setPromptText("Hastaya ya da eczacıya not (isteğe bağlı). Ör: Kontrol 1 hafta sonra.");
        aciklama.setPrefRowCount(3);
        aciklama.setWrapText(true);
        aciklama.setTextFormatter(new javafx.scene.control.TextFormatter<String>(
                ch -> ch.getControlNewText().length() <= 500 ? ch : null));
        VBox step3 = section("3", "Not", aciklama);

        // ---- alt çubuk
        Button clear = new Button("Formu temizle", Icons.of(MaterialDesignD.DELETE_SWEEP_OUTLINE, 16));
        clear.getStyleClass().add("flat");
        clear.setOnAction(e -> {
            if (kalemler.isEmpty() || Dialogs.confirm("Formu temizle", null, "Girilen tüm bilgiler silinsin mi?", "Temizle")) reset();
        });
        submit.getStyleClass().addAll("accent", "large");
        submit.setOnAction(e -> submit());
        Region sp = new Region();
        HBox.setHgrow(sp, Priority.ALWAYS);
        HBox footer = new HBox(12, clear, sp, submit);
        footer.setAlignment(Pos.CENTER_RIGHT);

        VBox form = new VBox(20, step1, step2, step3, footer);
        form.setMaxWidth(980);
        root = new ScrollPane(form);
        root.setFitToWidth(true);
        root.getStyleClass().add("page-scroll");
    }

    private static VBox section(String no, String title, Node content) {
        Label n = new Label(no);
        n.getStyleClass().addAll("badge", "badge-accent");
        Label t = new Label(title);
        t.getStyleClass().add("card-title");
        HBox head = new HBox(10, n, t);
        head.setAlignment(Pos.CENTER_LEFT);
        VBox card = new VBox(16, head, content);
        card.getStyleClass().add("card");
        return card;
    }

    private void buildTable() {
        table.setPlaceholder(new EmptyState(MaterialDesignP.PILL, "Henüz ilaç eklenmedi",
                "İlaç adını yazıp Enter'a bas ya da Ekle'ye tıkla."));
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        table.getStyleClass().addAll("bordered");
        table.setPrefHeight(230);

        TableColumn<KalemInput, Number> no = new TableColumn<>("#");
        no.setCellValueFactory(c -> new ReadOnlyObjectWrapper<>(kalemler.indexOf(c.getValue()) + 1));
        no.setPrefWidth(44);
        no.setMaxWidth(50);
        TableColumn<KalemInput, String> ad = new TableColumn<>("İlaç");
        ad.setCellValueFactory(c -> new ReadOnlyObjectWrapper<>(c.getValue().ilacAdi()));
        ad.setPrefWidth(280);
        TableColumn<KalemInput, Number> ad2 = new TableColumn<>("Adet");
        ad2.setCellValueFactory(c -> new ReadOnlyObjectWrapper<>(c.getValue().adet()));
        ad2.setPrefWidth(70);
        TableColumn<KalemInput, String> kul = new TableColumn<>("Kullanım");
        kul.setCellValueFactory(c -> new ReadOnlyObjectWrapper<>(c.getValue().kullanim() == null ? "—" : c.getValue().kullanim()));
        kul.setPrefWidth(280);
        TableColumn<KalemInput, KalemInput> del = new TableColumn<>("");
        del.setCellValueFactory(c -> new ReadOnlyObjectWrapper<>(c.getValue()));
        del.setPrefWidth(60);
        del.setMaxWidth(70);
        del.setSortable(false);
        del.setCellFactory(col -> new TableCell<>() {
            private final Button b = new Button(null, Icons.of(MaterialDesignD.DELETE_OUTLINE, 16));

            {
                b.getStyleClass().addAll("button-icon", "flat", "danger");
                b.setTooltip(new Tooltip("Kaldır"));
                b.setOnAction(e -> kalemler.remove(getItem()));
            }

            @Override
            protected void updateItem(KalemInput item, boolean empty) {
                super.updateItem(item, empty);
                setGraphic(empty ? null : b);
            }
        });
        table.getColumns().add(no);
        table.getColumns().add(ad);
        table.getColumns().add(ad2);
        table.getColumns().add(kul);
        table.getColumns().add(del);
        kalemler.addListener((javafx.collections.ListChangeListener<KalemInput>) c -> table.refresh());
    }

    @Override public String title() { return "Yeni Reçete"; }

    @Override public String subtitle() { return "Hastayı seç, ilaçları ekle ve reçeteyi eczaneye gönder"; }

    @Override public Node view() { return root; }

    @Override
    public void refresh() {
        Fx.run(() -> new Catalog(ctx.services().stok().eczaneler(), ctx.services().stok().ilaclar()), c -> {
            Eczane sel = eczane.getValue();
            eczane.getItems().setAll(c.eczaneler());
            if (sel != null) c.eczaneler().stream().filter(e -> e.id() == sel.id()).findFirst().ifPresent(eczane::setValue);
            if (eczane.getValue() == null && !c.eczaneler().isEmpty()) eczane.getSelectionModel().selectFirst();
            ilacAdlari.clear();
            c.ilaclar().forEach(i -> ilacAdlari.add(i.ad()));
        });
    }

    private record Catalog(List<Eczane> eczaneler, List<Ilac> ilaclar) {
    }

    private void lookupHasta(String tc) {
        hastaOk = false;
        if (tc.length() < 11) {
            Forms.setStatus(hastaStatus, null, true);
            return;
        }
        Fx.run(() -> ctx.services().recete().findHasta(ctx.user(), tc), hasta -> {
            if (!tc.equals(hastaTc.getText())) return; // bu arada değişti
            if (hasta.isPresent()) {
                hastaOk = true;
                Forms.setStatus(hastaStatus, "✓ " + hasta.get().fullName(), true);
            } else {
                Forms.setStatus(hastaStatus, "Bu T.C. ile kayıtlı hasta yok. Hastanın önce kayıt olması gerekir.", false);
            }
        });
    }

    private void addKalem() {
        String ad = ilac.getText() == null ? "" : ilac.getText().strip();
        if (ad.isEmpty()) {
            ilac.requestFocus();
            return;
        }
        if (kalemler.size() >= ReceteService.MAX_KALEM) {
            Toasts.error("Bir reçetede en fazla " + ReceteService.MAX_KALEM + " ilaç olabilir.");
            return;
        }
        adet.increment(0); // elle yazılan değeri işle
        String k = kullanim.getEditor().getText();
        kalemler.add(new KalemInput(ad, adet.getValue(), k == null || k.isBlank() ? null : k.strip()));
        ilac.clear();
        adet.getValueFactory().setValue(1);
        kullanim.getEditor().clear();
        kullanim.setValue(null);
        ilac.requestFocus();
    }

    private void submit() {
        if (!hastaOk) {
            Toasts.error("Önce kayıtlı bir hastanın T.C. Kimlik No'sunu gir.");
            hastaTc.requestFocus();
            return;
        }
        if (!ilac.getText().isBlank()) addKalem(); // yazılı ama eklenmemiş ilaç varsa ekle
        if (kalemler.isEmpty()) {
            Toasts.error("En az bir ilaç ekle.");
            ilac.requestFocus();
            return;
        }
        Eczane e = eczane.getValue();
        String tc = hastaTc.getText();
        List<KalemInput> items = List.copyOf(kalemler);
        String not = aciklama.getText();
        Fx.run(() -> ctx.services().recete().create(ctx.user(), tc, e == null ? null : e.id(), not, items), id -> {
            reset();
            refresh();
            Toasts.success("Reçete #" + id + " oluşturuldu ve eczaneye gönderildi" + (e == null ? "." : ": " + e.ad()));
            ctx.openRecete(id);
        }, submit, ex -> Dialogs.warn("Reçete oluşturulamadı", ex.getMessage()));
    }

    private void reset() {
        hastaTc.clear();
        ilac.clear();
        kalemler.clear();
        aciklama.clear();
        adet.getValueFactory().setValue(1);
        kullanim.setValue(null);
        kullanim.getEditor().clear();
    }

}
