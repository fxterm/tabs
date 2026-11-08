package fxterm.tabs;

import module java.base;

/** Does a launch join the running window when the registration file is stale, and does a window remove its registration on exit?
 *  Runs with user.home pointed at a temp folder, so the control socket and instance.json are the test's own and a running Tabs is never touched.
 *  Compiled together with Control, Instance, Main and Request by run.sh (the package-private statics are used directly).
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

    /** instance.json as a window that has exited would have left it: a port nobody listens on. */
    static void writeStaleInstance() throws IOException {
        int dead;
        try (var s = new ServerSocket(0, 1, InetAddress.getLoopbackAddress())) { dead = s.getLocalPort(); }
        Files.createDirectories(Instance.FILE.getParent());
        Files.writeString(Instance.FILE, "{\"port\":" + dead + ",\"token\":\"stale\",\"pid\":1}");
    }

    public static void main(String[] a) throws Exception {
        if (a.length > 0 && a[0].equals("serve")) { serveChild(); return; }

        var home = Files.createTempDirectory("fxj");
        System.setProperty("user.home", home.toString());   // before Instance is loaded: its FILE is fixed at class init
        try {
            var sock = Control.socket(null);
            var open = request("open", home.toString());

            // 1. no window answers anywhere: a launch is not joined, it would start its own window
            writeStaleInstance();
            check(!Instance.forward(open), "stale instance.json: forward fails");
            check(!Control.join(open), "no control socket: join fails");

            // 2. a running window answers the control socket
            try (var fake = new Fake(sock)) {
                check(!Instance.forward(open), "stale instance.json, window running: forward still fails");
                check(Control.join(open), "open: join succeeds");
                var n = Instance.JSON.readTree(fake.seen.getFirst());
                check(n.path("cmd").asText().equals("new") && n.path("dir").asText().equals(home.toString()) && !n.has("command"),
                        "open: the window got `new` with the launch folder and no command: " + fake.seen.getFirst());

                check(Control.join(request("run", home.toString(), "cmd", "/k", "C:\\a b\\x.cmd")), "run: join succeeds");
                n = Instance.JSON.readTree(fake.seen.get(1));
                check(n.path("command").asText().equals("cmd /k \"C:\\a b\\x.cmd\""), "run: the command keeps its quoting: " + fake.seen.get(1));

                int before = fake.seen.size();
                check(!Control.join(request("show", home.toString())), "bare launch: not joined");
                check(fake.seen.size() == before, "bare launch: the window was not asked for anything");

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
                    check(done && p.exitValue() == 0, "Main run with a stale instance.json: exits 0 (joined, no window of its own)");
                    check(fake.seen.size() == 1 && Instance.JSON.readTree(fake.seen.getFirst()).path("command").asText().equals("cmd /k marker"),
                            "Main run: the running window opened the tab: " + fake.seen);
                } finally {
                    p.destroyForcibly();   // if the join failed, Main opened a window of its own: do not leave it
                }
            }

            // 4. a window removes its registration when it exits, but only while the file still names it
            Files.deleteIfExists(Instance.FILE);
            check(exitedWindowLeavesFile(home, false) == false, "window exit: instance.json removed");
            check(exitedWindowLeavesFile(home, true), "window exit: a registration taken over by another window is left alone");
        } finally {
            try (var s = Files.walk(home)) { s.sorted(Comparator.reverseOrder()).forEach(p -> p.toFile().delete()); } catch (IOException ignored) { }
        }
        System.out.println(failed == 0 ? "ALL PASSED" : failed + " FAILED");
        System.exit(failed == 0 ? 0 : 1);
    }

    /** Starts a window-like child (Instance.serve), lets it exit, and says whether instance.json is still there afterwards. */
    static boolean exitedWindowLeavesFile(Path home, boolean takenOver) throws Exception {
        var p = new ProcessBuilder(Path.of(System.getProperty("java.home"), "bin", "java").toString(), "-Duser.home=" + home,
                "-cp", System.getProperty("java.class.path"), "fxterm.tabs.JoinTest", "serve").redirectErrorStream(true).start();
        try {
            var in = new BufferedReader(new InputStreamReader(p.getInputStream(), StandardCharsets.UTF_8));
            String line;
            while ((line = in.readLine()) != null && !line.equals("ready")) { }
            if (line == null) throw new IllegalStateException("the serve child did not start");
            if (!Files.exists(Instance.FILE)) throw new IllegalStateException("the serve child wrote no instance.json");
            if (takenOver) Files.writeString(Instance.FILE, "{\"port\":1,\"token\":\"other\",\"pid\":1}");
            p.getOutputStream().close();   // the child reads stdin and ends when it closes
            if (!p.waitFor(20, TimeUnit.SECONDS)) throw new IllegalStateException("the serve child did not exit");
            return Files.exists(Instance.FILE);
        } finally {
            p.destroyForcibly();
        }
    }

    static void serveChild() throws Exception {
        Instance.serve(r -> { });
        System.out.println("ready");
        System.out.flush();
        System.in.read();
    }
}
