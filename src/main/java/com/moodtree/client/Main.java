/* 心情树洞 Windows 桌面客户端 —— 程序入口。
 * 离线可用、联网与服务器（/api/v1/）同步数据，界面独立于网页版。 */
package com.moodtree.client;

import javafx.application.Application;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

public class Main extends Application {

    @Override
    public void start(Stage stage) {
        Label title = new Label("🌳 心情树洞");
        title.setStyle("-fx-font-size: 28px;");

        Label sub = new Label("桌面客户端骨架已跑通，功能开发中…");
        sub.setStyle("-fx-font-size: 14px; -fx-text-fill: #6b6b6b;");

        VBox root = new VBox(16, title, sub);
        root.setAlignment(Pos.CENTER);
        root.setStyle("-fx-background-color: #f5f1e8;");   // 与网页版一致的米色基调

        stage.setTitle("心情树洞");
        stage.setScene(new Scene(root, 480, 320));
        stage.show();
    }

    public static void main(String[] args) {
        launch(args);
    }
}
