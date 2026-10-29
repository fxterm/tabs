package fxterm.tabs;

import module java.base;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.*;
import javafx.application.Platform;

/** Drives a running Tabs from outside, as CDP drives a browser: list the windows and tabs, open, select, move,
 *  rename, colour, scroll, send text, read the screen, hibernate, wake, close, take a picture.
 *  `fxterm-tabs ctl <command> [name=value ...]` prints the JSON reply; `fxterm-tabs ctl help` lists the commands.
 *  The channel is a Unix-domain socket file (no network port) in ~/fxterm/tabs/run, so only this Windows user's
 *  processes reach it; one JSON request per connection, one JSON reply. Not gated yet. */
public final class Control {

    static final ObjectMapper JSON = new ObjectMapper();

    static final String HELP = """
            fxterm-tabs ctl <command> [name=value ...]     (values: text, numbers, true/false)
              list                                   windows and tabs: id, index, title, folder, live, colour, claude
              new [dir=] [command=] [title=] [window=] a tab (a shell in dir, or running command)
              separator title= [at=] [window=]       a separator row at index at (end if left out)
              select id=                             show that tab
              move id= to= [window=]                 to index `to` of its window, or of another window
              drag id= row= [after=true|false]        as a mouse drop on list row `row` (top or bottom half)
              rename id= title=                      (empty title: the program's own title again)
              collapse id=<label> [on=true|false]     fold or unfold a group (toggles without on=)
              color id= color=#rrggbb|none
              send id= text= [enter=true]            type text into the tab ("\\r" for Enter also works)
              buffer id= [lines=50]                  the last lines of its text
              scroll id= by=<lines>|top=true|bottom=true
              hibernate id=   wake id=   close id=
              window-new                             a new window with a fresh tab
              shot file=<png> [window=0]             a picture of a window
              events [n=20]                          the last events from the event log
            Ids come from `list`; they last while Tabs runs. Windows count from 0, tabs from 0 in list order.
            """;

    /** Where the socket lives: in the state folder, or beside a --state file for tests (a short temp name, since a
     *  Unix-domain socket path is limited to about 100 characters). */
    static Path socket(String state) {
        if (state == null) return Path.of(System.getProperty("user.home"), "fxterm", "tabs", "run", "ctl.sock");
        return Path.of(System.getProperty("java.io.tmpdir"), "fxtabs-" + Integer.toHexString(state.hashCode()) + ".sock");
    }

    // --- client: `fxterm-tabs ctl ...` ---

    static int client(List<String> args) {
        String state = null;
        var req = JSON.createObjectNode();
        var words = new ArrayList<String>();
        for (var a : args) {
            var eq = a.indexOf('=');
            if (a.startsWith("--state=")) state = a.substring(8);
            else if (eq > 0) req.set(a.substring(0, eq), value(a.substring(eq + 1)));
            else words.add(a);
        }
        if (words.isEmpty() || words.getFirst().equals("help")) {
            System.out.print(HELP);
            return 0;
        }
        req.put("cmd", words.getFirst());
        try (var ch = SocketChannel.open(UnixDomainSocketAddress.of(socket(state)))) {
            ch.write(ByteBuffer.wrap((JSON.writeValueAsString(req) + "\n").getBytes(StandardCharsets.UTF_8)));
            var reply = new BufferedReader(new InputStreamReader(Channels.newInputStream(ch), StandardCharsets.UTF_8)).readLine();
            System.out.println(reply);
            return reply != null && reply.contains("\"ok\":true") ? 0 : 1;
        } catch (IOException e) {
            System.out.println("{\"ok\":false,\"error\":\"Tabs is not running (no control socket at " + socket(state).toString().replace("\\", "\\\\") + ")\"}");
            return 2;
        }
    }

    static JsonNode value(String s) {
        if (s.equals("true") || s.equals("false")) return BooleanNode.valueOf(Boolean.parseBoolean(s));
        if (s.matches("-?\\d{1,9}")) return IntNode.valueOf(Integer.parseInt(s));
        return TextNode.valueOf(s);
    }

    // --- server, in the running Tabs ---

