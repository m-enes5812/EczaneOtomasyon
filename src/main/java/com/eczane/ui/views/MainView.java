package com.eczane.ui.views;

import com.eczane.model.Role;
import com.eczane.model.User;
import com.eczane.ui.AppShell;
import com.eczane.ui.Fx;
import com.eczane.ui.Icons;
import com.eczane.ui.Theme;
import com.eczane.ui.Toasts;
import com.eczane.ui.components.Avatar;
import com.eczane.ui.components.Badges;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.util.Duration;
import org.kordamp.ikonli.Ikon;
import org.kordamp.ikonli.javafx.FontIcon;
import org.kordamp.ikonli.materialdesign2.MaterialDesignA;
import org.kordamp.ikonli.materialdesign2.MaterialDesignF;
import org.kordamp.ikonli.materialdesign2.MaterialDesignH;
import org.kordamp.ikonli.materialdesign2.MaterialDesignL;
import org.kordamp.ikonli.materialdesign2.MaterialDesignM;
import org.kordamp.ikonli.materialdesign2.MaterialDesignP;
import org.kordamp.ikonli.materialdesign2.MaterialDesignR;
import org.kordamp.ikonli.materialdesign2.MaterialDesignV;
import org.kordamp.ikonli.materialdesign2.MaterialDesignW;

import java.util.LinkedHashMap;
import java.util.Map;

/** Giriş sonrası ana ekran: sol menü + üst çubuk + sayfa içeriği. Menü role göre değişir. */
public final class MainView extends BorderPane {

    enum Nav { GENEL, YENI_RECETE, RECETELER, STOK, HAREKETLER, HESAP }

    private final AppShell shell;
    private User user;
    private final ToggleGroup navGroup = new ToggleGroup();
    private final Map<Nav, ToggleButton> navButtons = new LinkedHashMap<>();
    private final Map<Nav, Page> pages = new LinkedHashMap<>();
    private final StackPane content = new StackPane();
    private final Label pageTitle = new Label();
    private final Label pageSubtitle = new Label();
    private final HBox pageActions = new HBox(8);
    private final Label unreadBadge = Badges.count(0);
    private final Timeline poll;
    private Page current;
    private int lastUnread = -1;

    public MainView(AppShell shell, User user) {
        this.shell = shell;
        this.user = user;
        getStyleClass().add("app-bg");

        Context ctx = new Context(this);
        pages.put(Nav.GENEL, new DashboardPage(ctx));
        if (user.is(Role.DOKTOR)) pages.put(Nav.YENI_RECETE, new YeniRecetePage(ctx));
        pages.put(Nav.RECETELER, new RecetelerPage(ctx));
        if (user.is(Role.ECZACI)) {
            pages.put(Nav.STOK, new StokPage(ctx));
            pages.put(Nav.HAREKETLER, new HareketPage(ctx));
        }
        pages.put(Nav.HESAP, new HesapPage(ctx));

        setLeft(sidebar());
        setCenter(center());

        navGroup.selectedToggleProperty().addListener((o, old, sel) -> {
            if (sel == null) {
                if (old != null) old.setSelected(true);
                return;
            }
            show((Nav) sel.getUserData());
        });
        navButtons.get(Nav.GENEL).setSelected(true);

        // Okunmamış mesajları düzenli kontrol et
        poll = new Timeline(new KeyFrame(Duration.seconds(10), e -> checkUnread()));
        poll.setCycleCount(Timeline.INDEFINITE);
        poll.play();
        checkUnread();
    }

    /** Sayfaların ortak erişim noktası. */
    static final class Context {
        private final MainView main;

        Context(MainView main) { this.main = main; }

        User user() { return main.user; }

        AppShell shell() { return main.shell; }

        com.eczane.service.Services services() { return main.shell.services(); }

        void go(Nav nav) { main.navigate(nav); }

        void openRecete(long id) { main.openRecete(id); }

