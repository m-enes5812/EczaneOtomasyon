package com.eczane.ui.views;

import com.eczane.model.Mesaj;
import com.eczane.model.Role;
import com.eczane.service.MesajService;
import com.eczane.ui.Fmt;
import com.eczane.ui.Fx;
import com.eczane.ui.Icons;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextField;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.util.Duration;
import org.kordamp.ikonli.materialdesign2.MaterialDesignI;
import org.kordamp.ikonli.materialdesign2.MaterialDesignM;
import org.kordamp.ikonli.materialdesign2.MaterialDesignS;

import java.util.List;

/** Reçeteye bağlı mesajlaşma. Açıkken 5 saniyede bir yeni mesajları getirir. */
final class ChatPane extends VBox {

    private final MainView.Context ctx;
    private final long receteId;
    private final Runnable onRead;
    private final VBox messages = new VBox(10);
    private final ScrollPane scroll = new ScrollPane(messages);
    private final TextField input = new TextField();
    private final Button send = new Button(null, Icons.of(MaterialDesignS.SEND, 18));
    private final Timeline poll;
    private long lastId = -1;
    private int lastCount = -1;

    ChatPane(MainView.Context ctx, long receteId, Runnable onRead) {
        this.ctx = ctx;
        this.receteId = receteId;
        this.onRead = onRead;
        getStyleClass().add("chat");
        setMinHeight(200);

        messages.setPadding(new Insets(14));
        messages.setFillWidth(true);
        scroll.setFitToWidth(true);
        scroll.getStyleClass().add("chat-scroll");
        scroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        VBox.setVgrow(scroll, Priority.ALWAYS);
        // Yeni mesajlar çizildiğinde içerik uzar; her seferinde en alta kaydır.
        messages.heightProperty().addListener((o, a, b) -> scroll.setVvalue(1.0));

        input.setPromptText("Mesaj yaz… (Enter ile gönder)");
        input.setTextFormatter(new javafx.scene.control.TextFormatter<String>(
                ch -> ch.getControlNewText().length() <= MesajService.MAX_LENGTH ? ch : null));
        input.setOnAction(e -> sendMessage());
        HBox.setHgrow(input, Priority.ALWAYS);
        send.getStyleClass().addAll("accent", "button-icon");
        send.setTooltip(new Tooltip("Gönder"));
        send.setOnAction(e -> sendMessage());
        send.disableProperty().bind(input.textProperty().isEmpty());
        HBox bar = new HBox(8, input, send);
        bar.setAlignment(Pos.CENTER);
        bar.getStyleClass().add("chat-input-bar");

        getChildren().addAll(scroll, bar);

        poll = new Timeline(new KeyFrame(Duration.seconds(5), e -> load(false)));
        poll.setCycleCount(Timeline.INDEFINITE);
        load(true);
        poll.play();
    }

    void stop() {
        poll.stop();
    }

    private void load(boolean first) {
        Fx.run(() -> ctx.services().mesaj().list(ctx.user(), receteId), (List<Mesaj> list) -> {
            long newest = list.isEmpty() ? 0 : list.getLast().id();
            if (!first && newest == lastId && list.size() == lastCount) return;
            boolean hadNew = lastId >= 0 && newest != lastId;
            lastId = newest;
            lastCount = list.size();
            render(list);
            if (first || hadNew) {
                onRead.run();
                ctx.unreadChanged();
            }
        }, null, ex -> poll.stop());
    }

    private void render(List<Mesaj> list) {
        messages.getChildren().clear();
        if (list.isEmpty()) {
            Label empty = new Label("Henüz mesaj yok. İlk mesajı sen gönder.");
            empty.getStyleClass().add("muted");
            HBox h = new HBox(empty);
            h.setAlignment(Pos.CENTER);
            messages.getChildren().add(h);
        }
        for (Mesaj m : list) messages.getChildren().add(m.sistem() ? systemRow(m) : bubbleRow(m));
        Platform.runLater(() -> scroll.setVvalue(1.0));
    }

    private HBox systemRow(Mesaj m) {
        Label l = new Label(m.metin() + "  ·  " + Fmt.relative(m.tarih()), Icons.of(MaterialDesignI.INFORMATION_OUTLINE, 14));
        l.getStyleClass().add("system-msg");
        l.setWrapText(true);
        HBox h = new HBox(l);
        h.setAlignment(Pos.CENTER);
        return h;
    }

    private HBox bubbleRow(Mesaj m) {
        boolean mine = m.gonderenId() == ctx.user().id();
        Label text = new Label(m.metin());
        text.setWrapText(true);
        text.getStyleClass().add("bubble-text");
        VBox bubble = new VBox(text);
        bubble.getStyleClass().add("bubble");
        if (mine) bubble.getStyleClass().add("mine");
        bubble.setMaxWidth(420);

        String who = mine ? "Sen" : (m.gonderenRol() == Role.DOKTOR ? "Dr. " : "") + m.gonderenAdi()
                + (m.gonderenRol() != null && m.gonderenRol() != Role.DOKTOR ? " · " + m.gonderenRol().label() : "");
        Label author = new Label(who);
        author.getStyleClass().add("bubble-author");
        Label time = new Label(Fmt.relative(m.tarih()));
        time.getStyleClass().add("bubble-meta");
        HBox meta = new HBox(8, author, time);
        meta.setAlignment(mine ? Pos.CENTER_RIGHT : Pos.CENTER_LEFT);

        VBox col = new VBox(4, meta, bubble);
        col.setAlignment(mine ? Pos.CENTER_RIGHT : Pos.CENTER_LEFT);
        col.setFillWidth(false);
        Region sp = new Region();
        HBox.setHgrow(sp, Priority.ALWAYS);
        HBox row = mine ? new HBox(sp, col) : new HBox(col, sp);
        row.setAlignment(mine ? Pos.CENTER_RIGHT : Pos.CENTER_LEFT);
        return row;
    }

    private void sendMessage() {
        String text = input.getText();
        if (text == null || text.isBlank()) return;
        Fx.run(() -> {
            ctx.services().mesaj().send(ctx.user(), receteId, text);
            return null;
        }, v -> {
            input.clear();
            load(false);
            input.requestFocus();
        }, input, null);
    }
}
