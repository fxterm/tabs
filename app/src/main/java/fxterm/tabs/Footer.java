package fxterm.tabs;

import module java.base;
import javafx.geometry.Pos;
import javafx.geometry.Side;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.shape.Circle;
import javafx.scene.paint.Color;

/** The status line. Left: all tabs' RAM together, then Tabs' own (two quiet chips with a coloured dot), the update
 *  status, then the shown tab's folder (click copies it). Right: the reading controls while a selection is being read
 *  aloud, the magnification, then the actions as flat buttons with their keys, so nothing has to be remembered.
 *  History and Restore all live in the toolbar above the tab list. */
public final class Footer extends HBox {

    /** The theme the buttons take their quiet colours from (one theme for the whole process). */
    static TabbyTheme theme;

    final TabsWindow win;
    final Label all = new Label(), self = new Label(), where = new Label(), update = new Label(), zoom = new Label();
    final Button copyText, paste, find, compose, recheck, zoomOut, zoomIn;
    final Circle allDot = new Circle(4), selfDot = new Circle(4);
    final HBox readBar = new HBox(4);
    final Label speed = new Label();
    final Button pauseRead = small("Pause", "Pause or resume reading", null), stopRead = small("Stop", "Stop reading", null),
            slower = small("−", "Slower", null), faster = small("+", "Faster", null), voice = small("Voice ▾", "Choose the voice", null);

    Footer(TabsWindow app, TabbyTheme theme) {
        Footer.theme = theme;
        this.win = app;
        setStyle(Ui.footer(theme));
        setAlignment(Pos.CENTER_LEFT);
        setSpacing(8);
        for (var l : List.of(all, self, update)) l.setMinWidth(Region.USE_PREF_SIZE);
        all.setGraphic(allDot);
        all.setTooltip(new Tooltip("Every live tab's shell and the program it runs (claude), together"));
        self.setGraphic(selfDot);
        selfDot.setFill(Color.web("#8b949e"));
        self.setTooltip(new Tooltip("What Tabs itself uses"));
        self.setStyle(chip("#8b949e"));
        where.setStyle(Ui.footerText(theme) + " -fx-cursor: hand; -fx-underline: false; -fx-padding: 0 0 0 6;");
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
        // the update line: quiet text, with a small icon button that asks again
        update.setStyle(Ui.footerText(theme));
        update.setTooltip(new Tooltip());
        update.setOnMouseClicked(e -> AppUpdate.now(win.app, win));
        var dim = Ui.mix(theme.foreground, theme.background, 0.38);
        recheck = Icons.button(Icons.REFRESH, 14, dim, theme.colors.get(4), "Check for a newer version now", () -> { if (win.app.updates != null) win.app.updates.recheck(); });
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
        // magnification: the terminals' font size, as a percentage of the starting size
        zoomOut = Icons.button(Icons.MINUS, 11, 2, dim, theme.foreground, Os.keys("Smaller text (Ctrl+-)"), () -> win.app.fontSize(FontSize.get() - FontSize.STEP));
        zoomIn = Icons.button(Icons.PLUS, 12, dim, theme.foreground, Os.keys("Larger text (Ctrl+=)"), () -> win.app.fontSize(FontSize.get() + FontSize.STEP));
        zoom.setStyle(Ui.footerText(theme) + " -fx-min-width: 40; -fx-alignment: center; -fx-cursor: hand;");
        zoom.setTooltip(new Tooltip(Os.keys("Magnification of the terminal text. Click to go back to 100% (Ctrl+0)")));
        zoom.setOnMouseClicked(e -> win.app.fontSize(FontSize.base));
        var magnify = new HBox(0, zoomOut, zoom, zoomIn);
        magnify.setAlignment(Pos.CENTER);
        copyText = flat(Icons.COPY, "Copy text", null, "Copy everything this tab shows and holds", app::copyText);
        paste = flat(Icons.PASTE, "Paste", null, "Paste into this tab (also: right-click)", app::paste);
        find = flat(Icons.SEARCH, "Find", Os.keys("Ctrl+F"), "Search this tab's text", app::find);
        compose = flat(Icons.COMPOSE, "Compose", Os.keys("Ctrl+Shift+E"), "Write in an editor, then send it to this tab", app::toggleCompose);
        var updateBox = new HBox(2, update, recheck);
        updateBox.setAlignment(Pos.CENTER_LEFT);
        getChildren().addAll(all, self, updateBox, where, readBar, magnify, copyText, paste, find, compose);
        magnified();
    }