        void userChanged(User u) {
            main.user = u;
            main.setLeft(main.sidebar());
            main.navButtons.get(Nav.HESAP).setSelected(true);
        }

        void unreadChanged() { main.checkUnread(); }
    }

    /** Belirli bir sayfaya geçer (klavye kısayolları ve testler için). */
    void navigate(Nav nav) {
        ToggleButton b = navButtons.get(nav);
        if (b != null) b.setSelected(true);
    }

    void openRecete(long id) {
        navigate(Nav.RECETELER);
        ((RecetelerPage) pages.get(Nav.RECETELER)).select(id);
    }

    public void dispose() {
        poll.stop();
        if (current != null) current.onHide();
    }

    // ---------------------------------------------------------------- düzen

    private VBox sidebar() {
        navButtons.clear();
        navGroup.getToggles().clear();

        StackPane mark = new StackPane(Icons.of(MaterialDesignP.PILL, 20));
        mark.getStyleClass().add("brand-mark");
        Label name = new Label("Eczane");
        name.getStyleClass().add("brand-name");
        Label sub = new Label("Otomasyon");
        sub.getStyleClass().add("user-role");
        HBox brand = new HBox(12, mark, new VBox(0, name, sub));
        brand.setAlignment(Pos.CENTER_LEFT);

        VBox nav = new VBox(4);
        Label menu = new Label("MENÜ");
        menu.getStyleClass().add("section-title");
        menu.setStyle("-fx-padding: 0 0 4 12;");
        nav.getChildren().add(menu);
        for (Nav n : pages.keySet()) {
            ToggleButton b = navButton(n);
            navButtons.put(n, b);
            if (n == Nav.RECETELER) {
                // okunmamış mesaj rozeti butonun sağ tarafında, tıklamayı engellemeden durur
                StackPane wrap = new StackPane(b, unreadBadge);
                unreadBadge.setMouseTransparent(true);
                StackPane.setAlignment(unreadBadge, Pos.CENTER_RIGHT);
                StackPane.setMargin(unreadBadge, new javafx.geometry.Insets(0, 10, 0, 0));
                nav.getChildren().add(wrap);
            } else {
                nav.getChildren().add(b);
            }
        }

        Region spacer = new Region();
        VBox.setVgrow(spacer, Priority.ALWAYS);

        Label uname = new Label(user.fullName());
        uname.getStyleClass().add("user-name");
        Label urole = new Label(user.role().label());
        urole.getStyleClass().add("user-role");
        VBox utext = new VBox(1, uname, urole);
        HBox.setHgrow(utext, Priority.ALWAYS);
        uname.setMaxWidth(130);
        Button logout = new Button(null, Icons.of(MaterialDesignL.LOGOUT, 18));
        logout.getStyleClass().addAll("button-icon", "flat");
        logout.setTooltip(new Tooltip("Çıkış yap"));
        logout.setOnAction(e -> shell.logout());
        HBox userBox = new HBox(10, new Avatar(user.initials(), user.tc(), 36), utext, logout);
        userBox.setAlignment(Pos.CENTER_LEFT);
        userBox.getStyleClass().add("user-box");

        VBox side = new VBox(24, brand, nav, spacer, userBox);
        side.getStyleClass().add("sidebar");
        if (current != null) {
            pages.entrySet().stream().filter(e -> e.getValue() == current).findFirst()
                    .ifPresent(e -> navButtons.get(e.getKey()).setSelected(true));
        }
        return side;
    }

