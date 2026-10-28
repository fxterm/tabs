package fxterm.tabs;

import module java.base;
import javafx.application.Platform;
import javafx.event.Event;
import javafx.scene.Scene;
import javafx.scene.input.*;

/** Everything only unattended tests use: benchmark, scripted keys, a scene snapshot, a timed quit. */
public final class TestHooks {

    static void start(TabsApp app, Scene scene, Options o) {
        if (o.bench > 0) new Bench(app.selected(), o).run();
        if (o.testKeys) Thread.ofPlatform().daemon().start(() -> {
            for (var k : List.of(KeyCode.T, KeyCode.T, KeyCode.TAB, KeyCode.W, KeyCode.W)) {
                Bench.sleep(1500);
                Platform.runLater(() -> press(scene, k, true, k != KeyCode.TAB));
                Bench.sleep(500);
                Platform.runLater(() -> System.err.println("test-keys: after " + k + " tab " + (app.tabs.indexOf(app.selected()) + 1)
                        + "/" + app.tabs.size() + ", focus on " + (scene.getFocusOwner() == null ? "nothing" : scene.getFocusOwner().getClass().getSimpleName())));
            }
        });
        // the tab menu's actions, on plain shells
        if (o.testMenu) Thread.ofPlatform().daemon().start(() -> {
            Runnable state = () -> System.err.println("test-menu:   " + app.tabs.stream()
                    .map(t -> (t.live() ? "live " : "asleep ") + t.where() + (t.sleeping != null && t.sleeping.screen != null ? " [text]" : ""))
                    .toList());
            Bench.sleep(1500);
            Platform.runLater(() -> { app.wakeAll(); System.err.println("test-menu: restore all"); });
            Bench.sleep(3000);
            Platform.runLater(() -> { app.hibernate(app.tabs.get(1)); System.err.println("test-menu: hibernate tab 2"); });
            Bench.sleep(3000);
            Platform.runLater(state);
            Platform.runLater(() -> { app.duplicate(app.tabs.get(0)); System.err.println("test-menu: duplicate tab 1"); state.run(); });
            Bench.sleep(2000);
            Platform.runLater(() -> { app.closeTabs(List.copyOf(app.tabs.subList(0, 2))); System.err.println("test-menu: close above tab 3"); state.run(); });
            Bench.sleep(1500);
            Platform.runLater(() -> { app.closeTabs(List.copyOf(app.tabs.subList(1, app.tabs.size()))); System.err.println("test-menu: close below tab 1"); state.run(); });
            Bench.sleep(1000);
            Platform.runLater(() -> app.copyCommand(app.tabs.get(0)));
            Bench.sleep(3000);
            Platform.runLater(() -> System.err.println("test-menu: clipboard = " + javafx.scene.input.Clipboard.getSystemClipboard().getString()));
        });
        // Ctrl+2 jumps to tab 2, then "Restore all" wakes every sleeping tab
        if (o.testWakeAll) Thread.ofPlatform().daemon().start(() -> {
            Bench.sleep(1500);
            Platform.runLater(() -> {
                Event.fireEvent(scene.getFocusOwner() != null ? scene.getFocusOwner() : scene.getRoot(),
                        new KeyEvent(KeyEvent.KEY_PRESSED, KeyEvent.CHAR_UNDEFINED, "", KeyCode.DIGIT2, false, true, false, false));
                System.err.println("test-wakeall: after Ctrl+2 tab " + (app.tabs.indexOf(app.selected()) + 1));
                app.wakeAll();
            });
            Bench.sleep(2500);
            Platform.runLater(() -> System.err.println("test-wakeall: live " + app.tabs.stream().filter(TerminalTab::live).count()
                    + " of " + app.tabs.size() + ", button shown " + app.sidebar.restoreAll.isVisible() + ", focus on "
                    + (scene.getFocusOwner() == null ? "nothing" : scene.getFocusOwner().getClass().getSimpleName())));
        });
        // a restored tab: shown asleep, Enter wakes it, its shell starts in the saved folder, a cd is noticed
        if (o.testWake) Thread.ofPlatform().daemon().start(() -> {
            Bench.sleep(1200);
            // as if the tab list had been clicked: the keys must come back to the tab
            Platform.runLater(() -> app.list.requestFocus());
            Bench.sleep(300);
            Platform.runLater(() -> report(app, scene, "before Enter, after a click on the list"));
            Platform.runLater(() -> press(scene, KeyCode.ENTER, false, false));
            Bench.sleep(4000);
            Platform.runLater(() -> report(app, scene, "after Enter"));
            Platform.runLater(() -> { try { app.selected().connector.write("cd /d C:\\Windows\\System32\r"); } catch (IOException e) { System.err.println(e); } });
            Bench.sleep(2500);
            Platform.runLater(() -> report(app, scene, "after cd"));
        });
        // Esc reaches the program: cmd clears the line on Esc, so "abc<Esc>x" must leave only "x"
        if (o.testEsc) Thread.ofPlatform().daemon().start(() -> {
            Bench.sleep(2500);
            var canvas = app.selected().widget.getTerminalPanel().getCanvas();
            Platform.runLater(() -> {
                typeText(canvas, "abc");
                fire(canvas, KeyEvent.KEY_PRESSED, KeyEvent.CHAR_UNDEFINED, "", KeyCode.ESCAPE);
                fire(canvas, KeyEvent.KEY_TYPED, "\u001b", "", KeyCode.UNDEFINED);
                fire(canvas, KeyEvent.KEY_RELEASED, KeyEvent.CHAR_UNDEFINED, "", KeyCode.ESCAPE);
                typeText(canvas, "x");
            });
            Bench.sleep(1500);
            Platform.runLater(() -> {
                var b = app.selected().widget.getTerminalTextBuffer();
                b.lock();
                try { System.err.println("test-esc screen:\n" + b.getScreenLines().strip()); } finally { b.unlock(); }
            });
        });
        if (o.snapshot != null) Snapshot.later(scene, o.snapshot, o.snapshotWait);
        if (o.quitAfter > 0) Thread.ofPlatform().daemon().start(() -> { Bench.sleep(o.quitAfter); Platform.runLater(app::quit); });
    }

