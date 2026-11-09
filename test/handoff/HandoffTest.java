package fxterm.tabs;

import module java.base;
import com.fasterxml.jackson.databind.*;

/** PRP 03 (and 12, item 3): a running Tabs listens on no TCP port, and a second process reaches it only through the control socket.
 *  Starts an offscreen Tabs on its own state file with user.home in a temp folder (a running Tabs is never touched), then
 *  checks: no TCP listener for its process; `open`, `run` and a bare launch through Main join it; `ctl rename` carries a bell
 *  and an accented letter from the command line, and quotes from stdin, unchanged.
 *  Uses only the jar under test (through its control socket and Main), so it runs against any build.
 *  Exit 0: all passed, 1: a check failed, 2: Tabs did not start. Usage: HandoffTest <the jar under test> */
public class HandoffTest {

    static final ObjectMapper JSON = new ObjectMapper();
    static final String BELL = new String(Character.toChars(0x1F514));
    static int failed = 0;
    static Path home, state;
    static String jar;

    static void check(boolean ok, String what) {
        System.out.println((ok ? "PASS " : "FAIL ") + what);
        if (!ok) failed++;
    }

    static JsonNode ctl(String json) throws IOException {
        try (var ch = SocketChannel.open(UnixDomainSocketAddress.of(Control.socket(state.toString())))) {
            ch.write(ByteBuffer.wrap((json + "\n").getBytes(StandardCharsets.UTF_8)));
            return JSON.readTree(new BufferedReader(new InputStreamReader(Channels.newInputStream(ch), StandardCharsets.UTF_8)).readLine());
        }
    }

    /** Main of the jar under test as a child process, started outside any Tabs tab (no FXTERM_TABS_TAB). */
    static ProcessBuilder java(String... args) {
        var cmd = new ArrayList<>(List.of(Path.of(System.getProperty("java.home"), "bin", "java").toString(), "-Duser.home=" + home, "-cp", jar, "fxterm.tabs.Main"));
        cmd.addAll(List.of(args));
        var pb = new ProcessBuilder(cmd).directory(home.toFile()).redirectError(ProcessBuilder.Redirect.DISCARD);
        pb.environment().remove("FXTERM_TABS_TAB");
        pb.environment().remove("FXTERM_TABS_INSTANCE");
        return pb;
    }

    /** Runs Main with the arguments; `stdin` is written to it when not null. Returns its exit code, the output is left in `out`. */
    static String out;

    static int launch(String stdin, String... args) throws Exception {
        var p = java(args).start();
        if (stdin != null) p.getOutputStream().write(stdin.getBytes(StandardCharsets.UTF_8));
        p.getOutputStream().close();
        out = new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8).strip();
        return p.waitFor(30, TimeUnit.SECONDS) ? p.exitValue() : -1;
    }

    static JsonNode tabs() throws IOException { return ctl("{\"cmd\":\"list\"}").path("windows").get(0).path("tabs"); }

    static String title(String id) throws IOException {
        for (var t : tabs()) if (t.path("id").asText().equals(id)) return t.path("title").asText();
        return "(no tab " + id + ")";
    }

    /** The local addresses a process listens on over TCP, as netstat sees them. */
    static List<String> listeners(long pid) throws Exception {
        var p = new ProcessBuilder("netstat", "-ano", "-p", "TCP").redirectErrorStream(true).start();
        var text = new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        p.waitFor();
        return text.lines().map(String::strip).filter(l -> l.contains("LISTENING") && l.endsWith(" " + pid)).toList();
    }

    public static void main(String[] a) throws Exception {
        jar = Path.of(a[0]).toAbsolutePath().toString();
        home = Files.createTempDirectory("fxh");
        state = home.resolve("state.json");
        System.setProperty("user.home", home.toString());
        var app = java("--offscreen", "true", "--state", state.toString(), "--serve", "true", "--restore", "false").start();
        try {
            var sock = Control.socket(state.toString());
            for (var i = 0; i < 300 && !Control.alive(sock); i++) Thread.sleep(200);
            if (!Control.alive(sock)) { System.out.println("HARNESS: Tabs did not start"); System.exit(2); }
            noTcp(app.pid());
            launches();
            arguments();
        } catch (Exception e) {
            check(false, "stopped early: " + e);
        } finally {
            app.destroyForcibly();
            try (var s = Files.walk(home)) { s.sorted(Comparator.reverseOrder()).forEach(p -> p.toFile().delete()); } catch (IOException ignored) { }
        }
        System.out.println(failed == 0 ? "ALL PASSED" : failed + " FAILED");
        System.exit(failed == 0 ? 0 : 1);
    }

    static void noTcp(long pid) throws Exception {
        var l = listeners(pid);
        check(l.isEmpty(), "the running Tabs listens on no TCP port" + (l.isEmpty() ? "" : ": " + l));
    }

    static void launches() throws Exception {
        var n = tabs().size();
        check(launch(null, "open", home.toString()) == 0, "open: exits 0 (joined the running window)");
        check(tabs().size() == n + 1, "open: the running window got a tab");
        check(launch(null, "run", "cmd", "/k", "rem") == 0, "run: exits 0 (joined the running window)");
        check(tabs().size() == n + 2, "run: the running window got a tab");
        check(launch(null) == 0, "bare launch: exits 0 (joined, no window of its own)");
        var shown = false;
        for (var e : ctl("{\"cmd\":\"events\",\"n\":100}").path("events")) shown |= e.path("request").path("cmd").asText().equals("show");
        check(shown, "bare launch: the running window was asked to come forward");
        check(tabs().size() == n + 2, "bare launch: no tab was added");
    }

    static void arguments() throws Exception {
        var id = tabs().get(0).path("id").asText();
        var rename = "{\"cmd\":\"rename\",\"id\":\"" + id + "\",\"title\":";
        var plain = BELL + " é on the command line";
        check(launch(null, "ctl", "--state=" + state, "rename", "id=" + id, "title=" + plain) == 0 && title(id).equals(plain),
                "ctl rename title=<bell, accent, spaces>: reaches the tab unchanged: " + title(id));
        check(launch(null, "ctl", "--state=" + state, "rename", "id=" + id, "title=My", "own", "tab") == 0 && title(id).equals("My own tab"),
                "ctl rename title=My own tab, unquoted: the words after the first space are kept: " + title(id));
        var quoted = BELL + " say \"hello there\" é";
        check(launch(rename + JSON.writeValueAsString(quoted) + "}", "ctl", "--state=" + state, "-") == 0 && title(id).equals(quoted),
                "ctl - (request on stdin): bell, quotes and accent reach the tab unchanged: " + title(id));
        check(launch(null, "ctl", "--state=" + state, "list") == 0 && out.contains("\\uD83D\\uDD14") && out.chars().allMatch(c -> c < 0x80),
                "ctl list prints the bell as a JSON escape, so any console code page shows it intact");
    }
}
