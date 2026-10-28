package fxterm.tabs;

import module java.base;
import javafx.application.Platform;
import javafx.event.Event;
import javafx.scene.Scene;
import javafx.scene.input.*;

/** Everything only unattended tests use: benchmark, scripted keys, a scene snapshot, a timed quit. */
public final class TestHooks {

    static void start(TabsWindow app, Scene scene, Options o) {
        if (o.bench > 0) new Bench(app.selected(), o).run();
        // font size: Ctrl+= twice, Ctrl+- once, Ctrl+wheel up one notch, Ctrl+0; the size and the shown grid after each
        if (o.testFont != null) Thread.ofPlatform().daemon().start(() -> {
            Bench.sleep(2500);
            Runnable show = () -> {
                var t = app.selected();
                var grid = t != null && t.widget != null ? t.widget.getTerminalPanel().getTerminalSizeFromComponent() : null;
                System.err.println("test-font: size " + FontSize.get() + ", grid " + (grid == null ? "-" : grid.getColumns() + "x" + grid.getRows()));
            };
            Platform.runLater(show);
            for (var k : List.of(KeyCode.EQUALS, KeyCode.EQUALS, KeyCode.MINUS)) {
                Bench.sleep(600);
                Platform.runLater(() -> { press(scene, k, true, false); show.run(); });
            }
            Bench.sleep(600);
            Platform.runLater(() -> {
                var c = app.content;
                var b = c.localToScene(c.getLayoutBounds());
                Event.fireEvent(c, new ScrollEvent(ScrollEvent.SCROLL, b.getCenterX(), b.getCenterY(), b.getCenterX(), b.getCenterY(),
                        false, true, false, false, false, false, 0, 40, 0, 40, ScrollEvent.HorizontalTextScrollUnits.NONE, 0,
                        ScrollEvent.VerticalTextScrollUnits.NONE, 0, 0, new PickResult(c, b.getCenterX(), b.getCenterY())));
                show.run();
            });
            Bench.sleep(1500);
            Platform.runLater(() -> Snapshot.write(scene, o.testFont + "-big.png"));
            Bench.sleep(600);
            Platform.runLater(() -> { press(scene, KeyCode.DIGIT0, true, false); show.run(); });
        });
        if (o.testKeys) Thread.ofPlatform().daemon().start(() -> {
            for (var k : List.of(KeyCode.T, KeyCode.T, KeyCode.TAB, KeyCode.W, KeyCode.W)) {
                Bench.sleep(1500);
                Platform.runLater(() -> press(scene, k, true, k != KeyCode.TAB));
                Bench.sleep(500);
                Platform.runLater(() -> System.err.println("test-keys: after " + k + " tab " + (app.tabs.indexOf(app.selected()) + 1)
                        + "/" + app.tabs.size() + ", focus on " + (scene.getFocusOwner() == null ? "nothing" : scene.getFocusOwner().getClass().getSimpleName())));
            }
        });
        // pictures: the window with the compose box open, and a tab's menu
        if (o.testLook != null) Thread.ofPlatform().daemon().start(() -> {
            Bench.sleep(1000);
            Platform.runLater(app::wakeAll);
            Bench.sleep(4500);
            Platform.runLater(() -> {
                app.toggleCompose();
                app.composer.text.setText("A message written in the compose box,\nsent to the tab with Ctrl+Enter.");
                // the figures from the user's example, so the badge colours can be seen side by side
                var mb = new long[] {266, 318, 398, 17};
                for (var i = 0; i < Math.min(4, app.tabs.size()); i++) app.tabs.get(i).ramBytes = mb[i] * 1024 * 1024;
                app.refresh();
            });
            Bench.sleep(800);
            Platform.runLater(() -> Snapshot.write(scene, o.testLook + "-main.png"));
            Platform.runLater(() -> {
                var menu = app.sidebar.menu(app.tabs.get(0));
                menu.show(app.stage);
                Platform.runLater(() -> Snapshot.write(menu.getScene(), o.testLook + "-menu.png"));
            });
        });
        // the compose box types into the tab: echo a marker, then read the screen
        if (o.testCompose) Thread.ofPlatform().daemon().start(() -> {
            Bench.sleep(2500);
            Platform.runLater(() -> {
                app.toggleCompose();
                app.composer.text.setText("echo COMPOSE-" + "OK");
                app.composer.send(true);
            });
            Bench.sleep(1500);
            Platform.runLater(() -> {
                var text = app.selected().screenText();
                System.err.println("test-compose: last lines:\n" + String.join("\n", text.lines().toList().subList(Math.max(0, (int) text.lines().count() - 4), (int) text.lines().count())));
                System.err.println("test-compose: output seen " + text.lines().anyMatch(l -> l.strip().equals("COMPOSE-OK"))
                        + ", box closed " + !app.bottom.getChildren().contains(app.composer) + ", focus on "
                        + (scene.getFocusOwner() == null ? "nothing" : scene.getFocusOwner().getClass().getSimpleName()));
            });
        });
        // windows: a tab out into a new window (still the same process), back again (the empty window vanishes), split
        // again for the save
        if (o.testWindows) Thread.ofPlatform().daemon().start(() -> {
            var a = app.app;
            Runnable state = () -> System.err.println("test-windows:   " + a.windows.size() + " windows "
                    + a.windows.stream().map(w -> w.tabs.stream().map(TerminalTab::where).toList()).toList());
            Bench.sleep(1500);
            Platform.runLater(() -> {
                app.wakeAll();
                var second = app.tabs.get(1);
                var pid = second.connector.process.pid();
                a.newWindow(second, 300, 300, app);
                System.err.println("test-windows: tab 2 to a new window; same process " + (second.live() && second.connector.process.pid() == pid));
                state.run();
            });
            Bench.sleep(2000);
            Platform.runLater(() -> {
                var w2 = a.windows.get(1);
                a.moveTo(w2.tabs.getFirst(), a.windows.getFirst(), Integer.MAX_VALUE);
                System.err.println("test-windows: moved back");
                state.run();
            });
            Bench.sleep(1500);
            Platform.runLater(() -> {
                a.newWindow(app.tabs.get(2), 400, 400, app);
                System.err.println("test-windows: split again");
                state.run();
            });
        });
        // history: a tab that printed a marker is closed, another hibernated; History finds the marker, previews it,
        // resumes it
        if (o.testHistory != null) Thread.ofPlatform().daemon().start(() -> {
            Bench.sleep(1500);
            Platform.runLater(app::wakeAll);
            Bench.sleep(2500);
            Platform.runLater(() -> app.tabs.getFirst().widget.getTerminalPanel().pasteText("echo HISTORY-MARKER-" + "ONE\r"));
            Bench.sleep(1500);
            Platform.runLater(() -> {
                app.close(app.tabs.getFirst());
                app.hibernate(app.tabs.getFirst());
            });
            Bench.sleep(3000);
            Platform.runLater(() -> {
                app.showHistory();
                app.historyPane.search.setText("history-marker-one");
            });
            Bench.sleep(2500);
            Platform.runLater(() -> {
                var p = app.historyPane;
                var e = p.chosen();
                System.err.println("test-history: all " + p.all.size() + " entries " + p.all.stream().map(x -> x.event + ":" + x.cwd).toList());
                System.err.println("test-history: search found " + p.results.getItems().size() + (e == null ? "" : ", first " + e.cwd
                        + ", preview has marker " + p.preview.getText().contains("HISTORY-MARKER-ONE")
                        + ", selection " + p.preview.getSelectedText()));
                Snapshot.write(scene, o.testHistory);
                var before = app.tabs.size();
                p.resume();
                System.err.println("test-history: resumed, tabs " + before + " -> " + app.tabs.size() + ", new tab live "
                        + app.selected().live() + " in " + app.selected().where() + ", history view closed " + !app.historyShown());
            });
        });
        // the order of rows: drops before or after a row, a new tab placed after the shown one
        if (o.testOrder) Thread.ofPlatform().daemon().start(() -> {
            Runnable order = () -> System.err.println("test-order:   " + app.tabs.stream().map(t -> t.separator ? "[" + t.shown() + "]" : t.shown()).toList());
            Bench.sleep(1200);
            Platform.runLater(() -> {
                System.err.println("test-order: start"); order.run();
                var a = app.tabs.get(1); var code = app.tabs.get(2); var b = app.tabs.get(3);
                app.drop(b, app.tabs.indexOf(code), false); System.err.println("test-order: b dropped on the top half of CODE"); order.run();
                app.drop(b, app.tabs.indexOf(code), true); System.err.println("test-order: b back under CODE"); order.run();
                app.drop(a, app.tabs.indexOf(code), true); System.err.println("test-order: a dropped on the bottom half of CODE"); order.run();
                app.drop(code, app.tabs.indexOf(a), false); System.err.println("test-order: CODE dropped on the top half of a"); order.run();
                app.list.getSelectionModel().select(a); app.add(); System.err.println("test-order: new tab from a"); order.run();
            });
        });
        // separators: never chosen, not counted, skipped by Ctrl+Tab, kept on save
        if (o.testSeparators) Thread.ofPlatform().daemon().start(() -> {
            Runnable chosen = () -> System.err.println("test-separators:   chosen " + (app.selected() == null ? "none" : app.selected().where()));
            Bench.sleep(1500);
            Platform.runLater(() -> { System.err.println("test-separators: at start"); chosen.run(); });
            Platform.runLater(() -> { app.select(2); System.err.println("test-separators: Ctrl+3"); chosen.run(); });
            Platform.runLater(() -> { app.step(1); System.err.println("test-separators: Ctrl+Tab"); chosen.run(); });
            Platform.runLater(() -> { app.step(1); System.err.println("test-separators: Ctrl+Tab (wraps)"); chosen.run(); });
            Platform.runLater(() -> app.list.getSelectionModel().select(app.tabs.stream().filter(t -> t.separator).findFirst().orElseThrow()));
            Bench.sleep(500);
            Platform.runLater(() -> { System.err.println("test-separators: clicked a separator"); chosen.run(); });
            Platform.runLater(() -> { app.select(2); app.close(app.selected()); System.err.println("test-separators: closed tab 3"); chosen.run(); });
            Bench.sleep(800);
            Platform.runLater(() -> { System.err.println("test-separators: a moment later"); chosen.run(); });
            Platform.runLater(() -> Snapshot.write(scene, o.snapshot != null ? o.snapshot : "separators.png"));
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
                    + " of " + app.tabs.size() + ", button shown " + app.footer.restoreAll.isVisible() + ", focus on "
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
        if (o.quitAfter > 0) Thread.ofPlatform().daemon().start(() -> { Bench.sleep(o.quitAfter); Platform.runLater(app.app::quit); });
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

    static void report(TabsWindow app, Scene scene, String when) {
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
