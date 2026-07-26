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
        if (!app.loggedIn()) {
            renderGuest();
            return;
        }
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

    /** 游客模式：本地统计（连胜/总条数在本机算）+ 登录引导，徽章联网后才有 */
    private void renderGuest() {
        profileBox.getChildren().clear();
        stateLabel.setText("");

        int streak = 0, total = 0;
        try {
            total = app.db.countAlive();
            java.util.List<java.time.LocalDate> dates = app.db.listDistinctDates();
            // 从今天（或昨天）往前数连续有记录的天数
            java.time.LocalDate cursor = java.time.LocalDate.now();
            if (!dates.contains(cursor)) cursor = cursor.minusDays(1);
            while (dates.contains(cursor)) {
                streak++;
                cursor = cursor.minusDays(1);
            }
        } catch (Exception ignored) { }

        Label fire = new Label("🔥");
        fire.setStyle("-fx-font-size: 40px;");
        Label days = new Label(streak + " 天");
        days.setStyle("-fx-font-size: 32px; -fx-font-weight: bold; -fx-text-fill: " + Theme.INK + ";");
        Label cap = new Label("连续记录 · 共 " + total + " 条（本机）");
        cap.setStyle(Theme.soft());
        HBox streakCard = new HBox(16, fire, new VBox(2, days, cap));
        streakCard.setAlignment(Pos.CENTER_LEFT);
        streakCard.setPadding(new Insets(20));
        streakCard.setStyle(Theme.card());

        Label hint = new Label("登录后：记录自动同步到云端、解锁徽章墙、多设备互通，已有记录不会丢");
        hint.setStyle("-fx-font-size: 14px; -fx-text-fill: " + Theme.INK + ";");
        hint.setWrapText(true);
        Button loginBtn = new Button("登录 / 注册");
        loginBtn.setStyle(Theme.primaryBtn());
        loginBtn.setOnAction(e -> app.showLogin());
        VBox guestCard = new VBox(10, hint, loginBtn);
        guestCard.setPadding(new Insets(20));
        guestCard.setStyle(Theme.card());

        profileBox.getChildren().addAll(streakCard, guestCard);
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

    /** 设置区：主题（预设 + 自定义强调色）/ 服务器地址 / 刷新推荐目录 */
    private VBox buildSettings() {
        Label h = new Label("设置");
        h.setStyle(Theme.h2());

        // ---- 主题预设 ----
        Label themeLabel = new Label("主题");
        themeLabel.setStyle(Theme.soft());
        HBox presetRow = new HBox(10);
        presetRow.setAlignment(Pos.CENTER_LEFT);
        for (String[] preset : Theme.PRESETS) {
            String id = preset[0], name = preset[1], preview = preset[2];
            javafx.scene.shape.Circle dot = new javafx.scene.shape.Circle(9,
                    javafx.scene.paint.Color.web(preview));
            dot.setStroke(javafx.scene.paint.Color.web("#00000033"));
            Button b = new Button(name, dot);
            boolean active = id.equals(app.config.themeId());
            b.setStyle("-fx-background-color: " + (active ? Theme.CARD : "transparent") + ";"
                    + "-fx-border-color: " + (active ? Theme.ACCENT : "#00000022") + ";"
                    + "-fx-border-radius: 8; -fx-background-radius: 8;"
                    + "-fx-padding: 6 14; -fx-font-size: 13px; -fx-cursor: hand;"
                    + "-fx-text-fill: " + Theme.INK + ";");
            b.setOnAction(e -> app.applyTheme(id, app.config.accent()));
            presetRow.getChildren().add(b);
        }

        // ---- 自定义强调色 ----
        Label accentLabel = new Label("强调色");
        accentLabel.setStyle(Theme.soft());
        javafx.scene.control.ColorPicker picker = new javafx.scene.control.ColorPicker();
        try {
            picker.setValue(javafx.scene.paint.Color.web(
                    app.config.accent().isEmpty() ? Theme.ACCENT : app.config.accent()));
        } catch (Exception ignored) { }
        picker.setOnAction(e -> {
            javafx.scene.paint.Color c = picker.getValue();
            String hex = String.format("#%02x%02x%02x",
                    (int) (c.getRed() * 255), (int) (c.getGreen() * 255), (int) (c.getBlue() * 255));
            app.applyTheme(app.config.themeId(), hex);
        });
        Button resetAccent = new Button("恢复默认");
        resetAccent.setStyle(Theme.ghostBtn() + "-fx-font-size: 12px;");
        resetAccent.setOnAction(e -> app.applyTheme(app.config.themeId(), ""));
        HBox accentRow = new HBox(10, picker, resetAccent);
        accentRow.setAlignment(Pos.CENTER_LEFT);

        // ---- 服务器地址（技术细节藏在这里，登录页不显示）----
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

        VBox box = new VBox(10, h, themeLabel, presetRow, accentLabel, accentRow,
                serverLabel, serverRow, catRow);
        box.setPadding(new Insets(16));
        box.setStyle(Theme.card());
        VBox.setMargin(box, new Insets(10, 0, 0, 0));
        return box;
    }
}