    /** A flat footer button: an icon, its name and its key in dimmer type; no frame, a soft square on hover. */
    static Button flat(String icon, String text, String key, String tip, Runnable action) {
        var dim = Ui.mix(theme.foreground, theme.background, 0.38);
        var ic = Icons.icon(icon, 14, dim);
        var label = new Label(text);
        var box = new HBox(6, ic, label);
        box.setAlignment(Pos.CENTER_LEFT);
        if (key != null) {
            var k = new Label(key);
            k.getStyleClass().add("key");
            box.getChildren().add(k);
        }
        var b = new Button();
        b.setGraphic(box);
        b.setFocusTraversable(false);
        b.setMinWidth(Region.USE_PREF_SIZE);
        b.getStyleClass().add("flat-button");
        b.setTooltip(new Tooltip(tip));
        b.setOnAction(e -> action.run());
        Icons.tint(b, ic, dim, theme.foreground);
        return b;
    }

    /** A flat rounded label with a faint tint of the colour behind it (both RAM figures use it, so they sit level). */
    static String chip(String colour) {
        return "-fx-background-color: " + Ui.mix(Ui.shade(theme.background, -10), colour, 0.16) + "; -fx-background-radius: 11;"
                + " -fx-padding: 2 10 2 8; -fx-graphic-text-gap: 6; -fx-text-fill: " + Ui.mix(theme.foreground, theme.background, 0.12) + "; -fx-font-size: 11.5;";
    }

    /** All tabs together: blue under 250 MB, yellow from 250, orange from 500, red from 800 (any claude session gets
     *  there, so with claude it is mostly red; plain terminal programs stay low). */
    static String totalColour(long bytes) {
        var mb = bytes / (1024.0 * 1024);
        return mb >= 800 ? "#e5534b" : mb >= 500 ? "#e8873a" : mb >= 250 ? "#d4aa3a" : "#4c8dff";
    }

    /** A quiet button: a shade lighter than the footer with a hairline edge and the theme's own text. An accent colour
     *  (null for none) only tints the text and the edge, so the row blends in and the few actions still differ.
     *  The look itself is the .tabs-button rule of the stylesheet (hover, pressed, disabled). Used by the selection bar,
     *  the compose box and History; the footer itself uses flat(). */
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

    /** tabs: this window's; every: all windows' (for the RAM total). */
    void update(TerminalTab selected, List<TerminalTab> withSeparators, List<TerminalTab> everyWithSeparators, long selfBytes) {
        var every = everyWithSeparators.stream().filter(t -> !t.separator).toList();
        var everyLive = every.stream().filter(TerminalTab::live).count();
        var total = every.stream().mapToLong(t -> t.live() ? t.ramBytes : 0).sum();
        all.setText("All tabs " + Mem.mb(total) + "  ·  " + everyLive + " live, " + (every.size() - everyLive) + " asleep");
        var colour = totalColour(total);
        allDot.setFill(Color.web(colour));
        all.setStyle(chip(colour));
        self.setText("Tabs app " + Mem.mb(selfBytes));
        if (!where.getText().startsWith("copied: ")) where.setText(selected == null ? "" : selected.where());
        var on = selected != null && selected.live();
        copyText.setDisable(selected == null);
        paste.setDisable(!on);
        find.setDisable(!on);
        compose.setDisable(!on);
    }

    /** The magnification, after the font size changed: the size as a percentage of the starting one. */
    void magnified() {
        zoom.setText(Math.round(FontSize.get() / FontSize.base * 100) + "%");
    }

    /** The update line and its button, after a check starts or ends. */
    void updateStatus(UpdateCheck u) {
        if (u == null) return;
        if (JarWatch.changed) {
            update.setText(JarWatch.MESSAGE);
            update.setStyle(Ui.footerText(theme) + " -fx-text-fill: " + Ui.mix(theme.foreground, "#ff7a7a", 0.9) + "; -fx-opacity: 1;");
            ((Tooltip) update.getTooltip()).setText("dist\\fxterm-tabs.jar was replaced while this window runs from it, so anything not loaded yet can fail. Close Tabs and start it again.");
            return;
        }
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
