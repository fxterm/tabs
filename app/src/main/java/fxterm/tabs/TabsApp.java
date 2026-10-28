package fxterm.tabs;

import module java.base;
import javafx.application.*;
import javafx.stage.*;

/** The process: one or more windows (TabsWindow) sharing the settings, the RAM watch and the hibernator. Closing
 *  saves every tab of every window, with the window it was in; the next start brings the windows back. */
public final class TabsApp extends Application {

    static Request request;

    final List<TabsWindow> windows = new ArrayList<>();
    Options options;
    TabbyTheme theme;
    SessionStore store;
    History history;
    RamWatch ram;
    /** Where a tab from another launch (Tabby's open / run) goes. */
    TabsWindow lastFocused;

    @Override
    public void start(Stage first) throws Exception {
        options = Options.parse(getParameters().getRaw());
        theme = TabbyTheme.load(options.theme);
        // a test run (off-screen or its own state file) neither reads nor writes the user's saved size
        FontSize.init(options.font > 0 ? options.font : theme.fontSize, !options.offscreen && options.state == null);
        store = new SessionStore(options);
        history = new History(options);
        var states = options.offscreen || options.state != null ? List.of(new WindowState()) : WindowState.loadAll();
        var saved = options.restore ? store.load() : List.<SavedTab>of();
        var count = Math.max(1, saved.stream().mapToInt(s -> s.window + 1).max().orElse(1));
        for (var i = 0; i < count; i++) windows.add(new TabsWindow(this, i == 0 ? first : new Stage(), state(states, i)));
        saved.forEach(s -> windows.get(Math.min(Math.max(s.window, 0), count - 1)).adopt(wire(TerminalTab.restored(options, theme, s))));
        var main = windows.getFirst();
        var asked = request != null && !"show".equals(request.kind) ? main.adopt(wire(TerminalTab.of(options, theme, request))) : null;
        if (main.realTabs().isEmpty()) for (var i = 0; i < options.tabs; i++) main.adopt(wire(TerminalTab.fresh(options, theme)));
        windows.removeIf(w -> w.realTabs().isEmpty());
        if (!options.offscreen && options.state == null || options.serve) Instance.serve(r -> Platform.runLater(() -> handle(r)));
        for (var i = 0; i < windows.size(); i++) {
            var w = windows.get(i);
            if (options.wakeAll) for (var t : w.realTabs().reversed()) w.list.getSelectionModel().select(t);
            if (asked != null && w == main) w.list.getSelectionModel().select(asked);
            // the tab that was shown when Tabs closed, else the first real tab
            else w.list.getSelectionModel().select(w.realTabs().stream().filter(t -> t.wasSelected).findFirst().orElse(w.realTabs().getFirst()));
            w.open(state(states, i));
        }
        lastFocused = main;
        IdleTrim.start();
        new Hibernator(this::hidden, options, s -> history.record(s, "hibernated")).start();
        ram = new RamWatch(this::allTabs, this::refreshAll);
        ram.start();
        TestHooks.start(main, main.scene, options);
    }

    /** A saved window's place, or one a little below and right of the window before it. */
    static WindowState state(List<WindowState> states, int i) {
        if (i < states.size()) return states.get(i);
        var s = new WindowState();
        var before = states.isEmpty() ? null : states.getLast();
        if (before != null && !Double.isNaN(before.x)) {
            s.x = before.x + 40 * (i - states.size() + 1);
            s.y = before.y + 40 * (i - states.size() + 1);
            s.width = before.width;
            s.height = before.height;
            s.sidebar = before.sidebar;
        }
        return s;
    }

    /** What every tab needs whichever window it is in: closing itself when its program exits, waking, a title change
     *  showing; all go to the window that holds it now. */
    TerminalTab wire(TerminalTab t) {
        t.onExit = x -> { if (x.owner != null) x.owner.close(x); };
        t.wake = () -> { if (t.owner != null) t.owner.wake(t); };
        t.title.addListener((ov, was, now) -> { if (t.owner != null) t.owner.refresh(); });
        return t;
    }

    /** Another launch asked for something (Tabby's `open` / `run`, or just to be shown): to the last window used. */
    void handle(Request r) {
        var w = windows.contains(lastFocused) ? lastFocused : windows.getFirst();
        if (!"show".equals(r.kind)) w.list.getSelectionModel().select(w.adopt(wire(TerminalTab.of(options, theme, r))));
        if (w.stage.isIconified()) w.stage.setIconified(false);
        w.stage.toFront();
    }

    /** A new window: with the given tab moved into it (dragged out, or "Move to window > New window"), or with a fresh
     *  tab; placed at the given screen point, or a little off the window it came from. */
    TabsWindow newWindow(TerminalTab t, double x, double y, TabsWindow from) {
        var ws = new WindowState();
        ws.width = from.stage.getWidth();
        ws.height = from.stage.getHeight();
        ws.sidebar = from.sidebarWidth();
        ws.x = Double.isNaN(x) ? from.stage.getX() + 40 : x - 100;
        ws.y = Double.isNaN(y) ? from.stage.getY() + 40 : y - 20;
        var w = new TabsWindow(this, new Stage(), ws);
        windows.add(w);
        if (t != null && t.owner != null) t.owner.take(t);
        w.put(t != null ? t : wire(TerminalTab.fresh(options, theme)), 0);
        w.open(ws);
        lastFocused = w;
        return w;
    }

