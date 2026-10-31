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
        soon.setOnFinished(e -> refresh());
        // a tab added or taken away here renumbers the windows after this one
        tabs.addListener((ListChangeListener<TerminalTab>) c -> app.windows.forEach(TabsWindow::refreshSoon));
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
        else if (ctrlShift && code == KeyCode.W) requestClose(selected());
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
        // tabs in a folded group are passed over, as a browser does
        var real = realTabs().stream().filter(t -> !folded(t) || t == selected()).toList();
        if (real.isEmpty()) return;
        var at = Math.max(0, real.indexOf(selected()));
        list.getSelectionModel().select(real.get(Math.floorMod(at + by, real.size())));
    }

    /** Alt or Ctrl+number: the tab with that number (separators are not counted, as their rows show no number).
     *  Numbers run on from one window to the next, so a window holds the numbers after the windows before it. */
    void select(int i) {
        var real = realTabs();
        var at = i - (firstNumber() - 1);
        if (at >= 0 && at < real.size()) list.getSelectionModel().select(real.get(at));
    }

    /** The number of this window's first tab: one more than the tabs in the windows before it (tabs are numbered
     *  straight on across windows; when there are too many, split them over windows). */
    int firstNumber() {
        var n = 1;
        for (var w : app.windows) {
            if (w == this) break;
            n += w.realTabs().size();
        }
        return n;
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
        // the words being read are lit in one tab's terminal: choosing another tab ends the reading
        if (app.reader.tab != null && app.reader.tab != t) app.reader.stop();
        // a tab chosen in a folded group (by its number, from another tool) opens its group
        if (folded(t)) {
            headOf(t).collapsed = false;
            Events.log("expand", "head", Events.tab(headOf(t)), "because", Events.tab(t));
        }
        if (t.sleeping == null || options.wakeAll) start(t);
        content.getChildren().setAll(t.host);
        paint();
        refresh();
        Platform.runLater(t::focus);
    }

    /** Puts a tab at another place in the list (drag and drop, Ctrl+Shift+PgUp/PgDn); the shown tab stays shown. */
    void move(TerminalTab t, int to) {
        var shown = selected();
        Events.log("move", "tab", Events.tab(t), "to", to);
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
        var read = Footer.button("Read aloud", theme.colors.get(3), "Read the selection aloud, lighting each word in the terminal as it is said", () -> {
            app.reader.start(t, buffer, sel);
            done(panel);
        });
        var here = Footer.button("Quote & reply", null, "Put it in the compose box as a quote, to answer point by point with >>", () -> {
            var md = Quote.markdown(buffer, sel) + "\n\n";
            if (!bottom.getChildren().contains(composer)) toggleCompose();
            composer.insert(md);
            done(panel);
            Platform.runLater(composer::focusEditor);
        });
        var outside = Footer.button("Quote in notepadfx", null, "Write it to a reply file (beside the tab's PRP, or a temp file) and open it in notepadfx, the original as it is, to answer under it with >>", () -> {
            var md = Quote.markdownAsIs(buffer, sel) + "\n\n";
            done(panel);
            composer.editOutside(t, md);
        });
        var row = new HBox(6, copy, read, here, outside);
        row.setPadding(new javafx.geometry.Insets(5));
        // a popup is a scene of its own: it needs the button rules too
        row.getStylesheets().add(Ui.stylesheet(theme));
        row.setStyle("-fx-background-color: " + Ui.shade(theme.background, -6) + "; -fx-background-radius: 6; -fx-border-color: "
                + Ui.shade(theme.background, 26) + "; -fx-border-radius: 6; -fx-effect: dropshadow(gaussian, rgba(0,0,0,0.5), 8, 0.2, 0, 2);");
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
            composer.ensure();
            bottom.getChildren().addFirst(composer);
            Platform.runLater(composer::focusEditor);
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
        if (t.hibernating || !t.live()) return;
        // the row says so at once: reading claude's flags is a slow call when the machine is busy, and a click that
        // shows nothing looks like a hang
        t.hibernating = true;
        app.refreshAll();
        Thread.ofPlatform().daemon().start(() -> {
            var saved = t.snapshot(ClaudeProbe.commandLines());
            Platform.runLater(() -> {
                t.hibernating = false;
                if (saved == null || !t.live()) { app.refreshAll(); return; }
                if (app.reader.tab == t) app.reader.stop();
                app.history.record(saved, "hibernated");
                t.sleep(saved);
                if (t.owner != null && t == t.owner.selected()) t.owner.show(t);
                app.refreshAll();
            });
        });
    }

    /** The user closing one tab (its x, Ctrl+Shift+W, the menu). A sleeping tab, or a shell with nothing running in
     *  it, just closes; when claude or any other program is running in it, it asks: close, hibernate or cancel. */
    void requestClose(TerminalTab t) {
        if (t == null) return;
        var running = running(t);
        if (running == null) {
            close(t);
            return;
        }
        var choice = Ui.closeTab(stage, t.shown(), running);
        Events.log("close-tab", "tab", Events.tab(t), "choice", choice == 0 ? "close" : choice == 1 ? "hibernate" : "cancel");
        if (choice == 0) close(t);
        else if (choice == 1) hibernate(t);
    }

    static final Set<String> SHELLS = Set.of("cmd.exe", "powershell.exe", "pwsh.exe", "bash.exe", "zsh.exe", "sh.exe", "wsl.exe"),
            HELPERS = Set.of("conhost.exe", "openconsole.exe", "winpty-agent.exe");

    /** What is running in a live tab beyond its own shell, as a sentence for the close question; null for a bare shell
     *  (or a tab asleep), which is closed without asking. */
    static String running(TerminalTab t) {
        if (t.separator || !t.live()) return null;
        var pid = t.connector.process.pid();
        var claude = ClaudeProbe.of(pid);
        if (claude.isPresent()) {
            var c = claude.get();
            return "Claude Code is running here" + (c.name != null ? " (" + c.name + ")" : "")
                    + (c.idle() ? ", waiting for you." : ", in the middle of a reply.");
        }
        var root = ProcessHandle.of(pid).orElse(null);
        if (root == null) return null;
        // the shell is the process itself, or the first shell below a console helper
        var shell = SHELLS.contains(name(root)) ? root : root.children().filter(p -> SHELLS.contains(name(p))).findFirst().orElse(root);
        var busy = shell.descendants().map(TabsWindow::name).filter(n -> !HELPERS.contains(n)).distinct().limit(3).toList();
        return busy.isEmpty() ? null : "Still running here: " + String.join(", ", busy) + ".";
    }

    static String name(ProcessHandle p) {
        return p.info().command().map(c -> Path.of(c).getFileName().toString().toLowerCase(Locale.ROOT)).orElse("a program");
    }

    /** Closes the given tabs (close, close above/below/others), asking first if any of them has claude mid-reply. */
    void closeTabs(List<TerminalTab> which) {
        if (which.isEmpty()) return;
        if (which.size() == 1 && !which.getFirst().separator) {
            requestClose(which.getFirst());
            return;
        }
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
        soon.stop();
        var t = selected();
        // without claude's animated symbol: its width changes every frame, and the rest of the title (in the title bar
        // and on the taskbar button) slid left and right with it
        stage.setTitle(t == null ? "Tabs" : "Tabs - " + TabCell.plain(t.shown()) + " (" + t.where() + ")");
        // the list is only laid out again when something it shows changed: a title that a program rewrites many
        // times a second (claude's spinner) used to redraw every row each time, and the sidebar shook under it
        var rows = rowsShown();
        if (!rows.equals(lastRows)) {
            lastRows = rows;
            // the list is as tall as the rows it shows (a folded group's tabs take none), so + sits right under the last
            sidebar.fit();
            list.refresh();
        }
        // the terminal's border and the divider take the active tab's colour, so tab and terminal read as one
        var frame = Ui.frame(theme, colourOf(t));
        if (!frame.equals(lastFrame)) {
            lastFrame = frame;
            content.setStyle("-fx-border-color: " + frame + "; -fx-border-width: " + Ui.FRAME + " " + Ui.FRAME + " " + Ui.FRAME + " 0;"
                    + " -fx-background-color: " + theme.background + ";");
            split.lookupAll(".split-pane-divider").forEach(d -> d.setStyle("-fx-background-color: " + frame + "; -fx-background-insets: 0; -fx-border-width: 0; -fx-padding: 0 "
                    + (Ui.FRAME / 2.0) + " 0 " + (Ui.FRAME / 2.0) + ";"));
        }
        footer.update(t, tabs, app.allTabs(), app.ram == null ? 0 : app.ram.selfBytes);
    }

    /** What the tab list shows, as one string: when it is the same as last time the list need not be redrawn. */
    String rowsShown() {
        var s = new StringBuilder().append(firstNumber()).append('\n');
        for (var x : tabs) {
            s.append(x.separator ? 'S' : 'T').append(x.separator ? x.shown() : "").append('\u0001').append(x.where()).append('\u0001').append(x.live())
                    .append('\u0001').append(x.live() ? x.ramBytes >> 20 : 0).append('\u0001').append(x.color.get()).append('\u0001')
                    .append(x.collapsed).append(x.hibernating).append('\n');
        }
        return s.toString();
    }

    String lastRows, lastFrame;
    /** A title or RAM change asks for a refresh through this: many changes a second become a few redraws. */
    final javafx.animation.PauseTransition soon = new javafx.animation.PauseTransition(javafx.util.Duration.millis(300));

    void refreshSoon() {
        if (soon.getStatus() != javafx.animation.Animation.Status.RUNNING) soon.playFromStart();
    }

    /** A row dragged onto row `row` of this window, before it or after it: the list order the user sees, with the
     *  dragged row's own place taken out first (rows below it move up by one). */
    void drop(TerminalTab t, int row, boolean after) {
        var at = row + (after ? 1 : 0);
        // below a folded group's label means after the whole group, not into its hidden tabs
        if (after && row >= 0 && row < tabs.size() && tabs.get(row).separator && tabs.get(row).collapsed)
            at = row + group(tabs.get(row)).size();
        if (t.separator) {
            moveGroup(t, at);
            return;
        }
        if (tabs.indexOf(t) < at) at--;
        move(t, at);
    }

    /** The separator heading the group a tab is in, or null for a tab above every separator. */
    TerminalTab headOf(TerminalTab t) {
        for (var i = tabs.indexOf(t) - 1; i >= 0; i--) if (tabs.get(i).separator) return tabs.get(i);
        return null;
    }

    /** True for a tab whose group is folded away. */
    boolean folded(TerminalTab t) {
        if (t.separator) return false;
        var head = headOf(t);
        return head != null && head.collapsed;
    }

    /** Folds or unfolds a group. Folding the group of the shown tab shows the nearest tab still in sight. */
    void toggleGroup(TerminalTab head) {
        head.collapsed = !head.collapsed;
        Events.log(head.collapsed ? "collapse" : "expand", "head", Events.tab(head), "size", group(head).size() - 1);
        var shown = selected();
        if (head.collapsed && shown != null && folded(shown)) {
            var i = tabs.indexOf(head);
            TerminalTab next = null;
            for (var d = 1; d < tabs.size() && next == null; d++) {
                for (var j : new int[] {i + d, i - d})
                    if (j >= 0 && j < tabs.size() && !tabs.get(j).separator && !folded(tabs.get(j))) { next = tabs.get(j); break; }
            }
            if (next != null) list.getSelectionModel().select(next);
        }
        refresh();
        focusSoon();
    }

    /** Closing a group asks: remove only the label (its tabs stay, joining the group above), or close the group with
     *  all its tabs, or cancel. An empty group just goes. */
    void closeGroup(TerminalTab head) {
        var inside = group(head).subList(1, group(head).size());
        var choice = inside.isEmpty() ? 0 : Ui.closeGroup(stage, head.shown(), inside.size());
        Events.log("close-group", "head", Events.tab(head), "size", inside.size(), "choice", choice == 0 ? "label" : choice == 1 ? "all" : "cancel");
        if (choice < 0) return;
        if (choice == 1) {
            if (!TabsApp.okToEnd(stage, inside, "Closing the group ends its " + inside.size() + " tabs (they stay in History).")) return;
            List.copyOf(inside).forEach(this::close);
        }
        tabs.remove(head);
        refresh();
        focusSoon();
    }

    /** The colour a row shows: its own, else for a tab the colour of the label it sits under (null for none). */
    String colourOf(TerminalTab t) {
        if (t == null) return null;
        var own = t.color.get();
        if (own != null || t.separator) return own;
        var head = headOf(t);
        return head == null ? null : head.color.get();
    }

    /** Colours the label and every tab under it alike: the tabs' own colours go, so they follow the label. */
    void colourGroup(TerminalTab head, String hex) {
        for (var x : group(head)) x.color.set(x == head ? hex : null);
        refresh();
    }

    /** A separator is the head of a group: it and the tabs under it, down to the next separator. */
    List<TerminalTab> group(TerminalTab head) {
        var from = tabs.indexOf(head);
        var to = from + 1;
        while (to < tabs.size() && !tabs.get(to).separator) to++;
        return List.copyOf(tabs.subList(from, to));
    }

    /** Moves a whole group, as one block, to before list row `at` (counted before the move). A drop inside the group
     *  itself changes nothing; a drop in the middle of another group puts the block after that group's tab there,
     *  which splits it, so the block goes to the end of that group instead. */
    void moveGroup(TerminalTab head, int at) {
        var block = group(head);
        var from = tabs.indexOf(head);
        if (at >= from && at <= from + block.size()) return;
        // inside another group: to its nearer edge, before its label or after its last tab, as a browser does
        if (at > 0 && at < tabs.size() && !tabs.get(at).separator) {
            var start = at;
            while (start > 0 && !tabs.get(start).separator) start--;
            if (!tabs.get(start).separator) start = 0;
            var end = at;
            while (end < tabs.size() && !tabs.get(end).separator) end++;
            // the tabs above every label are not a group: a group cannot go in among them, only after them
            at = tabs.get(start).separator && at - start <= end - at ? start : end;
            if (at >= from && at <= from + block.size()) return;
        }
        if (at > from) at -= block.size();
        var shown = selected();
        Events.log("move-group", "head", Events.tab(head), "size", block.size(), "to", at);
        moving = true;
        try {
            tabs.removeAll(block);
            tabs.addAll(Math.clamp(at, 0, tabs.size()), block);
            list.getSelectionModel().select(shown);
        } finally {
            moving = false;
        }
        refresh();
        focusSoon();
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
            if (app.reader.tab == t) app.reader.stop();
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
