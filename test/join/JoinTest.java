package fxterm.tabs;

import module java.base;

/** Does a launch join the running window through the control socket, is a stale socket file ignored, and does a window remove its socket on exit?
 *  Runs with user.home pointed at a temp folder, so the control socket is the test's own and a running Tabs is never touched.
 *  Compiled together with Control, CtlArgs, WinArgs, Main and Request by run.sh (the package-private statics are used directly).
 *  Exit 0: all passed, 1: a check failed. */
public class JoinTest {

    static int failed = 0;

    static void check(boolean ok, String what) {
        System.out.println((ok ? "PASS " : "FAIL ") + what);
        if (!ok) failed++;
    }

    /** A stand-in for a running window: answers the control socket, remembers each request line. */
    static final class Fake implements AutoCloseable {
        final ServerSocketChannel server;
        final Path sock;
        final List<String> seen = new CopyOnWriteArrayList<>();
        volatile String reply = "{\"ok\":true}";

        Fake(Path sock) throws IOException {
            this.sock = sock;
            Files.createDirectories(sock.getParent());
            server = ServerSocketChannel.open(StandardProtocolFamily.UNIX);
            server.bind(UnixDomainSocketAddress.of(sock));
            Thread.ofPlatform().daemon().start(() -> {
                while (true) {
                    try (var ch = server.accept()) {
                        var line = new BufferedReader(new InputStreamReader(Channels.newInputStream(ch), StandardCharsets.UTF_8)).readLine();
                        if (line == null) continue;   // a liveness probe (Control.alive) that sends nothing
                        seen.add(line);
                        ch.write(ByteBuffer.wrap((reply + "\n").getBytes(StandardCharsets.UTF_8)));
                    } catch (IOException e) {
                        return;
                    }
                }
            });
        }

        @Override public void close() throws IOException {
            server.close();
            Files.deleteIfExists(sock);
        }
    }

    static Request request(String kind, String cwd, String... argv) {
        var r = new Request();
        r.kind = kind;
        r.cwd = cwd;
        r.argv = new ArrayList<>(List.of(argv));
        return r;
    }

    /** The socket file a window that was killed would have left: bound once, nobody listening any more. */
    static void writeStaleSocket(Path sock) throws IOException {
        Files.createDirectories(sock.getParent());
        try (var s = ServerSocketChannel.open(StandardProtocolFamily.UNIX)) { s.bind(UnixDomainSocketAddress.of(sock)); }
        if (!Files.exists(sock)) throw new IllegalStateException("closing a bound Unix socket removed its file: the stale case cannot be set up");
    }

    public static void main(String[] a) throws Exception {
        if (a.length > 0 && a[0].equals("serve")) { serveChild(); return; }

        var home = Files.createTempDirectory("fxj");
        System.setProperty("user.home", home.toString());
        try {
            var sock = Control.socket(null);
            var open = request("open", home.toString());

            // 1. no window answers: a launch is not joined, it would start its own window (also with a stale socket file)
            check(!Control.join(open), "no control socket: join fails");
            writeStaleSocket(sock);
            check(!Control.join(open), "stale socket file, nobody listening: join fails");
            Files.deleteIfExists(sock);

            // 2. a running window answers the control socket
            try (var fake = new Fake(sock)) {
                check(Control.join(open), "open: join succeeds");
                var n = Control.JSON.readTree(fake.seen.getFirst());
                check(n.path("cmd").asText().equals("new") && n.path("dir").asText().equals(home.toString()) && !n.has("command") && n.path("front").asBoolean(),
                        "open: the window got `new` with the launch folder, no command, and front: " + fake.seen.getFirst());

                check(Control.join(request("run", home.toString(), "cmd", "/k", "C:\\a b\\x.cmd")), "run: join succeeds");
                n = Control.JSON.readTree(fake.seen.get(1));
                check(n.path("command").asText().equals("cmd /k \"C:\\a b\\x.cmd\""), "run: the command keeps its quoting: " + fake.seen.get(1));
                check(n.path("argv").toString().equals("[\"cmd\",\"/k\",\"C:\\\\a b\\\\x.cmd\"]"), "run: the words travel as they are: " + n.path("argv"));

                check(Control.join(request("show", home.toString())), "bare launch: join succeeds");
                check(Control.JSON.readTree(fake.seen.get(2)).path("cmd").asText().equals("show"), "bare launch: the window was asked to show itself: " + fake.seen.get(2));

                fake.reply = "{\"ok\":false,\"error\":\"no\"}";
                check(!Control.join(open), "window answers ok:false: join fails");
                fake.reply = "{\"ok\":true}";

                // 3. Main itself, started like proj starts it: it must join and exit 0 instead of opening a window
                fake.seen.clear();
                var p = new ProcessBuilder(Path.of(System.getProperty("java.home"), "bin", "java").toString(), "-Duser.home=" + home,
                        "-cp", System.getProperty("java.class.path"), "fxterm.tabs.Main", "run", "cmd", "/k", "marker")
                        .directory(home.toFile()).redirectErrorStream(true).redirectOutput(ProcessBuilder.Redirect.DISCARD).start();
                try {
                    boolean done = p.waitFor(30, TimeUnit.SECONDS);
                    check(done && p.exitValue() == 0, "Main run: exits 0 (joined, no window of its own)");
                    check(fake.seen.size() == 1 && Control.JSON.readTree(fake.seen.getFirst()).path("command").asText().equals("cmd /k marker"),
                            "Main run: the running window opened the tab: " + fake.seen);
                } finally {
                    p.destroyForcibly();   // if the join failed, Main opened a window of its own: do not leave it
                }
            }

            // 4. a window removes its socket when it exits
            check(!exitedWindowLeavesSocket(home), "window exit: the control socket file is removed");
        } finally {
            try (var s = Files.walk(home)) { s.sorted(Comparator.reverseOrder()).forEach(p -> p.toFile().delete()); } catch (IOException ignored) { }
        }
        System.out.println(failed == 0 ? "ALL PASSED" : failed + " FAILED");
        System.exit(failed == 0 ? 0 : 1);
    }

    /** Starts a window-like child (Control.serve), lets it exit, and says whether its socket file is still there afterwards. */
    static boolean exitedWindowLeavesSocket(Path home) throws Exception {
        var p = new ProcessBuilder(Path.of(System.getProperty("java.home"), "bin", "java").toString(), "-Duser.home=" + home,
                "-cp", System.getProperty("java.class.path"), "fxterm.tabs.JoinTest", "serve").redirectErrorStream(true).start();
        try {
            var in = new BufferedReader(new InputStreamReader(p.getInputStream(), StandardCharsets.UTF_8));
            String line;
            while ((line = in.readLine()) != null && !line.equals("ready")) { }
            if (line == null) throw new IllegalStateException("the serve child did not start");
            if (!Control.alive(Control.socket(null))) throw new IllegalStateException("the serve child opened no control socket");
            p.getOutputStream().close();   // the child reads stdin and ends when it closes
            if (!p.waitFor(20, TimeUnit.SECONDS)) throw new IllegalStateException("the serve child did not exit");
            return Files.exists(Control.socket(null));
        } finally {
            p.destroyForcibly();
        }
    }

    static void serveChild() throws Exception {
        Control.serve(null, null);   // no window behind it: nothing sends it a request
        System.out.println("ready");
        System.out.flush();
        System.in.read();
    }
}
