/* 我的页面：连胜、徽章墙、总记录数（在线拉 /api/v1/profile/，离线用缓存）+ 设置区。 */
package com.moodtree.client.ui;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.moodtree.client.AppContext;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextField;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

public class MeView extends VBox implements Refreshable {

    private final AppContext app;
    private final VBox profileBox = new VBox(14);
    private final Label stateLabel = new Label();
    private boolean loaded;

    public MeView(AppContext app) {
        this.app = app;
        setSpacing(16);
        setPadding(new Insets(24));
        setStyle(Theme.page());

        Label title = new Label("我的");
        title.setStyle(Theme.h1());
        stateLabel.setStyle(Theme.soft());

        VBox body = new VBox(14, profileBox, buildSettings());
        ScrollPane scroll = new ScrollPane(body);
        scroll.setFitToWidth(true);
        scroll.setStyle("-fx-background: transparent; -fx-background-color: transparent;");
        VBox.setVgrow(scroll, Priority.ALWAYS);

        getChildren().addAll(title, stateLabel, scroll);
        refresh();
    }

    @Override
    public void refresh() {
        if (loaded) return;
        loaded = true;
        Bg.run(() -> {
                    if (app.api.ping()) {
                        JsonObject p = app.api.profile();
                        app.db.kvSet("profile_cache", p.toString());
                        return p;
                    }
                    String cached = app.db.kvGet("profile_cache");
                    return cached == null ? null : JsonParser.parseString(cached).getAsJsonObject();
                },
                this::render,
                err -> stateLabel.setText("加载失败：" + err.getMessage()));
    }

    private void render(JsonObject p) {
        profileBox.getChildren().clear();
        if (p == null) {
            stateLabel.setText("离线且暂无缓存数据，联网后这里会显示你的连胜和徽章");
            return;
        }
        stateLabel.setText("");

        // ---- 连胜卡 ----
        int streak = p.has("streak") ? p.get("streak").getAsInt() : 0;
        Label fire = new Label("🔥");
        fire.setStyle("-fx-font-size: 40px;");
        Label days = new Label(streak + " 天");
        days.setStyle("-fx-font-size: 32px; -fx-font-weight: bold; -fx-text-fill: " + Theme.INK + ";");
        Label cap = new Label("连续记录");
        cap.setStyle(Theme.soft());
        HBox streakCard = new HBox(16, fire, new VBox(2, days, cap));
        streakCard.setAlignment(Pos.CENTER_LEFT);
        streakCard.setPadding(new Insets(20));
        streakCard.setStyle(Theme.card());

        // ---- 统计 ----
        int total = p.has("total_entries") ? p.get("total_entries").getAsInt() : 0;
        String joined = p.has("date_joined") && !p.get("date_joined").isJsonNull()
                ? p.get("date_joined").getAsString().substring(0, 10) : "";
        Label stat = new Label("共记录 " + total + " 条心情" + (joined.isEmpty() ? "" : " · " + joined + " 加入"));
        stat.setStyle(Theme.soft());

        // ---- 徽章墙 ----
        Label badgeTitle = new Label("徽章墙");
        badgeTitle.setStyle(Theme.h2());
        FlowPane badges = new FlowPane(10, 10);
        if (p.has("badges")) {
            for (JsonElement el : p.getAsJsonArray("badges")) {
                JsonObject b = el.getAsJsonObject();
                Label emoji = new Label(b.get("emoji").getAsString());
                emoji.setStyle("-fx-font-size: 28px;");
                Label name = new Label(b.get("name").getAsString());
                name.setStyle("-fx-font-size: 13px; -fx-text-fill: " + Theme.INK + ";");
                Label need = new Label("连续 " + b.get("days").getAsInt() + " 天");
                need.setStyle("-fx-font-size: 11px; -fx-text-fill: " + Theme.INK_SOFT + ";");
                VBox badge = new VBox(4, emoji, name, need);
                badge.setAlignment(Pos.CENTER);
                badge.setPadding(new Insets(14, 20, 14, 20));
                badge.setStyle(Theme.card());
                badges.getChildren().add(badge);
            }
        }
        if (badges.getChildren().isEmpty()) {
            Label none = new Label("还没有徽章，从连续记录 3 天开始收集吧");
            none.setStyle(Theme.soft());
            badges.getChildren().add(none);
        }

        profileBox.getChildren().addAll(streakCard, stat, badgeTitle, badges);
    }

    /** 设置区：服务器地址 + 刷新推荐目录 */
    private VBox buildSettings() {
        Label h = new Label("设置");
        h.setStyle(Theme.h2());

        Label serverLabel = new Label("服务器地址");
        serverLabel.setStyle(Theme.soft());
        TextField server = new TextField(app.config.serverBase());
        server.setStyle(Theme.input());
        server.setMaxWidth(360);
        Button saveServer = new Button("保存");
        saveServer.setStyle(Theme.primaryBtn());
        Label saveState = new Label();
        saveState.setStyle(Theme.soft());
        saveServer.setOnAction(e -> {
            app.config.setServerBase(server.getText());
            app.config.save();
            saveState.setText("已保存，重启后完全生效");
        });
        HBox serverRow = new HBox(10, server, saveServer, saveState);
        serverRow.setAlignment(Pos.CENTER_LEFT);

        Button refreshCatalog = new Button("刷新推荐内容缓存");
        refreshCatalog.setStyle(Theme.ghostBtn() + "-fx-border-color: " + Theme.ACCENT
                + "; -fx-border-radius: 8; -fx-text-fill: " + Theme.ACCENT_D + ";");
        Label catState = new Label();
        catState.setStyle(Theme.soft());
        refreshCatalog.setOnAction(e -> {
            refreshCatalog.setDisable(true);
            catState.setText("刷新中…");
            Bg.run(() -> app.sync.refreshCatalog(),
                    ok -> {
                        refreshCatalog.setDisable(false);
                        catState.setText(ok ? "已更新（离线也能给推荐）" : "刷新失败，检查网络");
                    },
                    err -> {
                        refreshCatalog.setDisable(false);
                        catState.setText("刷新失败：" + err.getMessage());
                    });
        });
        HBox catRow = new HBox(10, refreshCatalog, catState);
        catRow.setAlignment(Pos.CENTER_LEFT);

        VBox box = new VBox(10, h, serverLabel, serverRow, catRow);
        box.setPadding(new Insets(16));
        box.setStyle(Theme.card());
        VBox.setMargin(box, new Insets(10, 0, 0, 0));
        return box;
    }
}
