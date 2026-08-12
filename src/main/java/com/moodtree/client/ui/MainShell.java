/* 主框架：左侧导航 + 右侧内容区 + 底部同步状态。
 * 定时（60秒）和数据变更后自动同步；断网静默，恢复后自动补传。
 * 情绪视觉影响：最新心情的叠色（径向渐变）+ 雨滴动画（难过/孤独/麻木）。 */
package com.moodtree.client.ui;

import com.moodtree.client.AppContext;
import com.moodtree.client.model.MoodEntry;
import com.moodtree.client.model.MoodMeta;
import com.moodtree.client.sync.SyncEngine;
import javafx.animation.FadeTransition;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.animation.TranslateTransition;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.shape.Rectangle;
import javafx.util.Duration;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Random;

public class MainShell extends BorderPane {

    private final AppContext app;
    private final StackPane content = new StackPane();
    private final Map<String, Button> navButtons = new LinkedHashMap<>();
    private final Map<String, javafx.scene.Node> views = new LinkedHashMap<>();
    private final Label syncStatus = new Label("尚未同步");
    private String currentKey;
    private boolean syncing;

    // ---- 情绪视觉影响 ----
    private final Pane moodOverlay = new Pane();
    private final Pane rainContainer = new Pane();
    private Timeline rainTimeline;
    private final Random random = new Random();

    public MainShell(AppContext app) {
        this.app = app;
        setStyle(Theme.page());

        // ---- 左侧导航 ----
        VBox sidebar = new VBox(6);
        sidebar.setPrefWidth(190);
        sidebar.setPadding(new Insets(20, 12, 20, 12));
        sidebar.setStyle("-fx-background-color: " + Theme.CARD + "; -fx-background-radius: 12;");

        Label logo = new Label("心履");
        logo.setStyle(Theme.h2());
        // 侧边栏顶部放彩色圆点树 logo（无底 PNG）
        try {
            javafx.scene.image.ImageView logoImg = new javafx.scene.image.ImageView(
                    new javafx.scene.image.Image(getClass().getResourceAsStream("/logo.png")));
            logoImg.setFitWidth(30);
            logoImg.setFitHeight(30);
            logoImg.setPreserveRatio(true);
            logo.setGraphic(logoImg);
        } catch (Exception ignored) { }
        Label user = new Label(app.loggedIn() ? app.config.username() : "游客 · 数据只在本机");
        user.setStyle(Theme.soft());
        VBox head = new VBox(2, logo, user);
        head.setPadding(new Insets(0, 0, 18, 8));

        addNav("calendar", "📅  心 情 日 历");
        addNav("recommend", "🎵  今 日 推 荐");
        addNav("chat", "💬  A I 树 洞");
        addNav("me", "👤  我 的");

        Region spacer = new Region();
        VBox.setVgrow(spacer, Priority.ALWAYS);

        syncStatus.setStyle("-fx-text-fill: " + Theme.INK_SOFT + "; -fx-font-size: 11px;");
        syncStatus.setWrapText(true);

        Button syncBtn = new Button("↻ 立即同步");
        syncBtn.setStyle(Theme.ghostBtn());
        syncBtn.setMaxWidth(Double.MAX_VALUE);
        syncBtn.setOnAction(e -> syncNow());
        syncBtn.setVisible(app.loggedIn());          // 游客没有云端可同步
        syncBtn.setManaged(app.loggedIn());

        // 游客显示登录入口；已登录显示退出
        Button authBtn = new Button(app.loggedIn() ? "退出登录" : "登录 / 注册");
        authBtn.setStyle(app.loggedIn() ? Theme.dangerBtn()
                : Theme.ghostBtn() + "-fx-text-fill: " + Theme.ACCENT_D + ";");
        authBtn.setMaxWidth(Double.MAX_VALUE);
        authBtn.setOnAction(e -> {
            if (app.loggedIn()) app.logout();
            else app.showLogin();
        });

        sidebar.getChildren().add(head);
        sidebar.getChildren().addAll(navButtons.values());
        sidebar.getChildren().addAll(spacer, syncStatus, syncBtn, authBtn);
        setLeft(sidebar);

        // ---- 内容区 + 情绪视觉叠层 ----
        content.setPadding(new Insets(0));
        Pane contentWrapper = new StackPane();
        contentWrapper.getChildren().add(content);

        // 情绪叠色（半透明径向渐变，鼠标穿透，不影响点击）
        moodOverlay.setPickOnBounds(false);
        moodOverlay.setMouseTransparent(true);
        moodOverlay.setVisible(false);
        contentWrapper.getChildren().add(moodOverlay);   // 叠在 content 之上

        // 雨滴容器（鼠标穿透）
        rainContainer.setPickOnBounds(false);
        rainContainer.setMouseTransparent(true);
        rainContainer.setVisible(false);
        contentWrapper.getChildren().add(rainContainer);  // 叠在最上层

        setCenter(contentWrapper);

        // 登录用户每分钟自动同步一次（失败静默）
        Timeline timer = new Timeline(new KeyFrame(Duration.seconds(60), e -> syncNow()));
        timer.setCycleCount(Timeline.INDEFINITE);
        timer.play();
    }

