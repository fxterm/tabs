package tabbyfx;

import module java.base;
import javafx.application.*;
import javafx.collections.*;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.input.*;
import javafx.scene.layout.*;
import javafx.stage.*;

/** Tabs listed down the left, as Tabby shows them; only the selected tab is attached and painting.
 *  Keys (see keys()): Ctrl+Shift+T new, Ctrl+Shift+W close, Ctrl+Tab / Ctrl+Shift+Tab, Alt+1..9, F11 full screen. */
public final class TabbyApp extends Application {

    static Request request;

    final ObservableList<TerminalTab> tabs = FXCollections.observableArrayList();
    final StackPane content = new StackPane();
    final SplitPane split = new SplitPane();
    ListView<TerminalTab> list;
    Sidebar sidebar;
    WindowState window;
    Options options;
    TabbyTheme theme;
    SessionStore store;
    Footer footer;
    RamWatch ram;
    Stage stage;

    @Override
    public void start(Stage stage) throws Exception {
        this.stage = stage;
        options = Options.parse(getParameters().getRaw());
        theme = TabbyTheme.load(options.theme);
        store = new SessionStore(options);
        footer = new Footer(theme);
        window = options.offscreen || options.state != null ? new WindowState() : WindowState.load();
        sidebar = new Sidebar(this, tabs, theme);
        list = sidebar.list;
        list.getSelectionModel().selectedItemProperty().addListener((ov, was, now) -> show(now));
        stage.iconifiedProperty().addListener((ov, was, now) -> paint());

        (options.restore ? store.load() : List.<SavedTab>of()).forEach(s -> adopt(TerminalTab.restored(options, theme, s)));
        var asked = request != null && !"show".equals(request.kind) ? adopt(TerminalTab.of(options, theme, request)) : null;
        if (tabs.isEmpty()) for (var i = 0; i < options.tabs; i++) adopt(TerminalTab.fresh(options, theme));

        var scene = new Scene(layout(), window.width, window.height);
        scene.getStylesheets().add(Ui.stylesheet(theme));
        // JavaFX sets the title bar dark or light from this itself, overriding any DWM call made without it
        scene.getPreferences().setColorScheme(theme.dark() ? javafx.application.ColorScheme.DARK : javafx.application.ColorScheme.LIGHT);
        scene.addEventFilter(KeyEvent.KEY_PRESSED, this::keys);
        scene.addEventFilter(KeyEvent.ANY, e -> IdleTrim.activity());
        stage.setScene(scene);
        stage.getIcons().addAll(new Image(getClass().getResourceAsStream("icon-256.png")), new Image(getClass().getResourceAsStream("icon-32.png")));
        stage.setOnCloseRequest(e -> { e.consume(); quit(); });
        if (options.offscreen) {
            stage.initStyle(StageStyle.UTILITY);
            stage.setX(-5000);
            stage.setY(0);
        } else {
            window.apply(stage);
        }
        if (!options.offscreen && options.state == null || options.serve) Instance.serve(r -> Platform.runLater(() -> handle(r)));
        if (options.wakeAll) for (var i = tabs.size() - 1; i >= 0; i--) list.getSelectionModel().select(i);
        if (asked != null) list.getSelectionModel().select(asked); else list.getSelectionModel().select(0);
        stage.show();
        TitleBar.paintSoon(theme);
        split.setDividerPositions(window.sidebar / stage.getWidth());
        IdleTrim.start();
        new Hibernator(this::hidden, options).start();
        ram = new RamWatch(() -> tabs, this::refresh);
        ram.start();
        TestHooks.start(this, scene, options);
    }

    /** The tab list and the terminal side by side with a draggable divider; the list keeps its width when the window
     *  is resized. */
    BorderPane layout() {
        split.getItems().setAll(sidebar.node, content);
        SplitPane.setResizableWithParent(sidebar.node, false);
        split.setStyle("-fx-background-color: " + Ui.shade(theme.background, -10) + "; -fx-padding: 0; -fx-box-border: transparent;");
        var root = new BorderPane(split);
        root.setBottom(footer);
        return root;
    }

    double sidebarWidth() {
        return sidebar.node.getWidth();
    }

