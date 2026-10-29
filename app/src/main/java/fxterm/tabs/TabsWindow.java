package fxterm.tabs;

import module java.base;
import javafx.application.Platform;
import javafx.collections.*;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.input.*;
import javafx.scene.layout.*;
import javafx.stage.*;

/** One window: tabs listed down the left, the shown tab's terminal, the compose box and footer under it; only the
 *  shown tab is attached and painting. Several windows can be open in one process: a tab dragged out of a window, or
 *  sent with "Move to window", moves with its process still running, and a window left with no tabs vanishes.
 *  Keys (see keys()): Ctrl+Shift+T new tab, Ctrl+Shift+N new window, Ctrl+Shift+W close, Ctrl+Tab / Ctrl+Shift+Tab,
 *  Alt or Ctrl+1..9, Ctrl+Shift+PgUp/PgDn move, Ctrl+Shift+E compose, F11 full screen, Ctrl+= / Ctrl+- / Ctrl+0
 *  font size (also Ctrl+wheel and pinch over the terminal). */
public final class TabsWindow {

    final TabsApp app;
    final Options options;
    final TabbyTheme theme;
    final Stage stage;
    final ObservableList<TerminalTab> tabs = FXCollections.observableArrayList();
    final StackPane content = new StackPane();
    final SplitPane split = new SplitPane();
    /** Under the terminal: the compose box when open, then the footer. */
    final VBox bottom = new VBox();
    final ListView<TerminalTab> list;
    final Sidebar sidebar;
    final Footer footer;
    final Composer composer;
    final Scene scene;
    boolean moving;

    TabsWindow(TabsApp app, Stage stage, WindowState ws) {
        this.app = app;
        this.options = app.options;
        this.theme = app.theme;
        this.stage = stage;
        footer = new Footer(this, theme);
        composer = new Composer(this, theme);
        sidebar = new Sidebar(this, tabs, theme);
        list = sidebar.list;
        list.getSelectionModel().selectedItemProperty().addListener((ov, was, now) -> {
            // a separator cannot be chosen: the choice goes back to the tab that had it (or the nearest real tab)
            if (now != null && now.separator) {
                // only if the separator is still chosen by then: closing a tab may pass through one on its way
                Platform.runLater(() -> {
                    if (selected() != now) return;
                    list.getSelectionModel().select(was != null && tabs.contains(was) && !was.separator ? was : nearestReal(tabs.indexOf(now)));
                });
                return;
            }
            show(now);
        });
        stage.iconifiedProperty().addListener((ov, was, now) -> paint());
        scene = new Scene(layout(), ws.width, ws.height);
        scene.getStylesheets().add(Ui.stylesheet(theme));
        // JavaFX sets the title bar dark or light from this itself, overriding any DWM call made without it
        scene.getPreferences().setColorScheme(theme.dark() ? javafx.application.ColorScheme.DARK : javafx.application.ColorScheme.LIGHT);
        scene.addEventFilter(KeyEvent.KEY_PRESSED, this::keys);
        scene.addEventFilter(KeyEvent.ANY, e -> IdleTrim.activity());
        // a new press anywhere in the window (a click, a right-click paste) closes the selection's popup
        scene.addEventFilter(MouseEvent.MOUSE_PRESSED, e -> hideSelectionPopup());
        scene.addEventFilter(MouseEvent.MOUSE_CLICKED, e -> { if (e.getButton() == MouseButton.SECONDARY) hideSelectionPopup(); });
        // font size by gesture: a touch screen's pinch arrives as zoom events, a touchpad's pinch on Windows as
        // Ctrl+wheel, which is also the mouse's way
        var pinch = new double[] {1};
        scene.addEventFilter(ZoomEvent.ZOOM_STARTED, e -> pinch[0] = FontSize.get());
        scene.addEventFilter(ZoomEvent.ZOOM, e -> {
            if (inside(e.getPickResult().getIntersectedNode(), content)) { app.fontSize(pinch[0] * e.getTotalZoomFactor()); e.consume(); }
        });
        // a wheel notch is 40; a touchpad pinch sends many small deltas, so they are added up to a notch per step
        var wheel = new double[] {0};
        scene.addEventFilter(ScrollEvent.SCROLL, e -> {
            if (!e.isControlDown() || e.getDeltaY() == 0 || !inside(e.getPickResult().getIntersectedNode(), content)) return;
            wheel[0] += e.getDeltaY();
            var steps = (int) (wheel[0] / 40);
            if (steps != 0) {
                wheel[0] -= steps * 40;
                app.fontSize(FontSize.get() + steps * FontSize.STEP);
            }
            e.consume();
        });
        // the keys always belong to the shown tab: a click in the tab list, or coming back to the window, must not
        // leave them with the list or with nothing
        scene.focusOwnerProperty().addListener((ov, was, now) -> { if (now == null || inside(now, sidebar.node)) focusSoon(); });
        stage.focusedProperty().addListener((ov, was, now) -> {
            if (!now) return;
            app.lastFocused = this;
            focusSoon();
        });
        list.addEventHandler(MouseEvent.MOUSE_RELEASED, e -> focusSoon());
        stage.setScene(scene);
        stage.getIcons().addAll(new Image(TabsApp.class.getResourceAsStream("icon-256.png")), new Image(TabsApp.class.getResourceAsStream("icon-32.png")));
        stage.setOnCloseRequest(e -> { e.consume(); app.closeWindow(this); });
        if (options.offscreen) {
            stage.initStyle(StageStyle.UTILITY);
            stage.setX(-5000);
            stage.setY(0);
        } else {
            ws.apply(stage);
        }
    }

