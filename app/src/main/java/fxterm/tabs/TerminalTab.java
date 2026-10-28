package fxterm.tabs;

import module java.base;
import javafx.beans.property.*;
import javafx.geometry.*;
import javafx.scene.Node;
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
    List<String> argv;
    String cwd;
    JediTermFxWidget widget;
    PtyConnector connector;
    SavedTab sleeping;
    Consumer<TerminalTab> onExit = t -> { };
    Runnable wake = () -> { };
    /** A separator row: a label that groups the tabs under it; it runs nothing and cannot be selected. */
    boolean separator;
    /** Restored from a tab that was the shown one when Tabs closed. */
    boolean wasSelected;
    /** The window holding it now; changes when the tab is moved to another window. */
    TabsWindow owner;
    Button wakeButton;
    /** The last claude this tab ran, as it would be resumed; kept after that claude exits. */
    volatile SavedTab lastClaude;
    volatile long lastClaudePid;
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

    /** A separator row with this label. */
    static TerminalTab separator(Options o, TabbyTheme theme, String label) {
        var t = new TerminalTab(o, theme, List.of(), null);
        t.separator = true;
        t.title.set(label);
        t.custom.set(label);
        return t;
    }

    static TerminalTab restored(Options o, TabbyTheme theme, SavedTab s) {
        if (s.separator) return separator(o, theme, s.customTitle != null ? s.customTitle : s.title);
        var restoredSelected = s.selected;
        var t = new TerminalTab(o, theme, s.command(), s.cwd);
        t.custom.set(s.customTitle);
        t.color.set(s.color);
        t.sleep(s);
        t.wasSelected = restoredSelected;
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
        var text = screenText();
        if (text != null) s.screen = text;
        return s;
    }

    /** What the tab shows and holds now, as plain text, so it can be shown while asleep and searched in history. */
    String screenText() {
        var w = widget;
        if (w == null) return null;
        var b = w.getTerminalTextBuffer();
        b.lock();
        String all;
        try {
            var history = b.getHistoryBuffer().getLines();
            all = (history.isEmpty() ? "" : history + "\n") + b.getScreenLines();
        } finally {
            b.unlock();
        }
        var lines = new ArrayList<String>();
        for (var line : all.split("\n", -1)) lines.add(line.stripTrailing());
        while (!lines.isEmpty() && lines.getLast().isEmpty()) lines.removeLast();
        var from = Math.max(0, lines.size() - SavedTab.SCREEN_LINES);
        return String.join("\n", lines.subList(from, lines.size()));
    }

    void start() throws IOException {
        if (widget != null || separator) return;
        widget = new JediTermFxWidget(120, 30, new Theme(theme, o));
        connector = new PtyConnector(argv, cwd, 120, 30);
        widget.setTtyConnector(connector);
        widget.getTerminalPanel().setTitleListener(t -> { if (!t.isBlank()) title.set(t); });
        widget.start();
        var started = connector;
        // the program ended by itself (exit in cmd): the tab goes, as in other terminals. An exit fxterm-tabs caused
        // (close, sleep) has already replaced the connector, so it is not mistaken for this.
        started.process.onExit().thenRun(() -> javafx.application.Platform.runLater(() -> { if (connector == started) onExit.accept(this); }));
        host.getChildren().setAll(widget.getPane());
        wakeButton = null;
        // a woken claude tab knows its session from the start, so it is never saved without it (see snapshot)
        if (sleeping != null && sleeping.claude() && lastClaude == null) lastClaude = sleeping;
        sleeping = null;
        asleep.set(false);
    }

    /** What to save for this tab; null when it is a plain shell with nothing worth restoring beyond a new shell. */
    SavedTab snapshot(Map<Long, String> commandLines) {
        if (separator) {
            var s = new SavedTab();
            s.separator = true;
            s.title = shown();
            s.customTitle = shown();
            return s;
        }
        if (sleeping != null) return decorate(sleeping);
        if (connector == null) return null;
        return decorate(ClaudeProbe.of(connector.process.pid()).map(c -> c.withArgs(commandLines).saved(title.get())).orElseGet(() -> {
            var s = new SavedTab();
            s.title = title.get();
            s.cwd = connector.promptCwd != null ? connector.promptCwd : cwd;
            s.shell = SavedTab.shellOf(argv);
            s.lastClaude = lastClaude;
            if (lastClaudePid != 0 && ProcessHandle.of(lastClaudePid).map(ProcessHandle::isAlive).orElse(false))
                System.err.println("fxterm-tabs: claude " + lastClaudePid + " is running but was not found under tab '" + shown()
                        + "'; saved as a shell with its session offered (F3)");
            return s;
        }));
    }

    /** Its saved form at the moment it closes, for history: no WMI call (that takes a second), so a live claude's flags
     *  come from what RamWatch noted when that claude started. */
    SavedTab quickSave() {
        if (separator) return null;
        if (sleeping != null) return decorate(sleeping);
        if (connector == null) return null;
        var probe = ClaudeProbe.of(connector.process.pid());
        SavedTab s;
        if (probe.isPresent()) {
            var c = probe.get();
            s = c.saved(title.get());
            var known = lastClaude;
            if (known != null && c.sessionId.equals(known.claudeSessionId)) s.claudeArgs = new ArrayList<>(known.claudeArgs);
        } else {
            s = new SavedTab();
            s.title = title.get();
            s.cwd = connector.promptCwd != null ? connector.promptCwd : cwd;
            s.shell = SavedTab.shellOf(argv);
        }
        return decorate(s);
    }

    /** Ends the process and frees the widget; keeps the saved form so the tab can start again. In its place the tab
     *  shows what waking it would start, and waits to be asked: Enter or F2, or the button. */
    void sleep(SavedTab s) {
        close();
        sleeping = s;
        title.set(s.title);
        asleep.set(true);
        // a bar saying what waking runs, with the buttons; under it the tab as it looked, dimmed, selectable to copy
        var fg = "-fx-text-fill: " + theme.foreground + ";";
        var mono = " -fx-font-family: '" + o.family + "';";
        var moon = new javafx.scene.image.ImageView(TabCell.SLEEPING);
        moon.setFitWidth(22);
        moon.setFitHeight(22);
        var head = new Label("Asleep" + (s.claude() ? ": claude session " + s.claudeSessionId : ""));
        head.setStyle(fg + " -fx-font-size: 14; -fx-font-weight: bold;");
        var what = new Label(s.commandLine() + "   in   " + (s.cwd != null ? s.cwd : System.getProperty("user.home")));
        what.setStyle(fg + " -fx-opacity: 0.75; -fx-font-size: 12;" + mono);
        what.setWrapText(true);
        var restore = new Button("Restore (Enter or F2)");
        restore.setStyle("-fx-font-size: 12.5; -fx-background-color: " + theme.colors.get(4) + "; -fx-text-fill: #ffffff; -fx-padding: 4 12 4 12;");
        restore.setOnAction(e -> wake.run());
        var buttons = new HBox(8, restore);
        var last = s.claude() ? null : s.lastClaude;
        Runnable resumeLast = () -> { };
        if (last != null) {
            // the shell had run claude and it was ended before fxterm-tabs closed: offer that session too
            var resume = new Button("Resume its last claude" + (last.title != null ? " (" + last.title + ")" : "") + " (F3)");
            resume.setStyle("-fx-font-size: 12.5; -fx-padding: 4 12 4 12;");
            resume.setTooltip(new Tooltip(last.commandLine() + "\nin " + last.cwd));
            resumeLast = () -> {
                lastClaude = last;
                argv = last.command();
                cwd = last.cwd;
                wake.run();
            };
            var go = resumeLast;
            resume.setOnAction(e -> go.run());
            buttons.getChildren().add(resume);
        }
        var f3 = resumeLast;
        var titleRow = new HBox(8, moon, head);
        titleRow.setAlignment(Pos.CENTER_LEFT);
        var bar = new VBox(6, titleRow, what, buttons);
        bar.setPadding(new Insets(10, 14, 10, 14));
        bar.setStyle("-fx-background-color: " + Ui.shade(theme.background, 10) + ";");
        Node shot;
        if (s.screen != null && !s.screen.isBlank()) {
            var text = new TextArea(s.screen);
            text.setEditable(false);
            text.setFocusTraversable(false);
            text.setStyle("-fx-control-inner-background: " + theme.background + "; -fx-background-color: " + theme.background + ";"
                    + " -fx-text-fill: " + theme.foreground + "; -fx-opacity: 0.7; -fx-background-insets: 0; -fx-focus-color: transparent;"
                    + " -fx-faint-focus-color: transparent; -fx-font-size: " + (o.font > 0 ? o.font : theme.fontSize) + ";" + mono);
            // the end of the text is what the screen showed
            text.sceneProperty().addListener((ov, was, now) -> { if (now != null) javafx.application.Platform.runLater(() -> text.setScrollTop(Double.MAX_VALUE)); });
            shot = text;
        } else {
            var none = new Label("No saved screen for this tab (it was saved before screens were kept).");
            none.setStyle(fg + " -fx-opacity: 0.5; -fx-font-size: 12;");
            none.setPadding(new Insets(14));
            shot = none;
        }
        VBox.setVgrow(shot, Priority.ALWAYS);
        var box = new VBox(0, bar, shot);
        box.setAlignment(Pos.TOP_LEFT);
        // a keystroke typed into the previous tab just before switching must not wake this one
        var armedAt = new long[] {Long.MAX_VALUE};
        box.sceneProperty().addListener((ov, was, now) -> { if (now != null) armedAt[0] = System.currentTimeMillis() + 700; });
        box.addEventFilter(KeyEvent.KEY_PRESSED, e -> {
            var k = e.getCode();
            if (k != KeyCode.ENTER && k != KeyCode.F2 && k != KeyCode.F3 && k != KeyCode.SPACE) return;
            e.consume();
            if (k == KeyCode.SPACE || System.currentTimeMillis() < armedAt[0]) return;
            if (k == KeyCode.F3) f3.run(); else wake.run();
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