    /** A tab sent to another window (dropped on its tab list, or "Move to window"): it keeps running. */
    void moveTo(TerminalTab t, TabsWindow to, int index) {
        if (t.owner == to) {
            to.move(t, index);
            return;
        }
        if (t.owner != null) t.owner.take(t);
        to.put(t, index);
        to.stage.toFront();
    }

    /** A window with no tabs left vanishes; the last one quits (saving, as closing does). */
    void emptied(TabsWindow w) {
        if (windows.size() <= 1) {
            quit();
            return;
        }
        windows.remove(w);
        w.stage.close();
        if (lastFocused == w) lastFocused = windows.getFirst();
    }

    /** The window's own close button: the last window quits (everything saved); another window closes with its tabs,
     *  after asking if any claude in it would be ended. */
    void closeWindow(TabsWindow w) {
        if (windows.size() <= 1) {
            quit();
            return;
        }
        var all = List.copyOf(w.tabs);
        if (!okToEnd(w.stage, all, "Closing this window ends its " + all.size() + " tabs (claude conversations stay on disk"
                + " and can be resumed).")) return;
        all.forEach(w::close);
    }

    /** True when none of the tabs has claude mid-reply, or the user agreed to end them anyway. */
    static boolean okToEnd(Stage owner, List<TerminalTab> which, String consequence) {
        var busy = which.stream().filter(TerminalTab::live).flatMap(t -> ClaudeProbe.of(t.connector.process.pid()).stream())
                .filter(c -> !c.idle()).map(c -> c.name != null ? c.name : c.sessionId).toList();
        return busy.isEmpty() || Ui.confirm(owner, "Still working: " + String.join(", ", busy) + ".\n" + consequence);
    }

    /** Every terminal in every window takes the new font size, keeping its pixel area (so its rows and columns
     *  change), and so does the text of a sleeping tab. */
    void fontSize(double size) {
        if (!FontSize.set(size)) return;
        for (var t : allTabs()) t.fontChanged();
    }

    List<TerminalTab> allTabs() {
        return windows.stream().flatMap(w -> w.tabs.stream()).toList();
    }

    List<TerminalTab> hidden() {
        return windows.stream().flatMap(w -> w.hidden().stream()).toList();
    }

    void refreshAll() {
        windows.forEach(TabsWindow::refresh);
    }

    void copy(String text) {
        var c = new javafx.scene.input.ClipboardContent();
        c.putString(text);
        javafx.scene.input.Clipboard.getSystemClipboard().setContent(c);
    }

    /** The line that brings this tab back in any cmd window: its folder, then claude --resume with its flags, or its
     *  shell. Reading a live claude's flags takes a WMI call, so it runs off the FX thread. */
    void copyCommand(TerminalTab t) {
        Thread.ofPlatform().daemon().start(() -> {
            var s = t.live() ? t.snapshot(ClaudeProbe.commandLines()) : t.sleeping;
            if (s != null) Platform.runLater(() -> copy(s.resumeLine()));
        });
    }

    /** Saves every tab of every window (claude sessions with their id and flags, which window it was in), asks first
     *  if any claude is mid-turn, then ends all. */
    void quit() {
        var snapshot = new ArrayList<TabsWindow>(windows);
        var owner = (lastFocused != null ? lastFocused : snapshot.getFirst()).stage;
        Thread.ofPlatform().daemon().start(() -> {
            var lines = ClaudeProbe.commandLines();
            var all = snapshot.stream().flatMap(w -> w.tabs.stream()).toList();
            var saved = new ArrayList<SavedTab>();
            for (var i = 0; i < snapshot.size(); i++) {
                for (var t : snapshot.get(i).tabs) {
                    var s = t.snapshot(lines);
                    if (s == null) continue;
                    s.window = i;
                    s.selected = t == snapshot.get(i).selected();
                    saved.add(s);
                    // a live tab goes into history as it was when Tabs closed (a sleeping one went in when it slept)
                    if (t.live()) history.record(s, "open when Tabs closed");
                }
            }
            var busy = all.stream().filter(TerminalTab::live).flatMap(t -> ClaudeProbe.of(t.connector.process.pid()).stream())
                    .filter(c -> !c.idle()).map(c -> c.name != null ? c.name : c.sessionId).toList();
            Platform.runLater(() -> {
                if (!busy.isEmpty() && !Ui.confirm(owner, "Still working: " + String.join(", ", busy)
                        + ".\nClosing ends them mid-turn. They are saved and resume when their tab is opened again.")) return;
                store.save(saved);
                history.flush();
                all.forEach(TerminalTab::close);
                if (!options.offscreen && options.state == null) WindowState.saveAll(snapshot);
                if (!options.offscreen && options.state == null || options.serve) Instance.release();
                Platform.exit();
                System.exit(0);
            });
        });
    }
}