    static void serve(TabsApp app, String state) {
        var path = socket(state);
        try {
            Files.createDirectories(path.getParent());
            Files.deleteIfExists(path);
            var server = ServerSocketChannel.open(StandardProtocolFamily.UNIX);
            server.bind(UnixDomainSocketAddress.of(path));
            Thread.ofPlatform().daemon().name("control").start(() -> {
                while (true) {
                    try (var ch = server.accept()) {
                        var line = new BufferedReader(new InputStreamReader(Channels.newInputStream(ch), StandardCharsets.UTF_8)).readLine();
                        var reply = handle(app, line);
                        ch.write(ByteBuffer.wrap((reply + "\n").getBytes(StandardCharsets.UTF_8)));
                    } catch (IOException | RuntimeException e) {
                        System.err.println("fxterm-tabs: control request failed: " + e);
                    }
                }
            });
            System.err.println("fxterm-tabs: control socket " + path);
        } catch (IOException e) {
            System.err.println("fxterm-tabs: no control socket: " + e);
        }
    }

    static void release(String state) {
        try {
            Files.deleteIfExists(socket(state));
        } catch (IOException ignored) { }
    }

    /** Runs the request on the FX thread and waits for its answer. */
    static String handle(TabsApp app, String line) {
        var result = new CompletableFuture<ObjectNode>();
        JsonNode req;
        try {
            req = JSON.readTree(line);
        } catch (IOException e) {
            return "{\"ok\":false,\"error\":\"not JSON\"}";
        }
        Events.log("ctl", "request", req);
        Platform.runLater(() -> {
            try {
                var out = JSON.createObjectNode().put("ok", true);
                run(app, req, out);
                result.complete(out);
            } catch (RuntimeException e) {
                result.complete(JSON.createObjectNode().put("ok", false).put("error", String.valueOf(e.getMessage())));
            }
        });
        try {
            return JSON.writeValueAsString(result.get(15, TimeUnit.SECONDS));
        } catch (Exception e) {
            return "{\"ok\":false,\"error\":\"timed out\"}";
        }
    }

