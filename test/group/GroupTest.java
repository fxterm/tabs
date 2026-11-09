package fxterm.tabs;

import module java.base;
import com.fasterxml.jackson.databind.JsonNode;

/** PRP 24: a launch into a tab group. Starts an offscreen Tabs on its own state and control socket (nothing of a running
 *  Tabs is touched), then launches `run`/`open --group` through Main the way proj does, and reads the order back with ctl list.
 *  Exit 0: all passed, 1: a check failed, 2: Tabs did not start. Usage: GroupTest <the jar under test> */
public class GroupTest {

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
            return Control.JSON.readTree(new BufferedReader(new InputStreamReader(Channels.newInputStream(ch), StandardCharsets.UTF_8)).readLine());
        }
    }

    /** Main of the jar under test, as a child process; `tab` is the FXTERM_TABS_TAB it inherits (none when null). */
    static ProcessBuilder java(String tab, String... args) {
        var cmd = new ArrayList<>(List.of(Path.of(System.getProperty("java.home"), "bin", "java").toString(), "-Duser.home=" + home, "-cp", jar, "fxterm.tabs.Main"));
        cmd.addAll(List.of(args));
        var pb = new ProcessBuilder(cmd).directory(home.toFile()).redirectErrorStream(true);
        pb.environment().remove("FXTERM_TABS_TAB");
        if (tab != null) pb.environment().put("FXTERM_TABS_TAB", tab);
        return pb;
    }

    static JsonNode tabs() throws IOException {
        var w = ctl("{\"cmd\":\"list\"}").path("windows");
        return w.isEmpty() ? w : w.get(0).path("tabs");
    }

    /** The ids in list order, a separator as "[label]". */
    static List<String> layout() throws IOException {
        var out = new ArrayList<String>();
        for (var t : tabs()) out.add(t.path("separator").asBoolean() ? "[" + t.path("title").asText() + "]" : t.path("id").asText());
        return out;
    }

    /** Launches through Main, waits for `rows` more rows in the running window, and returns the new real tab's id. */
    static String launch(String tab, int rows, String... args) throws Exception {
        var before = layout();
        var p = java(tab, args).redirectOutput(ProcessBuilder.Redirect.DISCARD).start();
        check(p.waitFor(30, TimeUnit.SECONDS) && p.exitValue() == 0, String.join(" ", args) + ": exits 0 (joined the running window)");
        for (var i = 0; i < 100 && layout().size() < before.size() + rows; i++) Thread.sleep(100);
        return layout().stream().filter(x -> !before.contains(x) && !x.startsWith("[")).findFirst().orElse("(none)");
    }

    static String run(String tab, String... args) throws Exception {
        var p = java(tab, args).start();
        var out = new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8).strip();
        p.waitFor();
        return out.lines().reduce((x, y) -> y).orElse("{}");
    }

    static String sep(String label) throws IOException { return ctl("{\"cmd\":\"separator\",\"title\":\"" + label + "\"}").path("id").asText(); }

    static String tab(String title) throws IOException { return ctl("{\"cmd\":\"new\",\"title\":\"" + title + "\",\"command\":\"cmd /k rem\"}").path("id").asText(); }

    /** True when `x` sits directly before `y` in the list. */
    static boolean before(List<String> l, String x, String y) { return l.contains(x) && l.indexOf(x) + 1 == l.indexOf(y); }

    public static void main(String[] a) throws Exception {
        jar = Path.of(a[0]).toAbsolutePath().toString();
        home = Files.createTempDirectory("fxg");
        state = home.resolve("state.json");
        System.setProperty("user.home", home.toString());
        var app = java(null, "--offscreen", "true", "--state", state.toString(), "--serve", "true", "--restore", "false").start();
        try {
            for (var i = 0; i < 300 && !Control.alive(Control.socket(state.toString())); i++) Thread.sleep(200);
            if (!Control.alive(Control.socket(state.toString()))) { System.out.println("HARNESS: Tabs did not start"); System.exit(2); }
            attempt("groups", GroupTest::groups);
            attempt("whoami", GroupTest::whoami);
        } finally {
            app.destroyForcibly();
            try (var s = Files.walk(home)) { s.sorted(Comparator.reverseOrder()).forEach(p -> p.toFile().delete()); } catch (IOException ignored) { }
        }
        System.out.println(failed == 0 ? "ALL PASSED" : failed + " FAILED");
        System.exit(failed == 0 ? 0 : 1);
    }

    interface Scenario { void run() throws Exception; }

    static void attempt(String name, Scenario s) {
        try { s.run(); } catch (Exception e) { check(false, name + " stopped early: " + e); }
    }

    static String t0, a1, b1, aHead;

    static void groups() throws Exception {
        t0 = layout().getFirst();
        aHead = sep("A"); a1 = tab("a1"); sep("B"); b1 = tab("b1");
        System.out.println("start: " + layout());

        // an explicit group: the end of that group; two launches in a row keep their order
        var n1 = launch(null, 1, "run", "--group", "A", "cmd", "/k", "rem");
        var l = layout();
        check(before(l, a1, n1) && before(l, n1, "[B]"), "run --group A: at the end of A, before B: " + l);
        var n2 = launch(null, 1, "run", "--group", "A", "cmd", "/k", "rem");
        l = layout();
        check(before(l, a1, n1) && before(l, n1, n2) && before(l, n2, "[B]"), "second launch: after the first, launch order kept: " + l);

        // a group that does not exist is made at the end of the window
        var n3 = launch(null, 2, "run", "--group", "C", "cmd", "/k", "rem");
        l = layout();
        check(before(l, "[C]", n3) && l.getLast().equals(n3), "run --group C (no such group): label C made at the end, the tab under it: " + l);

        // no --group: the group of the tab that started the launch (FXTERM_TABS_TAB); an explicit --group still wins
        var n4 = launch(b1, 1, "run", "cmd", "/k", "rem");
        l = layout();
        check(before(l, b1, n4) && before(l, n4, "[C]"), "launch from a tab of B: at the end of B, before C: " + l);
        var n5 = launch(b1, 1, "run", "--group", "A", "cmd", "/k", "rem");
        l = layout();
        check(before(l, n2, n5) && before(l, n5, "[B]"), "--group A from a tab of B: A wins: " + l);

        // an id no tab has, or a tab in no group: as before, the end of the list
        var n6 = launch("zzzz", 1, "run", "cmd", "/k", "rem");
        check(layout().getLast().equals(n6), "unknown FXTERM_TABS_TAB: the end of the list, as today: " + layout());
        var n7 = launch(t0, 1, "run", "cmd", "/k", "rem");
        check(layout().getLast().equals(n7), "launch from a tab in no group: the end of the list, as today: " + layout());

        // open --group and ctl new group=
        var n8 = launch(null, 1, "open", "--group", "B", home.toString());
        l = layout();
        check(before(l, n4, n8) && before(l, n8, "[C]"), "open --group B <dir>: at the end of B: " + l);
        var n9 = ctl("{\"cmd\":\"new\",\"group\":\"A\",\"title\":\"viactl\",\"command\":\"cmd /k rem\"}").path("id").asText();
        l = layout();
        check(before(l, n5, n9) && before(l, n9, "[B]"), "ctl new group=A: at the end of A: " + l);

        // a folded group opens for the new tab, which is shown
        ctl("{\"cmd\":\"collapse\",\"id\":\"" + aHead + "\",\"on\":true}");
        var n10 = launch(null, 1, "run", "--group", "A", "cmd", "/k", "rem");
        var t = Stream.of(tabs()).flatMap(n -> StreamSupport.stream(n.spliterator(), false)).filter(n -> n.path("id").asText().equals(n10)).findFirst().orElseThrow();
        check(!t.path("folded").asBoolean() && t.path("selected").asBoolean(), "launch into a folded group: the group opened and the new tab is shown: " + t);
        check(StreamSupport.stream(tabs().spliterator(), false).noneMatch(n -> n.path("id").asText().equals(aHead) && n.path("collapsed").asBoolean()), "launch into a folded group: the label A is no longer folded");

        // a group hiding its hibernated tabs: the new tab (live, shown) is not hidden
        ctl("{\"cmd\":\"hibernate\",\"id\":\"" + a1 + "\"}");
        ctl("{\"cmd\":\"hideasleep\",\"id\":\"" + aHead + "\",\"on\":true}");
        var n11 = launch(null, 1, "run", "--group", "A", "cmd", "/k", "rem");
        t = Stream.of(tabs()).flatMap(n -> StreamSupport.stream(n.spliterator(), false)).filter(n -> n.path("id").asText().equals(n11)).findFirst().orElseThrow();
        check(!t.path("folded").asBoolean() && t.path("selected").asBoolean(), "launch into a group hiding hibernated tabs: the new tab is in sight: " + t);
    }

    static void whoami() throws Exception {
        ctl("{\"cmd\":\"select\",\"id\":\"" + b1 + "\"}");
        ctl("{\"cmd\":\"send\",\"id\":\"" + b1 + "\",\"text\":\"echo ENV=%FXTERM_TABS_TAB%.\",\"enter\":true}");
        var seen = false;
        for (var i = 0; i < 50 && !seen; i++) { Thread.sleep(200); seen = ctl("{\"cmd\":\"buffer\",\"id\":\"" + b1 + "\"}").path("text").asText().contains("ENV=" + b1 + "."); }
        check(seen, "the shell of a tab has FXTERM_TABS_TAB=<its id>");

        var out = run(b1, "ctl", "--state=" + state, "whoami");
        var w = Control.JSON.readTree(out);
        check(w.path("ok").asBoolean() && w.path("id").asText().equals(b1) && w.path("window").asInt(-1) == 0 && w.path("group").asText("null").equals("B") && w.has("folder"),
                "ctl whoami inside a tab of B: id, window, group, folder: " + out);
        out = run(t0, "ctl", "--state=" + state, "whoami");
        w = Control.JSON.readTree(out);
        check(w.path("ok").asBoolean() && w.path("id").asText().equals(t0) && w.has("group") && w.get("group").isNull(), "ctl whoami inside a tab in no group: group is null: " + out);
        check(!ctl("{\"cmd\":\"whoami\",\"tab\":\"zzzz\"}").path("ok").asBoolean(), "ctl whoami with an unknown tab id: an error, not a guess");
    }
}
