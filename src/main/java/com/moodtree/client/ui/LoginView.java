/* 登录页：服务器地址 + 账号密码。登录成功后拉目录缓存并做首次同步。 */
package com.moodtree.client.ui;

import com.google.gson.JsonObject;
import com.moodtree.client.AppContext;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
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

        TextField server = new TextField(app.config.serverBase());
        server.setPromptText("服务器地址");
        server.setStyle(Theme.input());
        server.setMaxWidth(320);

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

        Label hint = new Label("没有账号？先在网页版注册：" + app.config.serverBase());
        hint.setStyle("-fx-text-fill: " + Theme.INK_SOFT + "; -fx-font-size: 12px;");

        Region gap = new Region();
        gap.setPrefHeight(8);

        login.setOnAction(e -> {
            app.config.setServerBase(server.getText());
            app.config.save();
            login.setDisable(true);
            status.setText("正在登录…");
            Bg.run(() -> {
                        JsonObject r = app.api.login(username.getText().trim(), password.getText());
                        app.config.setToken(r.get("token").getAsString());
                        app.config.setUsername(r.get("username").getAsString());
                        app.config.save();
                        app.sync.refreshCatalog();      // 离线推荐用的内容缓存
                        app.sync.sync();                // 把服务器上的历史记录拉下来
                        return null;
                    },
                    ok -> app.showMain(),
                    err -> {
                        login.setDisable(false);
                        status.setText(err instanceof com.moodtree.client.api.ApiClient.ApiException
                                ? err.getMessage() : "登录失败：" + err.getMessage());
                    });
        });

        getChildren().addAll(logo, title, sub, gap, server, username, password, login, status, hint);
    }
}
