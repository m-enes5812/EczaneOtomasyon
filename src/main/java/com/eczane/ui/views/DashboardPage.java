package com.eczane.ui.views;

import com.eczane.model.DashboardStats;
import com.eczane.model.ReceteOzet;
import com.eczane.model.Role;
import com.eczane.model.StokKalemi;
import com.eczane.model.User;
import com.eczane.ui.Fmt;
import com.eczane.ui.Fx;
import com.eczane.ui.Icons;
import com.eczane.ui.components.Badges;
import com.eczane.ui.components.EmptyState;
import com.eczane.ui.components.StatCard;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import org.kordamp.ikonli.materialdesign2.MaterialDesignA;
import org.kordamp.ikonli.materialdesign2.MaterialDesignC;
import org.kordamp.ikonli.materialdesign2.MaterialDesignF;
import org.kordamp.ikonli.materialdesign2.MaterialDesignM;
import org.kordamp.ikonli.materialdesign2.MaterialDesignP;
import org.kordamp.ikonli.materialdesign2.MaterialDesignT;

import java.util.List;

/** Role göre sayaçlar, son reçeteler ve (eczacı için) kritik stok listesi. */
final class DashboardPage implements Page {

    private final MainView.Context ctx;
    private final StatCard c1, c2, c3, c4;
    private final VBox recentList = new VBox();
    private final VBox stockList = new VBox();
    private final ScrollPane root;

    DashboardPage(MainView.Context ctx) {
        this.ctx = ctx;
        Role role = ctx.user().role();
        switch (role) {
            case DOKTOR -> {
                c1 = new StatCard(MaterialDesignF.FILE_DOCUMENT_MULTIPLE_OUTLINE, "Toplam reçete", "accent");
                c2 = new StatCard(MaterialDesignC.CLOCK_OUTLINE, "Eczanede bekleyen", "warning");
                c3 = new StatCard(MaterialDesignC.CHECK_CIRCLE_OUTLINE, "Teslim edilen", "success");
                c4 = new StatCard(MaterialDesignM.MESSAGE_TEXT_OUTLINE, "Okunmamış mesaj", "danger");
            }
            case HASTA -> {
                c1 = new StatCard(MaterialDesignC.CLOCK_OUTLINE, "Teslim alınacak", "warning");
                c2 = new StatCard(MaterialDesignC.CHECK_CIRCLE_OUTLINE, "Teslim alınan", "success");
                c3 = new StatCard(MaterialDesignF.FILE_DOCUMENT_MULTIPLE_OUTLINE, "Toplam reçete", "accent");
                c4 = new StatCard(MaterialDesignM.MESSAGE_TEXT_OUTLINE, "Okunmamış mesaj", "danger");
            }
            default -> {
                c1 = new StatCard(MaterialDesignC.CLOCK_OUTLINE, "Bekleyen reçete", "warning");
                c2 = new StatCard(MaterialDesignC.CHECK_CIRCLE_OUTLINE, "Bugün verilen", "success");
                c3 = new StatCard(MaterialDesignA.ALERT_OUTLINE, "Kritik stok", "danger");
                c4 = new StatCard(MaterialDesignM.MESSAGE_TEXT_OUTLINE, "Okunmamış mesaj", "accent");
            }
        }

        Label hello = new Label();
        hello.getStyleClass().add("card-title");
        hello.setStyle("-fx-font-size: 18px;");
        hello.setText(Fmt.greeting() + ", " + Fmt.firstName(ctx.user().fullName()));
        Label today = new Label(Fmt.today());
        today.getStyleClass().add("muted");
        Region sp = new Region();
        HBox.setHgrow(sp, Priority.ALWAYS);
        HBox welcome = new HBox(12, new VBox(2, hello, today), sp);
        welcome.setAlignment(Pos.CENTER_LEFT);
        if (role == Role.DOKTOR) {
            Button write = new Button("Yeni reçete yaz", Icons.of(MaterialDesignP.PLUS, 16));
            write.getStyleClass().add("accent");
            write.setOnAction(e -> ctx.go(MainView.Nav.YENI_RECETE));
            welcome.getChildren().add(write);
        } else if (role == Role.ECZACI) {
            Button stok = new Button("Stok girişi", Icons.of(MaterialDesignP.PLUS, 16));
            stok.getStyleClass().add("accent");
            stok.setOnAction(e -> ctx.go(MainView.Nav.STOK));
            welcome.getChildren().add(stok);
        }

        HBox stats = new HBox(16, c1, c2, c3, c4);

        VBox recentCard = card("Son reçeteler", recentList, "Tümünü gör", () -> ctx.go(MainView.Nav.RECETELER));
        HBox.setHgrow(recentCard, Priority.ALWAYS);
        HBox lower = new HBox(16, recentCard);
        if (role == Role.ECZACI) {
            VBox stockCard = card("Kritik seviyedeki ilaçlar", stockList, "Stoğa git", () -> ctx.go(MainView.Nav.STOK));
            stockCard.setPrefWidth(380);
            stockCard.setMinWidth(320);
            lower.getChildren().add(stockCard);
        }

        VBox body = new VBox(20, welcome, stats, lower);
        root = new ScrollPane(body);
        root.setFitToWidth(true);
        root.getStyleClass().add("page-scroll");
    }