    /** Shows the window, with the tab list at its saved width. */
    void open(WindowState ws) {
        stage.show();
        TitleBar.paintSoon(theme);
        split.setDividerPositions(ws.sidebar / stage.getWidth());
    }

    /** The tab list and the terminal side by side with a draggable divider; the list keeps its width when the window
     *  is resized. */
    BorderPane layout() {
        split.getItems().setAll(sidebar.node, content);
        SplitPane.setResizableWithParent(sidebar.node, false);
        split.setStyle("-fx-background-color: " + Ui.shade(theme.background, -10) + "; -fx-padding: 0; -fx-box-border: transparent;");
        var root = new BorderPane(split);
        bottom.getChildren().setAll(footer);
        root.setBottom(bottom);
        return root;
    }

    double sidebarWidth() {
        return sidebar.node.getWidth();
    }

    TerminalTab adopt(TerminalTab t) {
        tabs.add(t);
        t.owner = this;
        return t;
    }

    /** A tab arriving from another window: placed at that index and shown, its process still running. */
    void put(TerminalTab t, int index) {
        tabs.add(Math.clamp(index, 0, tabs.size()), t);
        t.owner = this;
        list.getSelectionModel().select(t);
        refresh();
    }

    void keys(KeyEvent e) {
        hideSelectionPopup();
        var ctrlShift = e.isControlDown() && e.isShiftDown() && !e.isAltDown();
        var alt = e.isAltDown() && !e.isControlDown() && !e.isShiftDown();
        var ctrlOnly = e.isControlDown() && !e.isAltDown() && !e.isShiftDown();
        var code = e.getCode();
        if (ctrlShift && code == KeyCode.T) add();
        else if (ctrlShift && code == KeyCode.N) app.newWindow(null, Double.NaN, Double.NaN, this);
        else if (ctrlShift && code == KeyCode.W) close(selected());
        else if (ctrlShift && code == KeyCode.E) toggleCompose();
        else if (ctrlShift && code == KeyCode.H) { if (historyShown()) hideHistory(); else showHistory(); }
        else if (ctrlShift && (code == KeyCode.PAGE_UP || code == KeyCode.PAGE_DOWN) && selected() != null)
            move(selected(), tabs.indexOf(selected()) + (code == KeyCode.PAGE_UP ? -1 : 1));
        else if (e.isControlDown() && code == KeyCode.TAB) step(e.isShiftDown() ? -1 : 1);
        else if ((alt || ctrlOnly) && code.isDigitKey() && code != KeyCode.DIGIT0 && code != KeyCode.NUMPAD0)
            select(code.getCode() - (code.isKeypadKey() ? KeyCode.NUMPAD1 : KeyCode.DIGIT1).getCode());
        else if (e.isControlDown() && !e.isAltDown() && (code == KeyCode.EQUALS || code == KeyCode.PLUS || code == KeyCode.ADD))app.fontSize(FontSize.get() + FontSize.STEP);
        else if (ctrlOnly && (code == KeyCode.MINUS || code == KeyCode.SUBTRACT)) app.fontSize(FontSize.get() - FontSize.STEP);
        else if (ctrlOnly && (code == KeyCode.DIGIT0 || code == KeyCode.NUMPAD0)) app.fontSize(FontSize.base);
        else if (code == KeyCode.F11 &&!e.isControlDown() && !e.isAltDown() && !e.isShiftDown()) stage.setFullScreen(!stage.isFullScreen());
        else return;
        e.consume();
    }

