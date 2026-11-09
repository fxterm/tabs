package fxterm.tabs;

import module java.base;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import javafx.application.Platform;

/** `ctl wait id= (text= | regex=) [timeout=ms]`: answers when the visible screen of the tab contains the text. Runs on the
 *  connection's own thread, never on the FX thread: it looks up the tab once there, then watches the tab's version counter (a number
 *  compare) and reads the screen only after the version moved, so a waiting client costs next to nothing and holds up no other
 *  connection. Read-only. */
final class Wait {

    static final long DEFAULT_MS = 30_000, MAX_MS = 600_000;

    static ObjectNode run(TabsApp app, JsonNode r) {
        var out = Control.JSON.createObjectNode();
        try {
            var t = lookup(app, r);
            var pattern = pattern(r);
            var timeout = Math.clamp(r.path("timeout").asLong(DEFAULT_MS), 1, MAX_MS);
            return watch(t, pattern, System.nanoTime() + timeout * 1_000_000, out);
        } catch (IllegalArgumentException | ExecutionException e) {
            var cause = e instanceof ExecutionException ? e.getCause() : e;
            return out.put("ok", false).put("error", String.valueOf(cause.getMessage()));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return out.put("ok", false).put("error", "interrupted");
        }
    }

    static TerminalTab lookup(TabsApp app, JsonNode r) throws InterruptedException, ExecutionException {
        var found = new CompletableFuture<TerminalTab>();
        Platform.runLater(() -> {
            try { found.complete(Control.live(app, r)); } catch (RuntimeException e) { found.completeExceptionally(e); }
        });
        return found.get();
    }

    static Pattern pattern(JsonNode r) {
        var text = r.hasNonNull("text") ? r.get("text").asText() : null;
        var regex = r.hasNonNull("regex") ? r.get("regex").asText() : null;
        if ((text == null) == (regex == null)) throw new IllegalArgumentException("give text= or regex= (one of them)");
        try {
            return Pattern.compile(regex != null ? regex : Pattern.quote(text));
        } catch (PatternSyntaxException e) {
            throw new IllegalArgumentException("bad regex: " + e.getDescription());
        }
    }

    static ObjectNode watch(TerminalTab t, Pattern pattern, long deadline, ObjectNode out) throws InterruptedException {
        var checked = -1L;
        while (true) {
            var version = t.version.get();
            var widget = t.widget;
            if (widget == null) return out.put("ok", false).put("error", "tab " + t.id + " is asleep or closed").put("version", version);
            if (version != checked) {
                checked = version;
                var screen = ScreenText.visible(widget.getTerminalTextBuffer());
                var m = pattern.matcher(screen);
                if (m.find()) {
                    var row = (int) screen.substring(0, m.start()).chars().filter(c -> c == '\n').count();
                    return out.put("ok", true).put("matched", m.group()).put("version", version).put("row", row);
                }
            }
            if (System.nanoTime() >= deadline) return out.put("ok", false).put("error", "timeout").put("version", version);
            Thread.sleep(40);
        }
    }

    private Wait() { }
}
