package tabbyfx;

import module java.base;
import javafx.beans.property.*;
import javafx.geometry.*;
import javafx.scene.control.*;
import javafx.scene.input.*;
import javafx.scene.layout.*;
import com.techsenger.jeditermfx.ui.JediTermFxWidget;

/** One tab. Either live (a widget and a shell) or asleep (only what is needed to start it again: no process, no
 *  widget, no screen buffer). A sleeping tab wakes when it is shown. */
public final class TerminalTab {

    final StringProperty title = new SimpleStringProperty("shell"), custom = new SimpleStringProperty(), color = new SimpleStringProperty();
    final BooleanProperty asleep = new SimpleBooleanProperty();
    final StackPane host = new StackPane();
    final Options o;
    final TabbyTheme theme;
    final List<String> argv;
    final String cwd;
    JediTermFxWidget widget;
    PtyConnector connector;
    SavedTab sleeping;
    Consumer<TerminalTab> onExit = t -> { };
    Runnable wake = () -> { };
    Button wakeButton;
    volatile long ramBytes;
    volatile String liveCwd;

    TerminalTab(Options o, TabbyTheme theme, List<String> argv, String cwd) {
        this.o = o;
        this.theme = theme;
        this.argv = argv;
        this.cwd = cwd;
        host.setStyle("-fx-background-color: " + theme.background + ";");
    }

    static TerminalTab fresh(Options o, TabbyTheme theme) {
        return new TerminalTab(o, theme, ClaudeProbe.tokens(o.shell), o.cwd);
    }

    static TerminalTab of(Options o, TabbyTheme theme, Request r) {
        return new TerminalTab(o, theme, r.command(o), r.cwd);
    }

    /** Where the tab is working: what claude reports, else what the shell's prompt last reported, else where the tab
     *  was started. */
    String where() {
        if (liveCwd != null) return liveCwd;
        var c = connector;
        if (c != null && c.promptCwd != null) return c.promptCwd;
        if (sleeping != null && sleeping.cwd != null) return sleeping.cwd;
        return cwd != null ? cwd : System.getProperty("user.home");
    }

    static TerminalTab restored(Options o, TabbyTheme theme, SavedTab s) {
        var t = new TerminalTab(o, theme, s.command(), s.cwd);
        t.custom.set(s.customTitle);
        t.color.set(s.color);
        t.sleep(s);
        return t;
    }

    /** The name the user gave the tab, else the title its program set. */
    String shown() {
        var c = custom.get();
        return c != null && !c.isBlank() ? c : title.get();
    }

    /** Adds what the user chose for this tab (name, colour) to its saved form. */
    SavedTab decorate(SavedTab s) {
        s.customTitle = custom.get();
        s.color = color.get();
        return s;
    }

    void start() throws IOException {
        if (widget != null) return;
        widget = new JediTermFxWidget(120, 30, new Theme(theme, o));
        connector = new PtyConnector(argv, cwd, 120, 30);
        widget.setTtyConnector(connector);
        widget.getTerminalPanel().setTitleListener(t -> { if (!t.isBlank()) title.set(t); });
        widget.start();
        var started = connector;
        // the program ended by itself (exit in cmd): the tab goes, as in other terminals. An exit tabbyfx caused
        // (close, sleep) has already replaced the connector, so it is not mistaken for this.
        started.process.onExit().thenRun(() -> javafx.application.Platform.runLater(() -> { if (connector == started) onExit.accept(this); }));
        host.getChildren().setAll(widget.getPane());
        wakeButton = null;
        sleeping = null;
        asleep.set(false);
    }

    /** What to save for this tab; null when it is a plain shell with nothing worth restoring beyond a new shell. */
    SavedTab snapshot(Map<Long, String> commandLines) {
        if (sleeping != null) return decorate(sleeping);
        if (connector == null) return null;
        return decorate(ClaudeProbe.of(connector.process.pid()).map(c -> c.withArgs(commandLines).saved(title.get())).orElseGet(() -> {
            var s = new SavedTab();
            s.title = title.get();
            s.cwd = connector.promptCwd != null ? connector.promptCwd : cwd;
            s.shell = SavedTab.shellOf(argv);
            return s;
        }));
    }

    /** Ends the process and frees the widget; keeps the saved form so the tab can start again. In its place the tab
     *  shows what waking it would start, and waits to be asked: Enter or F2, or the button. */
    void sleep(SavedTab s) {
        close();
        sleeping = s;
        title.set(s.title);
        asleep.set(true);
        var fg = "-fx-text-fill: " + theme.foreground + ";";
        var head = new Label("This tab is asleep" + (s.claude() ? ": claude session " + s.claudeSessionId : "") + ".");
        head.setStyle(fg + " -fx-font-size: 16;");
        var what = new Label("Restoring it runs:\n    " + s.commandLine() + "\nin:\n    " + (s.cwd != null ? s.cwd : System.getProperty("user.home")));
        what.setStyle(fg + " -fx-opacity: 0.8; -fx-font-size: 13; -fx-font-family: '" + o.family + "';");
        what.setWrapText(true);
        var restore = new Button("Restore this tab (Enter or F2)");
        restore.setStyle("-fx-font-size: 13; -fx-background-color: " + theme.colors.get(4) + "; -fx-text-fill: #ffffff; -fx-padding: 6 14 6 14;");
        restore.setOnAction(e -> wake.run());
        var later = new Label("Leaving it asleep costs nothing; another tab can be chosen instead.");
        later.setStyle(fg + " -fx-opacity: 0.5; -fx-font-size: 12;");
        var box = new VBox(14, head, what, restore, later);
        box.setPadding(new Insets(40));
        box.setMaxWidth(900);
        box.setAlignment(Pos.TOP_LEFT);
        // a keystroke typed into the previous tab just before switching must not wake this one
        var armedAt = new long[] {Long.MAX_VALUE};
        box.sceneProperty().addListener((ov, was, now) -> { if (now != null) armedAt[0] = System.currentTimeMillis() + 700; });
        box.addEventFilter(KeyEvent.KEY_PRESSED, e -> {
            if (e.getCode() != KeyCode.ENTER && e.getCode() != KeyCode.F2 && e.getCode() != KeyCode.SPACE) return;
            e.consume();
            if (e.getCode() != KeyCode.SPACE && System.currentTimeMillis() >= armedAt[0]) wake.run();
        });
        wakeButton = restore;
        StackPane.setAlignment(box, Pos.TOP_LEFT);
        host.getChildren().setAll(box);
    }

    boolean live() {
        return widget != null;
    }

    void focus() {
        if (widget == null) {
            if (wakeButton != null) wakeButton.requestFocus();
            return;
        }
        var n = widget.getPreferredFocusableNode();
        if (n != null) n.requestFocus();
    }

    void painting(boolean on) {
        if (widget != null) widget.getTerminalPanel().setPainting(on);
    }

    void close() {
        if (widget != null) {
            try { widget.close(); } catch (RuntimeException ignored) { }
        }
        if (connector != null) connector.close();
        widget = null;
        connector = null;
    }
}