    private void addNav(String key, String text) {
        Button b = new Button(text);
        b.setMaxWidth(Double.MAX_VALUE);
        b.setAlignment(Pos.CENTER_LEFT);
        b.setPadding(new Insets(10, 14, 10, 14));
        b.setOnAction(e -> show(key));
        navButtons.put(key, b);
        styleNav(key, false);
    }

    private void styleNav(String key, boolean selected) {
        Button b = navButtons.get(key);
        b.setStyle(selected
                ? "-fx-background-color: " + Theme.CARD + "; -fx-background-radius: 10;"
                  + "-fx-font-size: 14px; -fx-text-fill: " + Theme.INK + "; -fx-font-weight: bold; -fx-cursor: hand;"
                : "-fx-background-color: transparent; -fx-font-size: 14px;"
                  + "-fx-text-fill: " + Theme.INK_SOFT + "; -fx-cursor: hand;");
    }

    public void show(String key) {
        if (key.equals(currentKey)) return;
        javafx.scene.Node view = views.computeIfAbsent(key, this::buildView);

        // 页面切换淡入动画（与手机端 fragment 过渡对齐）
        view.setOpacity(0);
        content.getChildren().setAll(view);
        FadeTransition fade = new FadeTransition(Duration.millis(200), view);
        fade.setFromValue(0);
        fade.setToValue(1);
        fade.play();

        navButtons.keySet().forEach(k -> styleNav(k, k.equals(key)));
        currentKey = key;
        app.setLastViewKey(key);
        if (view instanceof Refreshable r) r.refresh();
    }

    private javafx.scene.Node buildView(String key) {
        return switch (key) {
            case "calendar" -> new CalendarView(app);
            case "recommend" -> new RecommendView(app);
            case "chat" -> new ChatView(app);
            case "me" -> new MeView(app);
            default -> new PlaceholderView(key);
        };
    }

    /** 进入主界面后的首次同步 */
    public void onShown() {
        // 开发调试：-Dmoodtree.view=recommend 可启动后直接跳到指定页
        String jump = System.getProperty("moodtree.view");
        if (jump != null && navButtons.containsKey(jump)) {
            show(jump);
        } else {
            show(app.lastViewKey());
        }
        updateMoodVisual();
        syncNow();
    }

    public void syncNow() {
        if (syncing) return;
        if (!app.loggedIn()) {
            syncStatus.setText("游客模式：记录已保存在本机，登录后自动上云");
            return;
        }
        syncing = true;
        syncStatus.setText("同步中…");
        Bg.run(() -> app.sync.sync(),
                r -> {
                    syncing = false;
                    String time = LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm"));
                    if (r.error != null) {
                        syncStatus.setText((r.offline ? "离线，数据已存本地 " : r.error + " ") + time);
                    } else {
                        syncStatus.setText(r.summary() + "  " + time);
                    }
                    updateMoodVisual();
                    javafx.scene.Node v = views.get(currentKey);
                    if (v instanceof Refreshable rv) rv.refresh();
                },
                err -> {
                    syncing = false;
                    syncStatus.setText("同步失败：" + err.getMessage());
                });
    }