    private ToggleButton navButton(Nav n) {
        String text;
        Ikon ikon;
        switch (n) {
            case GENEL -> { text = "Genel Bakış"; ikon = MaterialDesignV.VIEW_DASHBOARD_OUTLINE; }
            case YENI_RECETE -> { text = "Yeni Reçete"; ikon = MaterialDesignF.FILE_DOCUMENT_EDIT_OUTLINE; }
            case RECETELER -> {
                text = user.is(Role.ECZACI) ? "Gelen Reçeteler" : user.is(Role.DOKTOR) ? "Yazdığım Reçeteler" : "Reçetelerim";
                ikon = MaterialDesignF.FILE_DOCUMENT_MULTIPLE_OUTLINE;
            }
            case STOK -> { text = "Stok"; ikon = MaterialDesignP.PACKAGE_VARIANT_CLOSED; }
            case HAREKETLER -> { text = "Stok Hareketleri"; ikon = MaterialDesignH.HISTORY; }
            default -> { text = "Hesabım"; ikon = MaterialDesignA.ACCOUNT_COG_OUTLINE; }
        }
        ToggleButton b = new ToggleButton(text, Icons.of(ikon, 20));
        b.getStyleClass().add("nav-item");
        b.setUserData(n);
        b.setToggleGroup(navGroup);
        b.setMaxWidth(Double.MAX_VALUE);
        return b;
    }

    private BorderPane center() {
        pageTitle.getStyleClass().add("page-title");
        pageSubtitle.getStyleClass().add("page-subtitle");
        VBox titles = new VBox(2, pageTitle, pageSubtitle);
        HBox.setHgrow(titles, Priority.ALWAYS);

        FontIcon themeIcon = Icons.of(Theme.isDark() ? MaterialDesignW.WHITE_BALANCE_SUNNY : MaterialDesignW.WEATHER_NIGHT, 18);
        Button theme = new Button(null, themeIcon);
        theme.getStyleClass().addAll("button-icon", "flat");
        theme.setTooltip(new Tooltip("Açık / koyu tema"));
        theme.setOnAction(e -> Theme.toggle());
        Theme.darkProperty().addListener((o, a, dark) ->
                themeIcon.setIconCode(dark ? MaterialDesignW.WHITE_BALANCE_SUNNY : MaterialDesignW.WEATHER_NIGHT));

        Button refresh = new Button(null, Icons.of(MaterialDesignR.REFRESH, 18));
        refresh.getStyleClass().addAll("button-icon", "flat");
        refresh.setTooltip(new Tooltip("Yenile (F5)"));
        refresh.setOnAction(e -> {
            if (current != null) current.refresh();
            checkUnread();
        });

        pageActions.setAlignment(Pos.CENTER_RIGHT);
        HBox top = new HBox(12, titles, pageActions, refresh, theme);
        top.setAlignment(Pos.CENTER_LEFT);
        top.getStyleClass().add("topbar");

        content.getStyleClass().add("content");
        VBox.setVgrow(content, Priority.ALWAYS);

        sceneProperty().addListener((o, a, scene) -> {
            if (scene == null) return;
            scene.getAccelerators().put(new javafx.scene.input.KeyCodeCombination(javafx.scene.input.KeyCode.F5),
                    refresh::fire);
        });

        BorderPane bp = new BorderPane();
        bp.setTop(top);
        bp.setCenter(content);
        return bp;
    }

    private void show(Nav nav) {
        Page next = pages.get(nav);
        if (next == null || next == current) return;
        if (current != null) current.onHide();
        current = next;
        pageTitle.setText(next.title());
        pageSubtitle.setText(next.subtitle());
        Node actions = next.actions();
        pageActions.getChildren().setAll(actions == null ? new Node[0] : new Node[]{actions});
        content.getChildren().setAll(next.view());
        next.refresh();
    }

    private void checkUnread() {
        Fx.run(() -> shell.services().mesaj().unreadTotal(user), n -> {
            unreadBadge.setText(n > 99 ? "99+" : String.valueOf(n));
            unreadBadge.setVisible(n > 0);
            unreadBadge.setManaged(n > 0);
            if (lastUnread >= 0 && n > lastUnread) {
                Toasts.info("Reçetelerinde " + (n - lastUnread) + " yeni mesaj var.");
                if (current instanceof RecetelerPage || current instanceof DashboardPage) current.refresh();
            }
            lastUnread = n;
        });
    }
}
