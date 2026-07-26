module com.moodtree.client {
    requires javafx.controls;
    requires java.net.http;          // 调服务器 API
    requires java.sql;               // JDBC 访问本地 SQLite
    requires com.google.gson;        // JSON
    requires org.xerial.sqlitejdbc;  // SQLite 驱动

    exports com.moodtree.client;
}
