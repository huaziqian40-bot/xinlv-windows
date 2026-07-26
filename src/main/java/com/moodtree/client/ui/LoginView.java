/* 登录页：只留账号密码（服务器地址在"我的→设置"里改，不在这里暴露技术细节）。
 * 不想注册可以点"先逛逛"以游客身份使用，数据存本机，登录后自动同步上云。 */
package com.moodtree.client.ui;

import com.google.gson.JsonObject;
import com.moodtree.client.AppContext;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Hyperlink;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

public class LoginView extends VBox {

    public LoginView(AppContext app) {
        setAlignment(Pos.CENTER);
        setSpacing(14);
        setPadding(new Insets(40));
        setStyle(Theme.page());

        Label logo = new Label("🌳");
        logo.setStyle("-fx-font-size: 56px;");
        Label title = new Label("心情树洞");
        title.setStyle(Theme.h1());
        Label sub = new Label("把心情种下，让它慢慢发芽");
        sub.setStyle(Theme.soft());

        TextField username = new TextField();
        username.setPromptText("账号");
        username.setStyle(Theme.input());
        username.setMaxWidth(320);

        PasswordField password = new PasswordField();
        password.setPromptText("密码");
        password.setStyle(Theme.input());
        password.setMaxWidth(320);

        Button login = new Button("登 录");
        login.setStyle(Theme.primaryBtn());
        login.setDefaultButton(true);
        login.setMaxWidth(320);

        Label status = new Label();
        status.setStyle(Theme.soft());
        status.setWrapText(true);
        status.setMaxWidth(320);

        Hyperlink guest = new Hyperlink("没有账号？先随便逛逛（游客模式）");
        guest.setStyle("-fx-text-fill: " + Theme.ACCENT_D + "; -fx-font-size: 13px;");
        Label guestNote = new Label("游客的数据只保存在这台电脑，登录后自动同步到云端");
        guestNote.setStyle("-fx-text-fill: " + Theme.INK_SOFT + "; -fx-font-size: 12px;");

        Region gap = new Region();
        gap.setPrefHeight(8);

        login.setOnAction(e -> {
            login.setDisable(true);
            status.setText("正在登录…");
            Bg.run(() -> {
                        JsonObject r = app.api.login(username.getText().trim(), password.getText());
                        app.config.setToken(r.get("token").getAsString());
                        app.config.setUsername(r.get("username").getAsString());
                        app.config.setGuestMode(false);   // 转正：不再是游客
                        app.config.save();
                        app.sync.refreshCatalog();        // 离线推荐用的内容缓存
                        app.sync.sync();                  // 游客期间的记录会在这里一并推上云
                        return null;
                    },
                    ok -> app.showMain(),
                    err -> {
                        login.setDisable(false);
                        status.setText(err instanceof com.moodtree.client.api.ApiClient.ApiException
                                ? err.getMessage() : "登录失败：" + err.getMessage());
                    });
        });

        guest.setOnAction(e -> app.enterGuest());

        getChildren().addAll(logo, title, sub, gap, username, password, login, status, guest, guestNote);
    }
}
