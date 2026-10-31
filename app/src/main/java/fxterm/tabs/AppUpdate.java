package fxterm.tabs;

import module java.base;
import com.fasterxml.jackson.databind.JsonNode;
import javafx.application.Platform;
import javafx.scene.control.Alert;

/** Updating from inside the window, the same on Windows and on a Mac. The launcher (jr) is the one that knows how:
 *  `<exe> -Xjr:update -Xjr:batch` downloads the release, checks its SHA-256 and replaces the exe (on a Mac the whole
 *  Tabs.app), and prints one JSON line. Tabs keeps running the old version until it is restarted. A copy that was
 *  not started through the launcher (a plain jar) cannot do this and sends the user to the release page. */
final class AppUpdate {

    static String exe() {
        var e = System.getProperty("io.github.jarrunner.jr.exe");
        return e != null && Files.isExecutable(Path.of(e)) ? e : null;
    }

    static boolean possible() {
        return exe() != null;
    }

    /** The footer's click: ask, update off the UI thread, then offer the restart. */
    static void now(TabsApp app, TabsWindow w) {
        var u = app.updates;
        if (u == null || !u.available()) return;
        if (!possible()) { Links.open(UpdateCheck.PAGE); return; }
        if (!Ui.ask(w.stage, "Update Tabs?", "Tabs " + u.current + " will be replaced by " + u.latest + ". It is downloaded and checked first, "
                + "and this window keeps running until you restart it. Your tabs and sessions are not touched.", "F2: update    Esc: not now")) return;
        Thread.ofPlatform().daemon().name("app-update").start(() -> {
            var r = run();
            Platform.runLater(() -> done(app, w, r));
        });
    }

    static void done(TabsApp app, TabsWindow w, JsonNode r) {
        var status = r.path("status").asText("error");
        var message = r.path("message").asText("The update did not finish.");
        if ("updated".equals(status)) {
            if (Ui.ask(w.stage, "Updated", message + "\n\nRestart now? Every tab is saved and comes back asleep; a Claude Code tab wakes as the same conversation.",
                    "F2: restart now    Esc: later")) {
                app.restartAfterQuit = true;
                app.quit();
            }
            return;
        }
        var a = new Alert("error".equals(status) ? Alert.AlertType.ERROR : Alert.AlertType.INFORMATION, message);
        a.initOwner(w.stage);
        a.setHeaderText("current".equals(status) ? "Already up to date" : "The update did not finish");
        a.show();
    }

    /** jr's answer: stdout is exactly one JSON line, whatever happens (a stuck update ends after five minutes). */
    static JsonNode run() {
        try {
            var p = new ProcessBuilder(exe(), "-Xjr:update", "-Xjr:batch").redirectError(ProcessBuilder.Redirect.DISCARD).start();
            var out = new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            if (!p.waitFor(5, TimeUnit.MINUTES)) p.destroyForcibly();
            var line = out.lines().filter(l -> l.startsWith("{")).reduce((x, y) -> y).orElse("");
            return UpdateCheck.JSON.readTree(line);
        } catch (IOException | InterruptedException | RuntimeException e) {
            return UpdateCheck.JSON.createObjectNode().put("status", "error").put("message", "The update could not run: " + e);
        }
    }

    /** After Tabs has saved and let go of its sockets: the new version starts. jr's own variables are dropped from the
     *  child's environment so it reads its own configuration, not this process's. */
    static void relaunch() {
        try {
            var pb = new ProcessBuilder(exe());
            pb.environment().remove("JAVA_TOOL_OPTIONS");
            pb.start();
        } catch (IOException e) {
            System.err.println("fxterm-tabs: could not start the new version: " + e);
        }
    }

    private AppUpdate() { }
}
