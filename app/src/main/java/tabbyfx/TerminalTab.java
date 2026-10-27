package tabbyfx;

import module java.base;
import javafx.beans.property.*;
import javafx.scene.control.Label;
import javafx.scene.layout.StackPane;
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

    /** Where the tab is working: what claude reports, else where the tab was started. */
    String where() {
        if (liveCwd != null) return liveCwd;
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
            s.cwd = cwd;
            return s;
        }));
    }

    /** Ends the process and frees the widget; keeps the saved form so the tab can start again. */
    void sleep(SavedTab s) {
        close();
        sleeping = s;
        title.set(s.title);
        asleep.set(true);
        var label = new Label("asleep - shown again, it resumes" + (s.claude() ? " claude session " + s.claudeSessionId : " a new shell"));
        label.setStyle("-fx-text-fill: " + theme.foreground + "; -fx-opacity: 0.6; -fx-font-size: 14;");
        host.getChildren().setAll(label);
    }

    boolean live() {
        return widget != null;
    }

    void focus() {
        if (widget == null) return;
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
