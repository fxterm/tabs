package fxterm.tabs;

import module java.base;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.*;

/** The status line. Left: all tabs' RAM together, then Tabs' own, then the shown tab's folder (click copies it).
 *  Right: the actions as coloured buttons with their keys, so nothing has to be remembered. */
public final class Footer extends HBox {

    final Label all = new Label(), self = new Label(), where = new Label();
    final Button restoreAll, copyText, paste, find, compose;

    Footer(TabsWindow app, TabbyTheme theme) {
        setStyle(Ui.footer(theme));
        setAlignment(Pos.CENTER_LEFT);
        setSpacing(12);
        all.setStyle(pill("#d64545"));
        all.setTooltip(new Tooltip("Every live tab's shell and the program it runs (claude), together"));
        self.setStyle(pill("#4a4a4a"));
        self.setTooltip(new Tooltip("What Tabs itself uses"));
        where.setStyle(Ui.footerText(theme) + " -fx-cursor: hand; -fx-underline: false;");
        where.setTooltip(new Tooltip("Click to copy this folder"));
        where.setOnMouseEntered(e -> where.setUnderline(true));
        where.setOnMouseExited(e -> where.setUnderline(false));
        where.setOnMouseClicked(e -> {
            app.copy(where.getText());
            var was = where.getText();
            where.setText("copied: " + was);
            javafx.animation.PauseTransition back = new javafx.animation.PauseTransition(javafx.util.Duration.millis(1200));
            back.setOnFinished(x -> { if (where.getText().equals("copied: " + was)) where.setText(was); });
            back.play();
        });
        where.setMinWidth(0);
        HBox.setHgrow(where, Priority.ALWAYS);
        where.setMaxWidth(Double.MAX_VALUE);
        restoreAll = button("Restore all", theme.colors.get(4), "Wake every sleeping tab", app::wakeAll);
        copyText = button("Copy text", "#4a4a4a", "Copy everything this tab shows and holds", app::copyText);
        paste = button("Paste", "#4a4a4a", "Paste into this tab (also: right-click)", app::paste);
        find = button("Find  Ctrl+F", "#4a4a4a", "Search this tab's text", app::find);
        compose = button("Compose  Ctrl+Shift+E", theme.colors.get(2), "Write in an editor, then send it to this tab", app::toggleCompose);
        getChildren().addAll(all, self, where, restoreAll, copyText, paste, find, compose);
    }

    /** A rounded label with a coloured background; both RAM figures use it, so they sit level on one line. */
    static String pill(String colour) {
        return "-fx-background-color: " + colour + "; -fx-text-fill: #ffffff; -fx-font-size: 12; -fx-font-weight: bold;"
                + " -fx-padding: 2 8 2 8; -fx-background-radius: 4;";
    }

    /** All tabs together: blue under 250 MB, yellow from 250, orange from 500, red from 800 (any claude session gets
     *  there, so with claude it is mostly red; plain terminal programs stay low). */
    static String totalColour(long bytes) {
        var mb = bytes / (1024.0 * 1024);
        return mb >= 800 ? "#d64545" : mb >= 500 ? "#e07b24" : mb >= 250 ? "#b8941f" : "#2f6fd6";
    }

    static Button button(String text, String colour, String tip, Runnable action) {
        var b = new Button(text);
        b.setFocusTraversable(false);
        b.setStyle("-fx-background-color: " + colour + "; -fx-text-fill: #ffffff; -fx-font-size: 11; -fx-font-weight: bold;"
                + " -fx-padding: 2 8 2 8; -fx-background-radius: 4; -fx-cursor: hand;");
        b.setTooltip(new Tooltip(tip));
        b.setOnAction(e -> action.run());
        return b;
    }

    /** tabs: this window's (for Restore all); every: all windows' (for the RAM total). */
    void update(TerminalTab selected, List<TerminalTab> tabs, List<TerminalTab> every, long selfBytes) {
        var everyLive = every.stream().filter(TerminalTab::live).count();
        var total = every.stream().mapToLong(t -> t.live() ? t.ramBytes : 0).sum();
        all.setText("All tabs " + Mem.mb(total) + "  (" + everyLive + " live, " + (every.size() - everyLive) + " asleep)");
        all.setStyle(pill(totalColour(total)));
        var live = tabs.stream().filter(TerminalTab::live).count();
        self.setText("Tabs app " + Mem.mb(selfBytes));
        if (!where.getText().startsWith("copied: ")) where.setText(selected == null ? "" : selected.where());
        restoreAll.setText("Restore all (" + (tabs.size() - live) + ")");
        restoreAll.setVisible(live < tabs.size());
        restoreAll.setManaged(live < tabs.size());
        var on = selected != null && selected.live();
        copyText.setDisable(selected == null);
        paste.setDisable(!on);
        find.setDisable(!on);
        compose.setDisable(!on);
    }
}
