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
            fxterm-tabs ctl -                              the request as JSON on stdin (text with quotes or non-ASCII characters a shell would damage)
            fxterm-tabs ctl --json '{"cmd":"list"}'        the same inline
              list                                   windows and tabs: id, index, title, folder, live, colour, claude
              new [dir=] [command=] [title=] [window=] [group=] [from=] [front=true]
                                                     a tab (a shell in dir, or running command). group=<label>: at the end of that
                                                     group (made at the end of the window when there is none). from=<tab id>: in
                                                     the group of that tab (the launcher sends its FXTERM_TABS_TAB); without
                                                     either, the end of the window
              separator title= [at=] [window=]       a separator row at index at (end if left out)
              select id=                             show that tab
              move id= to= [window=]                 to index `to` of its window, or of another window
              drag id= row= [after=true|false]        as a mouse drop on list row `row` (top or bottom half)
              rename id= title=                      (empty title: the program's own title again)
              collapse id=<label> [on=true|false]     fold or unfold a group (toggles without on=)
              color id= color=#rrggbb|none
              send id= text= [enter=true]            type text into the tab ("\\r" for Enter also works)
              buffer id= [lines=50]                  the last lines of its text
              screen id= [since=<version>]           the visible screen with styling, read-only: cols, rows, cursor{x,y,visible}, version, default{fg,bg},
                                                     lines = per row runs {x,t,fg,bg,b,i,u,inv} (fg/bg as painted, inverse applied; left out when default).
                                                     since=<version of the last answer>: {unchanged:true,version} when nothing changed. A sleeping tab answers
                                                     its saved text unstyled with asleep:true. No scrollback.
              scroll id= by=<lines>|top=true|bottom=true
              hibernate id=   wake id=   close id=
              show [window=]                         bring a window forward (what a bare `fxterm-tabs` launch does)
              window-new                             a new window with a fresh tab
              shot file=<png> [window=0]             a picture of a window
              key spec=meta+shift+e [window=0]       one key press to the window (ctrl, alt, shift, meta + a KeyCode name)
              switcher [window=0] [step=1] [query=]  open the Ctrl+Tab popup, optionally with text typed in its box
              switcher-shot file=<png>               a picture of the open popup;  switcher-close  closes it;  switcher-key spec=  a key to it
              attention [on|off]                    a bell on the calling tab (run inside it): rings once, asks the user to switch to it; off takes it away
              whoami                                 run inside a tab: its id, window index, group (null when none), folder; read-only, about the caller only
              window-name [window=0] name=           name a window (empty: Window N);  window-colour [window=0] colour=#rrggbb (empty or none: no colour)
              events [n=20]                          the last events from the event log
            Ids come from `list`; they last while Tabs runs. Windows count from 0, tabs from 0 in list order.
            """;

    /**
     * A launch hands its request to the running window through the control socket: `new` opens the tab in whichever window
     * answers (and brings it forward), `show` only brings the window forward. A socket file left by a Tabs that crashed has
     * nobody listening, so the connect fails and the launch opens a window of its own.
     */
    static boolean join(Request r) {
        var req = JSON.createObjectNode().put("cmd", "show".equals(r.kind) ? "show" : "new");
        if (!"show".equals(r.kind)) {
            req.put("dir", r.cwd).put("front", true);
            if ("run".equals(r.kind) && !r.argv.isEmpty()) {
                req.put("command", quoted(r.argv));
                r.argv.forEach(req.putArray("argv")::add);
            }
            if (r.group != null) req.put("group", r.group);
            if (r.from != null) req.put("from", r.from);
        }
        try (var ch = SocketChannel.open(UnixDomainSocketAddress.of(socket(null)))) {
            ch.write(ByteBuffer.wrap((JSON.writeValueAsString(req) + "\n").getBytes(StandardCharsets.UTF_8)));
            var reply = new BufferedReader(new InputStreamReader(Channels.newInputStream(ch), StandardCharsets.UTF_8)).readLine();
            return reply != null && JSON.readTree(reply).path("ok").asBoolean(false);
        } catch (IOException e) {
            return false;
        }
    }

    /** The words of a command line again, with quotes round any word that holds a space: ClaudeProbe.tokens reads them back. */
    static String quoted(List<String> words) {
        return words.stream().map(w -> w.isEmpty() || w.chars().anyMatch(Character::isWhitespace) ? "\"" + w + "\"" : w)
            .collect(java.util.stream.Collectors.joining(" "));
    }

    /** Where the socket lives: in the state folder, or beside a --state file for tests (a short temp name, since a
     *  Unix-domain socket path is limited to about 100 characters). */
    static Path socket(String state) {
        if (state == null) return System.getenv("FXTERM_TABS_INSTANCE") != null ? Path.of(System.getenv("FXTERM_TABS_INSTANCE"))
                : Path.of(System.getProperty("user.home"), "fxterm", "tabs", "run", "ctl.sock");
        return Path.of(System.getProperty("java.io.tmpdir"), "fxtabs-" + Integer.toHexString(state.hashCode()) + ".sock");
    }

    // --- client: `fxterm-tabs ctl ...` ---

    static int client(List<String> args) {
        var state = CtlArgs.state(args);
        ObjectNode req;
        try {
            req = CtlArgs.request(args);
        } catch (IOException | IllegalArgumentException e) {
            System.out.println("{\"ok\":false,\"error\":" + JSON.valueToTree(String.valueOf(e.getMessage())) + "}");
            return 2;
        }
        if (req == null) {
            System.out.print(HELP);
            return 0;
        }
        var cmd = req.path("cmd").asText();
        if (cmd.equals("attention") || cmd.equals("whoami")) {
            req.put("pid", ProcessHandle.current().pid());
            if (System.getenv("CLAUDE_CODE_SESSION_ID") != null) req.put("session", System.getenv("CLAUDE_CODE_SESSION_ID"));
            if (cmd.equals("whoami") && System.getenv("FXTERM_TABS_TAB") != null) req.put("tab", System.getenv("FXTERM_TABS_TAB"));
        }
        try (var ch = SocketChannel.open(UnixDomainSocketAddress.of(socket(state)))) {
            ch.write(ByteBuffer.wrap((JSON.writeValueAsString(req) + "\n").getBytes(StandardCharsets.UTF_8)));
            var reply = new BufferedReader(new InputStreamReader(Channels.newInputStream(ch), StandardCharsets.UTF_8)).readLine();
            System.out.println(CtlArgs.ascii(reply));
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
            if (alive(path)) {
                System.err.println("fxterm-tabs: another Tabs answers on " + path + ", so this one has no control socket");
                return;
            }
            Files.deleteIfExists(path);
            var server = ServerSocketChannel.open(StandardProtocolFamily.UNIX);
            server.bind(UnixDomainSocketAddress.of(path));
            Thread.ofPlatform().daemon().name("control").start(() -> {
                while (true) {
                    try (var ch = server.accept()) {
                        var line = new BufferedReader(new InputStreamReader(Channels.newInputStream(ch), StandardCharsets.UTF_8)).readLine();
                        if (line == null) continue;   // a liveness probe (alive) that sends nothing
                        var reply = handle(app, line);
                        ch.write(ByteBuffer.wrap((reply + "\n").getBytes(StandardCharsets.UTF_8)));
                    } catch (IOException | RuntimeException e) {
                        System.err.println("fxterm-tabs: control request failed: " + e);
                    }
                }
            });
            // a window that is killed leaves its socket file behind: a launch then fails to connect and opens its own window, which is
            // correct, but remove it on the way out anyway
            if (owned.isEmpty()) Runtime.getRuntime().addShutdownHook(new Thread(() -> release(null)));
            owned.add(path);
            System.err.println("fxterm-tabs: control socket " + path);
        } catch (IOException e) {
            System.err.println("fxterm-tabs: no control socket: " + e);
        }
    }

    /** The sockets this process bound (its own, and the one launches use when it is the instance); release removes only those, never another running Tabs's. */
    static final List<Path> owned = new CopyOnWriteArrayList<>();

    /** True when something is listening on the socket file (a real Tabs, as opposed to a file left by a crash). */
    static boolean alive(Path path) {
        if (!Files.exists(path)) return false;
        try (var ch = SocketChannel.open(UnixDomainSocketAddress.of(path))) {
            return true;
        } catch (IOException e) {
            return false;
        }
    }

    static void release(String state) {
        for (var p : owned) try { Files.deleteIfExists(p); } catch (IOException ignored) { }
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
        if (!"screen".equals(req.path("cmd").asText())) Events.log("ctl", "request", req);
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
                var req = new Request();
                req.kind = "run";
                req.cwd = r.hasNonNull("dir") ? r.get("dir").asText() : null;
                if (r.hasNonNull("command")) req.argv = ClaudeProbe.tokens(r.get("command").asText());
                if (r.path("argv").isArray()) { req.argv = new ArrayList<>(); r.get("argv").forEach(a -> req.argv.add(a.asText())); }
                var t = app.wire(TerminalTab.of(app.options, app.theme, req));
                var w = Placement.add(app, t, r.path("group").asText(null), r.path("from").asText(null), window(app, r), r.has("window"));
                if (r.hasNonNull("title")) t.custom.set(r.get("title").asText());
                w.list.getSelectionModel().select(t);
                if (r.path("front").asBoolean(false)) front(w);
                out.put("id", t.id);
            }
            case "show" -> front(window(app, r));
            case "whoami" -> {
                var t = caller(app, r);
                var head = t.owner.headOf(t);
                out.put("id", t.id).put("window", app.windows.indexOf(t.owner)).put("title", t.shown()).put("folder", t.where());
                if (head == null) out.putNull("group"); else out.put("group", head.shown());
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
            case "screen" -> ScreenView.read(tab(app, r), r.path("since").asLong(-1), out);
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
            case "hideasleep" -> {
                var t = tab(app, r);
                if (!t.separator) throw new IllegalArgumentException("tab " + t.id + " is not a group label");
                if (t.hideAsleep != r.path("on").asBoolean(!t.hideAsleep)) t.owner.toggleAsleep(t);
                out.put("hideAsleep", t.hideAsleep);
            }
            case "hibernate" -> { var t = tab(app, r); t.owner.hibernate(t); }
            case "wake" -> { var t = tab(app, r); t.owner.wake(t); }
            case "close" -> { var t = tab(app, r); t.owner.close(t); }
            case "window-new" -> out.put("window", app.windows.indexOf(app.newWindow(null, Double.NaN, Double.NaN, app.windows.getFirst())));
            case "shot" -> Snapshot.write(window(app, r).scene, need(r, "file"));
            case "attention" -> {
                var t = r.hasNonNull("id") ? tab(app, r) : caller(app, r);
                t.asking = r.path("on").asBoolean(true);
                app.attention.update(t);
                t.owner.refresh();
                out.put("id", t.id).put("attention", t.attention());
            }
            case "switcher" -> {
                app.switcher.open(window(app, r), r.path("step").asInt(0));
                if (r.hasNonNull("query")) app.switcher.filter.setText(r.get("query").asText());
            }
            case "switcher-shot" -> Snapshot.write(app.switcher.stage.getScene(), need(r, "file"));
            case "attention-key" -> TestHooks.pressKeys(app.attention.shown.stage.getScene(), need(r, "spec"));
            case "attention-shot" -> Snapshot.write(app.attention.shown.stage.getScene(), need(r, "file"));
            case "switcher-close" -> app.switcher.close(true);
            case "switcher-key" -> TestHooks.pressKeys(app.switcher.stage.getScene(), need(r, "spec"));
            case "window-name" -> { var w = window(app, r); w.name = r.path("name").asText("").isBlank() ? null : r.get("name").asText(); w.look(); }
            case "window-colour" -> {
                var w = window(app, r);
                if (!r.has("colour")) throw new IllegalArgumentException("missing colour (#rrggbb, or empty / none for no colour)");
                var c = r.get("colour").asText("").strip();
                w.colour = c.isEmpty() || c.equalsIgnoreCase(WindowLook.NONE) ? null : c;
                w.look();
            }
            case "key" -> TestHooks.pressKeys(window(app, r).scene, need(r, "spec"));
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

    /** The window raised, out of the taskbar if it was minimised: what a launch does when it joins a running Tabs. */
    static void front(TabsWindow w) {
        if (w.stage.isIconified()) w.stage.setIconified(false);
        w.stage.toFront();
    }

    static ArrayNode list(TabsApp app) {
        var windows = JSON.createArrayNode();
        for (var w : app.windows) {
            var wn = windows.addObject().put("index", app.windows.indexOf(w)).put("focused", w.stage.isFocused())
                    .put("name", w.windowLabel()).put("colour", w.colour == null ? WindowLook.NONE : w.colour);
            var tabs = wn.putArray("tabs");
            for (var t : w.tabs) {
                var n = tabs.addObject().put("id", t.id).put("index", w.tabs.indexOf(t)).put("title", t.shown());
                if (t.separator) { n.put("separator", true).put("collapsed", t.collapsed).put("hideAsleep", t.hideAsleep); continue; }
                if (w.folded(t)) n.put("folded", true);
                n.put("folder", t.where()).put("live", t.live()).put("selected", t == w.selected()).put("color", t.color.get()).put("attention", t.attention());
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

    /** The tab the caller runs in: the tab named by `tab` (the caller's FXTERM_TABS_TAB, which every process below a tab
     *  inherits), else the tab whose shell is an ancestor of the calling process, else the tab whose claude session is the
     *  caller's CLAUDE_CODE_SESSION_ID. */
    static TerminalTab caller(TabsApp app, JsonNode r) {
        var id = r.path("tab").asText("");
        var named = app.allTabs().stream().filter(t -> !t.separator && t.id.equals(id)).findFirst();
        if (named.isPresent()) return named.get();
        var shells = app.allTabs().stream().filter(t -> !t.separator && t.live()).collect(java.util.stream.Collectors.toMap(t -> t.connector.process.pid(), t -> t, (a, b) -> a));
        for (var h = ProcessHandle.of(r.path("pid").asLong(0)); h.isPresent(); h = h.get().parent()) {
            var t = shells.get(h.get().pid());
            if (t != null) return t;
        }
        var session = r.path("session").asText("");
        for (var t : shells.values()) if (!session.isEmpty() && ClaudeProbe.of(t.connector.process.pid()).map(c -> session.equals(c.sessionId)).orElse(false)) return t;
        throw new IllegalArgumentException("no tab found for the caller: run it from inside a Tabs tab, or give id=");
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
