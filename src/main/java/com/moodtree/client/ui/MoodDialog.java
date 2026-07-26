/* 记心情弹窗：10 个心情色块 + 备注。新建和编辑共用（编辑传入已有记录）。 */
package com.moodtree.client.ui;

import com.moodtree.client.model.MoodEntry;
import com.moodtree.client.model.MoodMeta;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.VBox;

import java.time.LocalDate;

public class MoodDialog extends Dialog<MoodEntry> {

    public MoodDialog(LocalDate date, MoodEntry editing) {
        setTitle(editing == null ? "记录心情" : "修改记录");
        setHeaderText(editing == null
                ? date.getMonthValue() + "月" + date.getDayOfMonth() + "日，现在感觉怎么样？"
                : "修改这条心情记录");
        getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);
        ((javafx.scene.control.Button) getDialogPane().lookupButton(ButtonType.OK)).setText("保存");
        ((javafx.scene.control.Button) getDialogPane().lookupButton(ButtonType.CANCEL)).setText("取消");

        ToggleGroup group = new ToggleGroup();
        GridPane grid = new GridPane();
        grid.setHgap(8);
        grid.setVgap(8);
        int i = 0;
        for (MoodMeta m : MoodMeta.all()) {
            ToggleButton tile = new ToggleButton(m.emoji + " " + m.label);
            tile.setUserData(m.key);
            tile.setToggleGroup(group);
            tile.setPrefSize(104, 44);
            tile.setStyle("-fx-background-color: " + m.color + "; -fx-background-radius: 10;"
                    + "-fx-font-size: 14px; -fx-text-fill: #3d3a34; -fx-cursor: hand;");
            // 选中态：深色描边
            group.selectedToggleProperty().addListener((obs, old, now) -> {
                boolean sel = now != null && m.key.equals(now.getUserData());
                tile.setStyle("-fx-background-color: " + m.color + "; -fx-background-radius: 10;"
                        + "-fx-font-size: 14px; -fx-text-fill: #3d3a34; -fx-cursor: hand;"
                        + (sel ? "-fx-border-color: #3d3a34; -fx-border-width: 2; -fx-border-radius: 10;" : ""));
            });
            if (editing != null && m.key.equals(editing.mood)) tile.setSelected(true);
            grid.add(tile, i % 5, i / 5);
            i++;
        }

        Label noteLabel = new Label("想说点什么？（可以留空）");
        noteLabel.setStyle(Theme.soft());
        TextArea note = new TextArea(editing == null ? "" : editing.note);
        note.setPromptText("今天发生了什么…");
        note.setPrefRowCount(3);
        note.setWrapText(true);
        note.setStyle(Theme.input());

        VBox body = new VBox(14, grid, noteLabel, note);
        body.setPadding(new Insets(16));
        body.setAlignment(Pos.CENTER_LEFT);
        getDialogPane().setContent(body);
        getDialogPane().setStyle(Theme.page());

        setResultConverter(bt -> {
            if (bt != ButtonType.OK || group.getSelectedToggle() == null) return null;
            String mood = (String) group.getSelectedToggle().getUserData();
            if (editing != null) {
                editing.mood = mood;
                editing.note = note.getText().trim();
                editing.touchLocal();
                return editing;
            }
            return MoodEntry.create(date, mood, note.getText());
        });
    }
}