    /** Ctrl+Tab and Ctrl+Shift+Tab: the next or previous real tab, round the end, over separators. */
    void step(int by) {
        var real = realTabs();
        if (real.isEmpty()) return;
        var at = Math.max(0, real.indexOf(selected()));
        list.getSelectionModel().select(real.get(Math.floorMod(at + by, real.size())));
    }

    /** Alt or Ctrl+number: the i-th real tab (separators are not counted, as their rows show no number). */
    void select(int i) {
        var real = realTabs();
        if (i >= 0 && i < real.size()) list.getSelectionModel().select(real.get(i));
    }

    List<TerminalTab> realTabs() {
        return tabs.stream().filter(t -> !t.separator).toList();
    }

    /** The real tab at this place or the nearest one, below first (null when there is none). */
    TerminalTab nearestReal(int i) {
        for (var d = 0; d < tabs.size(); d++) {
            if (i + d < tabs.size() && i + d >= 0 && !tabs.get(i + d).separator) return tabs.get(i + d);
            if (i - d >= 0 && i - d < tabs.size() && !tabs.get(i - d).separator) return tabs.get(i - d);
        }
        return null;
    }

    /** A separator, labelled by the user, placed above the given tab (or at the end). */
    void insertSeparator(TerminalTab above) {
        var d = new TextInputDialog("");
        d.initOwner(stage);
        d.setTitle("Separator");
        d.setHeaderText("Label for the separator (for example: work, code, asleep)");
        d.showAndWait().filter(s -> !s.isBlank()).ifPresent(label -> {
            var sep = TerminalTab.separator(options, theme, label.strip());
            var i = above == null ? tabs.size() : tabs.indexOf(above);
            tabs.add(Math.max(0, i), sep);
            sep.owner = this;
            refresh();
        });
        focusSoon();
    }

    /** Shows a tab. A new tab starts here; a sleeping one shows what waking it would run and waits to be asked
     *  (see wake), so a restored session starts only for a tab the user actually wants back. */
    void show(TerminalTab t) {
        if (t == null || moving) return;
        if (t.sleeping == null || options.wakeAll) start(t);
        content.getChildren().setAll(t.host);
        paint();
        refresh();
        Platform.runLater(t::focus);
    }

    /** Puts a tab at another place in the list (drag and drop, Ctrl+Shift+PgUp/PgDn); the shown tab stays shown. */
    void move(TerminalTab t, int to) {
        var shown = selected();
        moving = true;
        try {
            tabs.remove(t);
            tabs.add(Math.clamp(to, 0, tabs.size()), t);
            list.getSelectionModel().select(shown);
        } finally {
            moving = false;
        }
        refresh();
        focusSoon();
    }

    void focusSoon() {
        Platform.runLater(() -> {
            if (historyShown()) return;
            var t = selected();
            if (t != null) t.focus();
        });
    }

    HistoryPane historyPane;

    boolean historyShown() {
        return historyPane != null && content.getChildren().contains(historyPane);
    }

    /** The History view in place of the terminal; choosing a tab, Esc, or the button again goes back. */
    void showHistory() {
        if (historyPane == null) historyPane = new HistoryPane(this, app.history);
        content.getChildren().setAll(historyPane);
        historyPane.open();
    }

    void hideHistory() {
        var t = selected();
        if (t != null) show(t);
        else content.getChildren().clear();
    }

    /** A history entry opened again: a new tab, started at once (claude --resume with its flags, or its shell). */
    void resumeFromHistory(SavedTab s) {
        var t = app.wire(new TerminalTab(options, theme, s.command(), s.cwd));
        t.custom.set(s.customTitle);
        if (s.claude()) t.lastClaude = s;
        list.getSelectionModel().select(adopt(t));
        show(t);
    }