    /** Another launch asked for something (Tabby's `open` / `run`, or just to be shown). */
    void handle(Request r) {
        if (!"show".equals(r.kind)) list.getSelectionModel().select(adopt(TerminalTab.of(options, theme, r)));
        if (stage.isIconified()) stage.setIconified(false);
        stage.toFront();
    }

    TerminalTab adopt(TerminalTab t) {
        t.onExit = this::exited;
        t.wake = () -> wake(t);
        t.title.addListener((ov, was, now) -> refresh());
        tabs.add(t);
        return t;
    }

    /** The program in a tab ended by itself: the tab closes; the last one closes the window. */
    void exited(TerminalTab t) {
        close(t);
    }

    void keys(KeyEvent e) {
        var ctrlShift = e.isControlDown() && e.isShiftDown() && !e.isAltDown();
        var alt = e.isAltDown() && !e.isControlDown() && !e.isShiftDown();
        var code = e.getCode();
        if (ctrlShift && code == KeyCode.T) add();
        else if (ctrlShift && code == KeyCode.W) close(selected());
        else if (e.isControlDown() && code == KeyCode.TAB) step(e.isShiftDown() ? -1 : 1);
        else if (alt && code.isDigitKey() && code != KeyCode.DIGIT0) select(code.getCode() - KeyCode.DIGIT1.getCode());
        else if (code == KeyCode.F11 && !e.isControlDown() && !e.isAltDown() && !e.isShiftDown()) stage.setFullScreen(!stage.isFullScreen());
        else return;
        e.consume();
    }

    void step(int by) {
        if (!tabs.isEmpty()) list.getSelectionModel().select(Math.floorMod(list.getSelectionModel().getSelectedIndex() + by, tabs.size()));
    }

    void select(int i) {
        if (i >= 0 && i < tabs.size()) list.getSelectionModel().select(i);
    }

    /** Shows a tab. A new tab starts here; a sleeping one shows what waking it would run and waits to be asked
     *  (see wake), so a restored session starts only for a tab the user actually wants back. */
    void show(TerminalTab t) {
        if (t == null) return;
        if (t.sleeping == null || options.wakeAll) start(t);
        content.getChildren().setAll(t.host);
        paint();
        refresh();
        Platform.runLater(t::focus);
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
            System.err.println("tabbyfx: cannot start tab: " + ex);
        }
    }

    /** Window title, side list and footer, after a title, RAM or selection change. */
    void refresh() {
        var t = selected();
        stage.setTitle(t == null ? "tabbyfx" : "tabbyfx - " + t.shown() + " (" + t.where() + ")");
        list.refresh();
        footer.update(t, tabs, ram == null ? 0 : ram.selfBytes);
    }

    void add() {
        list.getSelectionModel().select(adopt(TerminalTab.fresh(options, theme)));
    }

    void close(TerminalTab t) {
        if (t == null) return;
        var i = tabs.indexOf(t);
        var wasSelected = t == selected();
        tabs.remove(t);
        t.close();
        if (tabs.isEmpty()) { quit(); return; }
        // closing the shown tab shows the one that took its place (or the one above, for the last), and types into it
        if (wasSelected) list.getSelectionModel().clearAndSelect(Math.min(i, tabs.size() - 1));
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

    /** Saves every tab (claude sessions with their id and flags), asks first if any claude is mid-turn, then ends all. */
    void quit() {
        Thread.ofPlatform().daemon().start(() -> {
            var lines = ClaudeProbe.commandLines();
            var busy = tabs.stream().filter(TerminalTab::live).flatMap(t -> ClaudeProbe.of(t.connector.process.pid()).stream())
                    .filter(c -> !c.idle()).map(c -> c.name != null ? c.name : c.sessionId).toList();
            var saved = tabs.stream().map(t -> t.snapshot(lines)).filter(Objects::nonNull).toList();
            Platform.runLater(() -> {
                if (!busy.isEmpty() && !Ui.confirm(stage, "Still working: " + String.join(", ", busy)
                        + ".\nClosing ends them mid-turn. They are saved and resume when their tab is opened again.")) return;
                store.save(saved);
                tabs.forEach(TerminalTab::close);
                if (!options.offscreen && options.state == null) WindowState.save(stage, sidebarWidth());
                if (!options.offscreen && options.state == null || options.serve) Instance.release();
                Platform.exit();
                System.exit(0);
            });
        });
    }
}
