/* 视觉主题：与网页版一致的"温暖治愈"基调（米色底 + 灰绿点缀）。
 * JavaFX 用内联样式，色值集中在这里方便统一调整。 */
package com.moodtree.client.ui;

public class Theme {
    public static final String BG       = "#f5f1e8";   // 页面底色（米色）
    public static final String CARD     = "#fffdf7";   // 卡片底色
    public static final String INK      = "#3d3a34";   // 主文字
    public static final String INK_SOFT = "#8a857a";   // 次要文字
    public static final String ACCENT   = "#7d9b76";   // 灰绿（主按钮/选中）
    public static final String ACCENT_D = "#65835f";   // 灰绿按下
    public static final String SIDEBAR  = "#efe9db";   // 侧边栏底色
    public static final String DANGER   = "#c9706a";   // 删除/危险

    public static String page() {
        return "-fx-background-color: " + BG + ";";
    }

    public static String card() {
        return "-fx-background-color: " + CARD + ";"
             + "-fx-background-radius: 12;"
             + "-fx-effect: dropshadow(gaussian, rgba(0,0,0,0.06), 8, 0, 0, 2);";
    }

    public static String primaryBtn() {
        return "-fx-background-color: " + ACCENT + "; -fx-text-fill: white;"
             + "-fx-background-radius: 8; -fx-padding: 8 20; -fx-font-size: 14px; -fx-cursor: hand;";
    }

    public static String ghostBtn() {
        return "-fx-background-color: transparent; -fx-text-fill: " + INK_SOFT + ";"
             + "-fx-padding: 8 16; -fx-font-size: 14px; -fx-cursor: hand;";
    }

    public static String dangerBtn() {
        return "-fx-background-color: transparent; -fx-text-fill: " + DANGER + ";"
             + "-fx-padding: 4 10; -fx-font-size: 12px; -fx-cursor: hand;";
    }

    public static String input() {
        return "-fx-background-color: " + CARD + "; -fx-border-color: #ddd5c4;"
             + "-fx-border-radius: 8; -fx-background-radius: 8; -fx-padding: 10 12; -fx-font-size: 14px;";
    }

    public static String h1() { return "-fx-font-size: 22px; -fx-font-weight: bold; -fx-text-fill: " + INK + ";"; }
    public static String h2() { return "-fx-font-size: 16px; -fx-font-weight: bold; -fx-text-fill: " + INK + ";"; }
    public static String soft() { return "-fx-text-fill: " + INK_SOFT + "; -fx-font-size: 13px;"; }
}