    static boolean inside(javafx.scene.Node n, javafx.scene.Node parent) {
        for (var p = n; p != null; p = p.getParent()) if (p == parent) return true;
        return false;
    }

    /** The small bar a selection brings up, beside where the mouse let go: Copy, Quote & reply (into the compose
     *  box), Quote in notepadfx. A click elsewhere or any key closes it. */
    javafx.stage.Popup selectionBar;

    void selectionPopup(TerminalTab t, double x, double y) {
        hideSelectionPopup();
        var panel = t.widget.getTerminalPanel();
        var sel = panel.getSelection();
        if (sel == null) return;
        var buffer = panel.getTerminalTextBuffer();
        var copy = Footer.button("Copy", theme.colors.get(4), "Copy the selection", () -> {
            var c = new ClipboardContent();
            c.putString(Quote.plain(buffer, sel));
            Clipboard.getSystemClipboard().setContent(c);
            done(panel);
        });
        var here = Footer.button("Quote & reply", "#4a4a4a", "Put it in the compose box as a quote, to answer point by point with >>", () -> {
            var md = Quote.markdown(buffer, sel) + "\n\n";
            if (!bottom.getChildren().contains(composer)) toggleCompose();
            var area = composer.text;
            area.insertText(area.getCaretPosition(), md);
            done(panel);
            Platform.runLater(area::requestFocus);
        });
        var outside = Footer.button("Quote in notepadfx", "#4a4a4a", "Write it to a reply file (beside the tab's PRP, or a temp file) and open it in notepadfx", () -> {
            var md = Quote.markdown(buffer, sel) + "\n\n";
            done(panel);
            composer.editOutside(t, md);
        });
        var row = new HBox(6, copy, here, outside);
        row.setPadding(new javafx.geometry.Insets(5));
        row.setStyle("-fx-background-color: " + Ui.shade(theme.background, 14) + "; -fx-background-radius: 6;"
                + " -fx-effect: dropshadow(gaussian, rgba(0,0,0,0.5), 8, 0.2, 0, 2);");
        selectionBar = new javafx.stage.Popup();
        selectionBar.getContent().add(row);
        selectionBar.setAutoHide(true);
        selectionBar.setConsumeAutoHidingEvents(false);
        selectionBar.show(stage, x + 8, y + 14);
    }

    void done(com.techsenger.jeditermfx.ui.TerminalPanel panel) {
        hideSelectionPopup();
        panel.clearSelection();
    }

    void hideSelectionPopup() {
        if (selectionBar != null) selectionBar.hide();
        selectionBar = null;
    }

    /** Opens the compose box under the terminal (pushing it up) and puts the cursor in it; closes it again. */
    void toggleCompose() {
        if (bottom.getChildren().contains(composer)) {
            bottom.getChildren().remove(composer);
            focusSoon();
        } else {
            bottom.getChildren().addFirst(composer);
            Platform.runLater(composer.text::requestFocus);
        }
    }

    /** Everything the shown tab shows and holds, as text (its saved text when asleep). */
    void copyText() {
        var t = selected();
        if (t == null) return;
        var text = t.live() ? t.screenText() : t.sleeping == null ? null : t.sleeping.screen;
        if (text != null) copy(text);
    }

    void paste() {
        var t = selected();
        if (t != null && t.live()) t.widget.getTerminalPanel().handlePaste();
        focusSoon();
    }

    void find() {
        var t = selected();
        if (t != null && t.live()) t.widget.showFindComponent();
    }

    void copy(String text) {
        app.copy(text);
    }

    void copyCommand(TerminalTab t) {
        app.copyCommand(t);
    }

    /** A new tab with a fresh shell in the same folder. */
    void duplicate(TerminalTab t) {
        var copy = app.wire(new TerminalTab(options, theme, ClaudeProbe.tokens(options.shell), t.where()));
        tabs.add(tabs.indexOf(t) + 1, copy);
        copy.owner = this;
        list.getSelectionModel().select(copy);
    }

    /** Puts one live tab to sleep now, as RAM pressure would: saved (claude session and flags, its text), process ended. */
    void hibernate(TerminalTab t) {
        Thread.ofPlatform().daemon().start(() -> {
            var saved = t.snapshot(ClaudeProbe.commandLines());
            Platform.runLater(() -> {
                if (saved == null || !t.live()) return;
                app.history.record(saved, "hibernated");
                t.sleep(saved);
                if (t.owner != null && t == t.owner.selected()) t.owner.show(t);
                app.refreshAll();
            });
        });
    }

