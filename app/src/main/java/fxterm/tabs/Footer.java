package fxterm.tabs;

import module java.base;
import javafx.geometry.Pos;
import javafx.geometry.Side;
import javafx.scene.control.*;
import javafx.scene.layout.*;

/** The status line. Left: all tabs' RAM together, then Tabs' own, the update status, then the shown tab's folder
 *  (click copies it). Right: the reading controls while a selection is being read aloud, then the actions as quiet
 *  buttons with their keys, so nothing has to be remembered. */
public final class Footer extends HBox {

    /** The theme the buttons take their quiet colours from (one theme for the whole process). */
    static TabbyTheme theme;

    final TabsWindow win;
    final Label all = new Label(), self = new Label(), where = new Label(), update = new Label();
    final Button restoreAll, copyText, paste, find, compose, history, recheck;
    final HBox readBar = new HBox(4);
    final Label speed = new Label();
    final Button pauseRead = small("Pause", "Pause or resume reading", null), stopRead = small("Stop", "Stop reading", null),
            slower = small("−", "Slower", null), faster = small("+", "Faster", null), voice = small("Voice ▾", "Choose the voice", null);

    Footer(TabsWindow app, TabbyTheme theme) {
        Footer.theme = theme;
        this.win = app;
        setStyle(Ui.footer(theme));
        setAlignment(Pos.CENTER_LEFT);
        setSpacing(10);
        all.setMinWidth(Region.USE_PREF_SIZE);
        self.setMinWidth(Region.USE_PREF_SIZE);
        update.setMinWidth(Region.USE_PREF_SIZE);
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
        // the update line: quiet text, with a small button that asks again
        update.setStyle(Ui.footerText(theme));
        update.setTooltip(new Tooltip());
        update.setOnMouseClicked(e -> { if (win.app.updates != null && win.app.updates.available()) openPage(); });
        recheck = small("⟳", "Check for a newer version now", () -> { if (win.app.updates != null) win.app.updates.recheck(); });
        // the reading controls, shown only in the window whose tab is being read
        speed.setStyle(Ui.footerText(theme) + " -fx-opacity: 0.9; -fx-min-width: 30; -fx-alignment: center;");
        var reading = new Label("Reading");
        reading.setStyle(Ui.footerText(theme) + " -fx-text-fill: " + Ui.mix(theme.foreground, "#ffd65a", 0.7) + ";");
        pauseRead.setOnAction(e -> win.app.reader.toggle());
        stopRead.setOnAction(e -> win.app.reader.stop());
        slower.setOnAction(e -> win.app.reader.rate(win.app.reader.rate - 0.1));
        faster.setOnAction(e -> win.app.reader.rate(win.app.reader.rate + 0.1));
        voice.setOnAction(e -> voiceMenu());
        readBar.setAlignment(Pos.CENTER_LEFT);
        readBar.getChildren().addAll(reading, pauseRead, stopRead, slower, speed, faster, voice);
        readBar.setVisible(false);
        readBar.setManaged(false);
        restoreAll = button("Restore all", theme.colors.get(4), "Wake every sleeping tab", app::wakeAll);
        copyText = button("Copy text", null, "Copy everything this tab shows and holds", app::copyText);
        paste = button("Paste", null, "Paste into this tab (also: right-click)", app::paste);
        find = button(Os.keys("Find  Ctrl+F"), null, "Search this tab's text", app::find);
        compose = button(Os.keys("Compose  Ctrl+Shift+E"), theme.colors.get(2), "Write in an editor, then send it to this tab", app::toggleCompose);
        history = button(Os.keys("History  Ctrl+Shift+H"), theme.colors.get(5), "Every saved session: search, read, copy, resume", app::showHistory);
        getChildren().addAll(all, self, update, recheck, where, readBar, restoreAll, copyText, paste, find, compose, history);
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

    /** A quiet button: a shade lighter than the footer with a hairline edge and the theme's own text. An accent colour
     *  (null for none) only tints the text and the edge, so the row blends in and the few actions still differ.
     *  The look itself is the .tabs-button rule of the stylesheet (hover, pressed, disabled). */
    static Button button(String text, String accent, String tip, Runnable action) {
        var b = new Button(text);
        b.setFocusTraversable(false);
        // never squeezed into "Pa...": when the line is too short it is the folder path that gives way
        b.setMinWidth(Region.USE_PREF_SIZE);
        b.getStyleClass().add("tabs-button");
        tint(b, accent);
        b.setTooltip(new Tooltip(tip));
        if (action != null) b.setOnAction(e -> action.run());
        return b;
    }

    /** The same, smaller: for a bar that sits under an editor, or beside a reading. */
    static Button small(String text, String tip, Runnable action) {
        var b = button(text, null, tip, action);
        b.getStyleClass().add("small");
        return b;
    }

    static void tint(Button b, String accent) {
        if (accent == null || accent.equals("#4a4a4a") || theme == null) return;
        b.setStyle("-fx-text-fill: " + Ui.mix(theme.foreground, accent, 0.45) + "; -fx-border-color: " + Ui.mix(Ui.shade(theme.background, 30), accent, 0.35) + ";");
    }

    static void openPage() {
        try {
            new ProcessBuilder("rundll32", "url.dll,FileProtocolHandler", UpdateCheck.PAGE).start();
        } catch (IOException e) {
            System.err.println("fxterm-tabs: could not open " + UpdateCheck.PAGE + ": " + e);
        }
    }

    /** tabs: this window's (for Restore all); every: all windows' (for the RAM total). */
    void update(TerminalTab selected, List<TerminalTab> withSeparators, List<TerminalTab> everyWithSeparators, long selfBytes) {
        var tabs = withSeparators.stream().filter(t -> !t.separator).toList();
        var every = everyWithSeparators.stream().filter(t -> !t.separator).toList();
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

    /** The update line and its button, after a check starts or ends. */
    void updateStatus(UpdateCheck u) {
        if (u == null) return;
        update.setText(u.status());
        update.setStyle(Ui.footerText(theme) + (u.available() ? " -fx-text-fill: " + Ui.mix(theme.foreground, "#7ec27e", 0.8) + "; -fx-cursor: hand; -fx-opacity: 1;" : ""));
        ((Tooltip) update.getTooltip()).setText(u.detail());
        recheck.setDisable(u.checking);
    }

    /** The reading controls: shown in the window of the tab being read, with the speed and what Pause says now. */
    void reading(ReadTerminal r) {
        var here = r.reading() && r.tab != null && r.tab.owner == win;
        readBar.setVisible(here);
        readBar.setManaged(here);
        pauseRead.setText(r.paused() ? "Resume" : "Pause");
        speed.setText("%.1f×".formatted(r.rate));
    }

    void voiceMenu() {
        var r = win.app.reader;
        r.voices().thenAccept(names -> javafx.application.Platform.runLater(() -> {
            var menu = new ContextMenu();
            var current = r.selectedVoice();
            for (var n : names) {
                var item = new CheckMenuItem(n);
                item.setMnemonicParsing(false);
                item.setSelected(n.equals(current));
                item.setOnAction(e -> r.select(n));
                menu.getItems().add(item);
            }
            menu.show(voice, Side.TOP, 0, 0);
        }));
    }
}
