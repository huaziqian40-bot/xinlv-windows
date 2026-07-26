/* 全局应用上下文：配置/API/数据库/同步引擎的持有者，负责登录页与主界面切换。 */
package com.moodtree.client;

import com.moodtree.client.api.ApiClient;
import com.moodtree.client.db.LocalDb;
import com.moodtree.client.model.MoodMeta;
import com.moodtree.client.sync.SyncEngine;
import com.moodtree.client.ui.LoginView;
import com.moodtree.client.ui.MainShell;
import javafx.scene.Scene;
import javafx.stage.Stage;

public class AppContext {
    public final Config config = new Config();
    public final ApiClient api = new ApiClient(config);
    public final LocalDb db;
    public final SyncEngine sync;

    private final Stage stage;

    public AppContext(Stage stage) throws Exception {
        this.stage = stage;
        this.db = new LocalDb(config.dbPath());
        this.sync = new SyncEngine(config, api, db);
        // 上次缓存的心情定义（离线兜底是代码里的默认值）
        String cached = db.kvGet("moods_cache");
        if (cached != null) MoodMeta.overrideFromCatalogJson(cached);
    }

    public boolean loggedIn() { return !config.token().isEmpty(); }

    public void showLogin() {
        stage.setScene(new Scene(new LoginView(this), 480, 560));
        stage.centerOnScreen();
    }

    public void showMain() {
        MainShell shell = new MainShell(this);
        stage.setScene(new Scene(shell, 1080, 700));
        stage.centerOnScreen();
        shell.onShown();   // 触发进入后的首次同步
    }

    /** 退出登录：通知服务端注销令牌（失败也继续），清空本地凭据 */
    public void logout() {
        try { api.logout(); } catch (Exception ignored) { }
        config.setToken("");
        config.setUsername("");
        config.save();
        showLogin();
    }

    public void close() {
        db.close();
    }
}
