module com.moodtree.client {
    requires javafx.controls;
    requires javafx.media;           // 在线音乐播放
    requires javafx.swing;           // AWT 彩色 Emoji 转 JavaFX
    requires javafx.web;             // WebView 小游戏
    requires java.desktop;           // Desktop.browse 打开视频链接
    requires java.net.http;          // 调服务器 API
    requires java.sql;               // JDBC 访问本地 SQLite
    requires com.google.gson;        // JSON
    requires org.xerial.sqlitejdbc;  // SQLite 驱动

    exports com.moodtree.client;
}