    static void typeText(javafx.scene.Node n, String s) {
        for (var c : s.toCharArray()) {
            var code = KeyCode.getKeyCode(String.valueOf(Character.toUpperCase(c)));
            fire(n, KeyEvent.KEY_PRESSED, KeyEvent.CHAR_UNDEFINED, String.valueOf(c), code);
            fire(n, KeyEvent.KEY_TYPED, String.valueOf(c), "", KeyCode.UNDEFINED);
            fire(n, KeyEvent.KEY_RELEASED, KeyEvent.CHAR_UNDEFINED, String.valueOf(c), code);
        }
    }

    static void fire(javafx.scene.Node n, javafx.event.EventType<KeyEvent> type, String ch, String text, KeyCode code) {
        Event.fireEvent(n, new KeyEvent(type, ch, text, code, false, false, false, false));
    }

    static void report(TabsApp app, Scene scene, String when) {
        var t = app.selected();
        System.err.println("test-wake: " + when + ": " + (t.live() ? "live" : "asleep") + ", where " + t.where()
                + ", focus on " + (scene.getFocusOwner() == null ? "nothing" : scene.getFocusOwner().getClass().getSimpleName()));
    }

    /** A key press as the keyboard would deliver it, sent to the focused node through the scene. */
    static void press(Scene scene, KeyCode code, boolean ctrl, boolean shift) {
        var target = scene.getFocusOwner() != null ? scene.getFocusOwner() : scene.getRoot();
        Event.fireEvent(target, new KeyEvent(KeyEvent.KEY_PRESSED, KeyEvent.CHAR_UNDEFINED, "", code, shift, ctrl, false, false));
    }

    private TestHooks() { }
}
