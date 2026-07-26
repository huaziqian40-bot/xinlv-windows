/* 视觉主题：预设主题 + 自定义强调色，运行时可切换（切换后重建界面生效）。
 * 字段是 static 可变值，各界面通过 Theme.xxx / 样式方法引用。
 * 基调与网页版一致："温暖治愈"，不写死高饱和冷色。 */
package com.moodtree.client.ui;

public class Theme {

    // ---- 当前生效的色板（apply() 会改这些值）----
    public static String BG       = "#f5f1e8";   // 页面底色
    public static String CARD     = "#fffdf7";   // 卡片底色
    public static String INK      = "#3d3a34";   // 主文字
    public static String INK_SOFT = "#8a857a";   // 次要文字
    public static String ACCENT   = "#7d9b76";   // 强调色（按钮/选中）
    public static String ACCENT_D = "#65835f";   // 强调色（深色变体）
    public static String SIDEBAR  = "#efe9db";   // 侧边栏底色
    public static String DANGER   = "#c9706a";   // 删除/危险

    /** 预设主题。新增主题在这里加 case + PRESETS 条目。 */
    public static final String[][] PRESETS = {
            {"warm",   "暖阳", "#f5f1e8"},
            {"night",  "夜晚", "#26241f"},
            {"mint",   "薄荷", "#eef6f1"},
            {"sakura", "樱花", "#faf0f2"},
    };

    /** 应用预设主题；accentHex 非空时覆盖强调色（自定义）。 */
    public static void apply(String id, String accentHex) {
        switch (id == null ? "warm" : id) {
            case "night" -> set("#26241f", "#35322b", "#ece7db", "#a09a8b",
                                "#2e2b25", "#7d9b76", "#93b18b", "#d08078");
            case "mint"  -> set("#eef6f1", "#fbfffc", "#33403a", "#7d8d85",
                                "#e0efe7", "#5ea07c", "#4c8767", "#c9706a");
            case "sakura"-> set("#faf0f2", "#fffafa", "#43353a", "#97828a",
                                "#f5e3e8", "#d18a9a", "#b87383", "#c9706a");
            default      -> set("#f5f1e8", "#fffdf7", "#3d3a34", "#8a857a",
                                "#efe9db", "#7d9b76", "#65835f", "#c9706a");
        }
        if (accentHex != null && !accentHex.isBlank()) {
            ACCENT = accentHex;
            ACCENT_D = darker(accentHex);
        }
    }

    private static void set(String bg, String card, String ink, String inkSoft,
                            String sidebar, String accent, String accentD, String danger) {
        BG = bg; CARD = card; INK = ink; INK_SOFT = inkSoft;
        SIDEBAR = sidebar; ACCENT = accent; ACCENT_D = accentD; DANGER = danger;
    }

    /** 把 #rrggbb 调暗一档（强调色按下/描边用） */
    private static String darker(String hex) {
        try {
            javafx.scene.paint.Color c = javafx.scene.paint.Color.web(hex).darker();
            return String.format("#%02x%02x%02x",
                    (int) (c.getRed() * 255), (int) (c.getGreen() * 255), (int) (c.getBlue() * 255));
        } catch (Exception e) {
            return hex;
        }
    }

    // ---- 常用样式片段 ----

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
             + "-fx-border-radius: 8; -fx-background-radius: 8; -fx-padding: 10 12; -fx-font-size: 14px;"
             + "-fx-text-fill: " + INK + ";";
    }

    public static String h1() { return "-fx-font-size: 22px; -fx-font-weight: bold; -fx-text-fill: " + INK + ";"; }
    public static String h2() { return "-fx-font-size: 16px; -fx-font-weight: bold; -fx-text-fill: " + INK + ";"; }
    public static String soft() { return "-fx-text-fill: " + INK_SOFT + "; -fx-font-size: 13px;"; }
}