    /** Closes the given tabs (close, close above/below/others), asking first if any of them has claude mid-reply. */
    void closeTabs(List<TerminalTab> which) {
        if (which.isEmpty()) return;
        if (!TabsApp.okToEnd(stage, which, "Closing ends them mid-reply (the conversation stays on disk and can be resumed).")) return;
        which.forEach(this::close);
    }

    /** "Restore all": starts every sleeping tab (claude sessions resume with their flags); the shown tab stays shown. */
    void wakeAll() {
        for (var t : List.copyOf(tabs)) if (t.sleeping != null) start(t);
        var shown = selected();
        if (shown != null) {
            content.getChildren().setAll(shown.host);
            paint();
        }
        refresh();
        focusSoon();
    }

    /** The user asked a sleeping tab to restore. */
    void wake(TerminalTab t) {
        start(t);
        if (t == selected()) show(t);
    }

    void start(TerminalTab t) {
        try {
            t.start();
        } catch (IOException ex) {
            System.err.println("fxterm-tabs: cannot start tab: " + ex);
        }
    }

    /** Window title, side list and footer, after a title, RAM or selection change. */
    void refresh() {
        var t = selected();
        stage.setTitle(t == null ? "Tabs" : "Tabs - " + t.shown() + " (" + t.where() + ")");
        list.refresh();
        // the terminal's border and the divider take the active tab's colour, so tab and terminal read as one
        var frame = Ui.frame(theme, t == null ? null : t.color.get());
        content.setStyle("-fx-border-color: " + frame + "; -fx-border-width: " + Ui.FRAME + " " + Ui.FRAME + " " + Ui.FRAME + " 0;"
                + " -fx-background-color: " + theme.background + ";");
        split.lookupAll(".split-pane-divider").forEach(d -> d.setStyle("-fx-background-color: " + frame + "; -fx-background-insets: 0; -fx-border-width: 0; -fx-padding: 0 "
                + (Ui.FRAME / 2.0) + " 0 " + (Ui.FRAME / 2.0) + ";"));
        footer.update(t, tabs, app.allTabs(), app.ram == null ? 0 : app.ram.selfBytes);
    }

    /** A row dragged onto row `row` of this window, before it or after it: the list order the user sees, with the
     *  dragged row's own place taken out first (rows below it move up by one). */
    void drop(TerminalTab t, int row, boolean after) {
        var at = row + (after ? 1 : 0);
        if (tabs.indexOf(t) < at) at--;
        move(t, at);
    }

    /** A new tab right after the shown one, so it lands in the same group (under the same separator). */
    void add() {
        var t = app.wire(TerminalTab.fresh(options, theme));
        var at = selected() == null ? tabs.size() : tabs.indexOf(selected()) + 1;
        tabs.add(at, t);
        t.owner = this;
        list.getSelectionModel().select(t);
    }

    void close(TerminalTab t) {
        if (t != null) remove(t, true);
    }

    /** Takes a tab out to move it to another window: its process keeps running. */
    void take(TerminalTab t) {
        remove(t, false);
    }

    /** Out of the list; ended or not. The shown tab's place goes to the one that took it (or the one above, for the
     *  last), and it gets the keys; a window left empty vanishes, and the last window quits. */
    void remove(TerminalTab t, boolean end) {
        var i = tabs.indexOf(t);
        if (i < 0) return;
        var wasSelected = t == selected();
        tabs.remove(t);
        if (end) {
            app.history.record(t.quickSave(), "closed");
            t.close();
        } else {
            t.painting(false);
        }
        if (realTabs().isEmpty()) {
            app.emptied(this);
            return;
        }
        if (wasSelected) list.getSelectionModel().clearAndSelect(tabs.indexOf(nearestReal(Math.min(i, tabs.size() - 1))));
        var now = selected();
        if (now != null) Platform.runLater(now::focus);
    }

    TerminalTab selected() {
        return list.getSelectionModel().getSelectedItem();
    }

    List<TerminalTab> hidden() {
        return tabs.stream().filter(t -> t != selected() && t.live()).toList();
    }

    void paint() {
        var shown = !stage.isIconified();
        tabs.forEach(t -> t.painting(shown && t == selected()));
    }
}