    static void run(TabsApp app, JsonNode r, ObjectNode out) {
        var cmd = r.path("cmd").asText();
        switch (cmd) {
            case "list" -> out.set("windows", list(app));
            case "new" -> {
                var w = window(app, r);
                var req = new Request();
                req.kind = "run";
                req.cwd = r.path("dir").asText(app.options.cwd != null ? app.options.cwd : System.getProperty("user.home"));
                if (r.hasNonNull("command")) req.argv = ClaudeProbe.tokens(r.get("command").asText());
                var t = w.adopt(app.wire(TerminalTab.of(app.options, app.theme, req)));
                if (r.hasNonNull("title")) t.custom.set(r.get("title").asText());
                w.list.getSelectionModel().select(t);
                out.put("id", t.id);
            }
            case "separator" -> {
                var w = window(app, r);
                var sep = TerminalTab.separator(app.options, app.theme, need(r, "title"));
                w.tabs.add(Math.clamp(r.path("at").asInt(w.tabs.size()), 0, w.tabs.size()), sep);
                sep.owner = w;
                w.refresh();
                out.put("id", sep.id);
            }
            case "select" -> { var t = tab(app, r); t.owner.list.getSelectionModel().select(t); }
            case "move" -> {
                var t = tab(app, r);
                var to = r.path("to").asInt();
                if (r.has("window")) app.moveTo(t, window(app, r), to);
                else t.owner.move(t, to);
            }
            case "drag" -> { var t = tab(app, r); t.owner.drop(t, r.path("row").asInt(), r.path("after").asBoolean(false)); }
            case "rename" -> { var t = tab(app, r); var s = r.path("title").asText(""); t.custom.set(s.isBlank() ? null : s); t.owner.refresh(); }
            case "color" -> { var t = tab(app, r); var c = r.path("color").asText("none"); t.color.set(c.equals("none") ? null : c); t.owner.refresh(); }
            case "send" -> {
                var t = live(app, r);
                var text = need(r, "text").replace("\\r", "\r");
                var panel = t.widget.getTerminalPanel();
                panel.pasteText(text);
                if (r.path("enter").asBoolean(false)) panel.sendText("\r");
            }
            case "buffer" -> {
                var t = live(app, r);
                var text = t.screenText();
                var lines = text.split("\n", -1);
                var n = r.path("lines").asInt(50);
                out.put("text", String.join("\n", Arrays.asList(lines).subList(Math.max(0, lines.length - n), lines.length)));
            }
            case "scroll" -> {
                var panel = live(app, r).widget.getTerminalPanel();
                if (r.path("top").asBoolean(false)) panel.scrollTo(Integer.MIN_VALUE);
                else if (r.path("bottom").asBoolean(false)) panel.scrollTo(Integer.MAX_VALUE);
                else panel.scrollBy(r.path("by").asInt());
            }
            case "collapse" -> {
                var t = tab(app, r);
                if (!t.separator) throw new IllegalArgumentException("tab " + t.id + " is not a group label");
                if (t.collapsed != r.path("on").asBoolean(!t.collapsed)) t.owner.toggleGroup(t);
                out.put("collapsed", t.collapsed);
            }
            case "hibernate" -> { var t = tab(app, r); t.owner.hibernate(t); }
            case "wake" -> { var t = tab(app, r); t.owner.wake(t); }
            case "close" -> { var t = tab(app, r); t.owner.close(t); }
            case "window-new" -> out.put("window", app.windows.indexOf(app.newWindow(null, Double.NaN, Double.NaN, app.windows.getFirst())));
            case "shot" -> Snapshot.write(window(app, r).scene, need(r, "file"));
            case "events" -> {
                try {
                    var all = Files.exists(Events.file) ? Files.readAllLines(Events.file) : List.<String>of();
                    var n = r.path("n").asInt(20);
                    var arr = out.putArray("events");
                    for (var l : all.subList(Math.max(0, all.size() - n), all.size())) arr.add(JSON.readTree(l));
                } catch (IOException e) {
                    throw new UncheckedIOException(e);
                }
            }
            default -> throw new IllegalArgumentException("unknown command '" + cmd + "' (fxterm-tabs ctl help)");
        }
    }

    static ArrayNode list(TabsApp app) {
        var windows = JSON.createArrayNode();
        for (var w : app.windows) {
            var wn = windows.addObject().put("index", app.windows.indexOf(w)).put("focused", w.stage.isFocused());
            var tabs = wn.putArray("tabs");
            for (var t : w.tabs) {
                var n = tabs.addObject().put("id", t.id).put("index", w.tabs.indexOf(t)).put("title", t.shown());
                if (t.separator) { n.put("separator", true).put("collapsed", t.collapsed); continue; }
                if (w.folded(t)) n.put("folded", true);
                n.put("folder", t.where()).put("live", t.live()).put("selected", t == w.selected()).put("color", t.color.get());
                if (t.live()) {
                    n.put("pid", t.connector.process.pid());
                    ClaudeProbe.of(t.connector.process.pid()).ifPresent(c -> n.putObject("claude").put("session", c.sessionId).put("status", c.status).put("name", c.name));
                }
            }
        }
        return windows;
    }

    static TabsWindow window(TabsApp app, JsonNode r) {
        var i = r.path("window").asInt(app.windows.indexOf(app.lastFocused));
        if (i < 0 || i >= app.windows.size()) throw new IllegalArgumentException("no window " + i);
        return app.windows.get(i);
    }

    static TerminalTab tab(TabsApp app, JsonNode r) {
        var id = need(r, "id");
        return app.allTabs().stream().filter(t -> t.id.equals(id)).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("no tab " + id + " (fxterm-tabs ctl list)"));
    }

    static TerminalTab live(TabsApp app, JsonNode r) {
        var t = tab(app, r);
        if (!t.live()) throw new IllegalArgumentException("tab " + t.id + " is asleep (ctl wake id=" + t.id + ")");
        return t;
    }

    static String need(JsonNode r, String name) {
        if (!r.hasNonNull(name)) throw new IllegalArgumentException("missing " + name + "=");
        return r.get(name).asText();
    }

    private Control() { }
}
