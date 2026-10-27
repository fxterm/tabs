package tabbyfx;

import module java.base;
import javafx.application.Platform;
import javafx.event.Event;
import javafx.scene.Scene;
import javafx.scene.input.*;

/** Everything only unattended tests use: benchmark, scripted keys, a scene snapshot, a timed quit. */
public final class TestHooks {

    static void start(TabbyApp app, Scene scene, Options o) {
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
        // a restored tab: shown asleep, Enter wakes it, its shell starts in the saved folder, a cd is noticed
        if (o.testWake) Thread.ofPlatform().daemon().start(() -> {
            Bench.sleep(1500);
            Platform.runLater(() -> report(app, scene, "before Enter"));
            Platform.runLater(() -> press(scene, KeyCode.ENTER, false, false));
            Bench.sleep(4000);
            Platform.runLater(() -> report(app, scene, "after Enter"));
            Platform.runLater(() -> { try { app.selected().connector.write("cd /d C:\\Windows\\System32\r"); } catch (IOException e) { System.err.println(e); } });
            Bench.sleep(2500);
            Platform.runLater(() -> report(app, scene, "after cd"));
        });
        if (o.snapshot != null) Snapshot.later(scene, o.snapshot, o.snapshotWait);
        if (o.quitAfter > 0) Thread.ofPlatform().daemon().start(() -> { Bench.sleep(o.quitAfter); Platform.runLater(app::quit); });
    }

    static void report(TabbyApp app, Scene scene, String when) {
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