    private static VBox card(String title, VBox list, String linkText, Runnable onLink) {
        Label t = new Label(title);
        t.getStyleClass().add("card-title");
        Region sp = new Region();
        HBox.setHgrow(sp, Priority.ALWAYS);
        javafx.scene.control.Hyperlink link = new javafx.scene.control.Hyperlink(linkText);
        link.setOnAction(e -> onLink.run());
        HBox head = new HBox(t, sp, link);
        head.setAlignment(Pos.CENTER_LEFT);
        VBox card = new VBox(12, head, list);
        card.getStyleClass().add("card");
        return card;
    }

    @Override public String title() { return "Genel Bakış"; }

    @Override public String subtitle() {
        User u = ctx.user();
        return switch (u.role()) {
            case DOKTOR -> "Yazdığın reçetelerin durumu";
            case HASTA -> "Reçetelerin ve eczane mesajların";
            case ECZACI -> "Eczanenin bugünkü durumu";
        };
    }

    @Override public Node view() { return root; }

    @Override
    public void refresh() {
        User u = ctx.user();
        Fx.run(() -> new Data(ctx.services().recete().stats(u), ctx.services().recete().list(u),
                u.is(Role.ECZACI) ? ctx.services().stok().list(u) : List.of()), this::render);
    }

    private record Data(DashboardStats stats, List<ReceteOzet> receteler, List<StokKalemi> stok) {
    }

    private void render(Data d) {
        DashboardStats s = d.stats();
        switch (ctx.user().role()) {
            case DOKTOR -> {
                c1.setValue(s.toplam());
                c1.setCaption("Bugün " + s.bugun() + " yeni");
                c2.setValue(s.bekleyen());
                c3.setValue(s.verilen());
                c4.setValue(s.okunmamis());
            }
            case HASTA -> {
                c1.setValue(s.bekleyen());
                c2.setValue(s.verilen());
                c3.setValue(s.toplam());
                c4.setValue(s.okunmamis());
            }
            case ECZACI -> {
                c1.setValue(s.bekleyen());
                c2.setValue(s.bugun());
                c3.setValue(s.kritikStok());
                c3.setCaption(s.kritikStok() == 0 ? "Her şey yolunda" : "Sipariş vermeyi unutma");
                c4.setValue(s.okunmamis());
            }
        }

        recentList.getChildren().clear();
        List<ReceteOzet> recent = d.receteler().stream().limit(6).toList();
        if (recent.isEmpty()) {
            recentList.getChildren().add(new EmptyState(MaterialDesignF.FILE_DOCUMENT_OUTLINE, "Henüz reçete yok",
                    ctx.user().is(Role.DOKTOR) ? "İlk reçeteni \"Yeni Reçete\" sayfasından yazabilirsin." : null));
        }
        for (ReceteOzet r : recent) recentList.getChildren().add(recentRow(r));

        stockList.getChildren().clear();
        List<StokKalemi> kritik = d.stok().stream().filter(StokKalemi::kritik)
                .sorted((a, b) -> Integer.compare(a.miktar(), b.miktar())).limit(8).toList();
        if (kritik.isEmpty() && ctx.user().is(Role.ECZACI)) {
            stockList.getChildren().add(new EmptyState(MaterialDesignT.THUMB_UP_OUTLINE, "Kritik ilaç yok",
                    "Tüm ilaçlar belirlenen seviyenin üzerinde."));
        }
        for (StokKalemi k : kritik) {
            Label name = new Label(k.ilacAdi());
            name.getStyleClass().add("info-value");
            Label min = new Label("Kritik seviye: " + k.minMiktar());
            min.getStyleClass().add("row-meta");
            min.getStyleClass().add("muted");
            Region sp = new Region();
            HBox.setHgrow(sp, Priority.ALWAYS);
            HBox row = new HBox(10, new VBox(2, name, min), sp, Badges.stok(k));
            row.setAlignment(Pos.CENTER_LEFT);
            row.getStyleClass().add("list-row");
            stockList.getChildren().add(row);
        }
    }

    private HBox recentRow(ReceteOzet r) {
        String who = switch (ctx.user().role()) {
            case DOKTOR, ECZACI -> r.hastaAdi();
            case HASTA -> "Dr. " + r.doktorAdi();
        };
        Label title = new Label("#" + r.id() + "  " + who);
        title.getStyleClass().add("info-value");
        String meta = switch (ctx.user().role()) {
            case DOKTOR -> r.eczaneAdi();
            case HASTA -> r.eczaneAdi();
            case ECZACI -> "Dr. " + r.doktorAdi();
        } + " · " + r.kalemSayisi() + " ilaç · " + Fmt.relative(r.tarih());
        Label m = new Label(meta);
        m.getStyleClass().add("muted");
        m.setStyle("-fx-font-size: 12px;");
        Region sp = new Region();
        HBox.setHgrow(sp, Priority.ALWAYS);
        HBox row = new HBox(10, new VBox(2, title, m), sp);
        if (r.okunmamis() > 0) {
            Label c = Badges.count(r.okunmamis());
            row.getChildren().add(c);
        }
        row.getChildren().add(Badges.durum(r.durum()));
        row.setAlignment(Pos.CENTER_LEFT);
        row.getStyleClass().add("list-row");
        row.setCursor(javafx.scene.Cursor.HAND);
        row.setOnMouseClicked(e -> ctx.openRecete(r.id()));
        return row;
    }
}