    // ============ 情绪视觉影响 ============

    /** 从数据库取最新一条心情，更新叠色和雨滴效果 */
    public void updateMoodVisual() {
        Bg.run(() -> {
                    try {
                        MoodEntry latest = app.db.getLatest();
                        if (latest == null) return null;
                        MoodMeta meta = MoodMeta.of(latest.mood);
                        return new Object[]{meta, latest.intensityLevel, latest.intensityPercent};
                    } catch (Exception e) {
                        return null;
                    }
                },
                data -> {
                    if (data == null) {
                        moodOverlay.setVisible(false);
                        rainContainer.setVisible(false);
                        stopRain();
                        return;
                    }
                    MoodMeta meta = (MoodMeta) data[0];
                    int intensityLevel = (int) data[1];
                    int intensityPercent = (int) data[2];

                    // 叠色：径向渐变，颜色来自心情色，强度缩放透明度
                    double opacity = 0.08;
                    if (intensityLevel >= 2) opacity = 0.12;
                    if (intensityLevel >= 3) opacity = 0.18;
                    if (intensityLevel >= 4) opacity = 0.25;
                    // 透明度再按百分位微调
                    opacity *= (0.8 + 0.4 * intensityPercent / 100.0);

                    String color = meta.color;
                    try {
                        Color c = Color.web(color);
                        int r = (int) (c.getRed() * 255);
                        int g = (int) (c.getGreen() * 255);
                        int b = (int) (c.getBlue() * 255);
                        String rgba = String.format("rgba(%d,%d,%d,%f)", r, g, b, opacity);
                        // 用 Region 实现径向渐变背景（CSS 方式，JavaFX 支持）
                        Region overlayRegion = new Region();
                        overlayRegion.setStyle(
                                "-fx-background-color: radial-gradient("
                                        + "focus-angle 0deg, focus-distance 0%, "
                                        + "center 50% 0%, radius 60%, "
                                        + rgba + " 0%, transparent 70%);");
                        overlayRegion.setMouseTransparent(true);
                        moodOverlay.getChildren().setAll(overlayRegion);
                    } catch (Exception e) {
                        moodOverlay.getChildren().clear();
                    }
                    moodOverlay.setVisible(true);

                    // 雨滴动画：难过/孤独/麻木 三种心情
                    String[] rainMoods = {"sad", "lonely", "numb"};
                    boolean shouldRain = false;
                    for (String rm : rainMoods) {
                        if (rm.equals(meta.key)) { shouldRain = true; break; }
                    }
                    if (shouldRain && intensityLevel >= 1) {
                        startRain();
                    } else {
                        stopRain();
                        rainContainer.setVisible(false);
                    }
                },
                err -> { /* 静默 */ });
    }

    private void startRain() {
        if (rainTimeline != null) return;
        rainContainer.setVisible(true);
        rainTimeline = new Timeline(new KeyFrame(Duration.millis(120), e -> spawnRainDrop()));
        rainTimeline.setCycleCount(Timeline.INDEFINITE);
        rainTimeline.play();
    }

    private void stopRain() {
        if (rainTimeline != null) {
            rainTimeline.stop();
            rainTimeline = null;
        }
        rainContainer.getChildren().clear();
    }

    private void spawnRainDrop() {
        if (!rainContainer.isVisible()) return;
        double w = rainContainer.getWidth();
        if (w <= 0) w = 800;
        Rectangle drop = new Rectangle(1.5, 14, Color.rgb(150, 160, 200, 0.2));
        drop.setX(random.nextDouble() * w);
        drop.setY(-20);
        rainContainer.getChildren().add(drop);

        double duration = 0.6 + random.nextDouble() * 0.4;
        TranslateTransition tt = new TranslateTransition(Duration.seconds(duration), drop);
        tt.setByY(rainContainer.getHeight() + 40);
        tt.setOnFinished(ev -> rainContainer.getChildren().remove(drop));
        tt.play();
    }
}
