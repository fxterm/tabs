package fxterm.tabs;

import module java.base;
import com.fasterxml.jackson.databind.JsonNode;

/** PRP 17: `ctl screen`. Starts an offscreen Tabs on its own state, socket and instance file (nothing of a running Tabs is
 *  touched) with the built-in theme, runs a tab whose program prints coloured text, and reads the screen back through the
 *  control socket: text, colours, bold, underline, inverse, a wide character's column, the cursor, and the cheap version check.
 *  Exit 0: all passed, 1: a check failed, 2: Tabs did not start. Usage: ScreenTest <the jar under test>
 *  With the first argument `emit` it is the program that runs inside the tab. */
public class ScreenTest {

    static int failed = 0;
    static Path home, state, instance;
    static String jar;

    static void check(boolean ok, String what) {
        System.out.println((ok ? "PASS " : "FAIL ") + what);
        if (!ok) failed++;
    }

    static JsonNode ctl(String json) throws IOException {
        try (var ch = SocketChannel.open(UnixDomainSocketAddress.of(Control.socket(state.toString())))) {
            ch.write(ByteBuffer.wrap((json + "\n").getBytes(StandardCharsets.UTF_8)));
            return Instance.JSON.readTree(new BufferedReader(new InputStreamReader(Channels.newInputStream(ch), StandardCharsets.UTF_8)).readLine());
        }
    }

    static JsonNode screen(String id, long since) throws IOException { return ctl("{\"cmd\":\"screen\",\"id\":\"" + id + "\"" + (since >= 0 ? ",\"since\":" + since : "") + "}"); }

    /** The first run (and its row) whose text starts with `start`, or null. */
    static JsonNode run(JsonNode screen, String start) {
        for (var line : screen.path("lines")) for (var r : line) if (r.path("t").asText().startsWith(start)) return r;
        return null;
    }

    static int rowOf(JsonNode screen, String start) {
        var y = 0;
        for (var line : screen.path("lines")) { for (var r : line) if (r.path("t").asText().startsWith(start)) return y; y++; }
        return -1;
    }

    /** The screen once it shows `start` and has stopped changing for 700 ms (a tab's startup output is still arriving before that). */
    static JsonNode waitFor(String id, String start) throws Exception {
        for (var i = 0; i < 300; i++) {
            var s = screen(id, -1);
            if (run(s, start) != null) {
                Thread.sleep(700);
                var quiet = screen(id, s.path("version").asLong());
                if (quiet.path("unchanged").asBoolean()) return s;
            }
            Thread.sleep(100);
        }
        return screen(id, -1);
    }

    /** The program that runs in the tab: prints styled text, then echoes each line typed to it in green. */
    static void emit() throws Exception {
        new ProcessBuilder("cmd", "/c", "chcp 65001 >nul").inheritIO().start().waitFor();
        var out = new PrintStream(new FileOutputStream(FileDescriptor.out), true, StandardCharsets.UTF_8);
        var e = "\u001b[";
        out.print(e + "2J" + e + "H");
        out.print(e + "1;31mRED-BOLD" + e + "0m plain\r\n");
        out.print(e + "38;2;200;100;50;48;2;10;20;30mTRUE" + e + "0m " + e + "4mUND" + e + "0m " + e + "7mINV" + e + "0m " + e + "3mITA" + e + "0m\r\n");
        out.print(e + "38;5;196mIDX256" + e + "0m\r\n");
        out.print(e + "31m中" + e + "0mX\r\n");
        out.print("READY\r\n");
        var in = new BufferedReader(new InputStreamReader(System.in));
        for (var line = in.readLine(); line != null; line = in.readLine()) out.print(e + "32mMORE " + line + e + "0m\r\n");
    }

    public static void main(String[] a) throws Exception {
        if (a[0].equals("emit")) { emit(); return; }
        jar = Path.of(a[0]).toAbsolutePath().toString();
        home = Files.createTempDirectory("fxs");
        state = home.resolve("state.json");
        instance = home.resolve("instance.json");
        System.setProperty("user.home", home.toString());
        var cmd = List.of(Path.of(System.getProperty("java.home"), "bin", "java").toString(), "-Duser.home=" + home, "-cp", jar, "fxterm.tabs.Main",
                "--offscreen", "true", "--state", state.toString(), "--serve", "true", "--restore", "false", "--theme", "builtin");
        var pb = new ProcessBuilder(cmd).directory(home.toFile()).redirectErrorStream(true).redirectOutput(ProcessBuilder.Redirect.DISCARD);
        pb.environment().put("FXTERM_TABS_INSTANCE", instance.toString());
        var app = pb.start();
        try {
            for (var i = 0; i < 300 && !Control.alive(Control.socket(state.toString())); i++) Thread.sleep(200);
            if (!Control.alive(Control.socket(state.toString()))) { System.out.println("HARNESS: Tabs did not start"); System.exit(2); }
            attempt("styled", ScreenTest::styled);
            attempt("version", ScreenTest::version);
            attempt("errors", ScreenTest::errors);
            attempt("asleep", ScreenTest::asleep);
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

    static String id;
    static JsonNode first;

    static void styled() throws Exception {
        var java = Path.of(System.getProperty("java.home"), "bin", "java").toString();
        var command = "\"" + java + "\" -cp \"" + System.getProperty("java.class.path") + "\" fxterm.tabs.ScreenTest emit";
        id = ctl(Instance.JSON.createObjectNode().put("cmd", "new").put("title", "colours").put("command", command).toString()).path("id").asText();
        first = waitFor(id, "READY");
        check(first.path("ok").asBoolean(), "screen answers ok: " + first.path("error").asText(""));
        check(first.path("cols").asInt() > 0 && first.path("rows").asInt() > 0, "cols and rows: " + first.path("cols") + " x " + first.path("rows"));
        check("#e8eaf2".equals(first.path("default").path("fg").asText()) && "#0f1324".equals(first.path("default").path("bg").asText()), "default colours of the theme: " + first.path("default"));
        var red = run(first, "RED-BOLD");
        check(red != null && "#cd3131".equals(red.path("fg").asText()) && red.path("b").asBoolean(), "RED-BOLD: palette red and bold: " + red);
        var plain = run(first, " plain");
        check(plain != null && !plain.has("fg") && !plain.has("bg") && !plain.has("b"), "' plain' has no style keys: " + plain);
        var rgb = run(first, "TRUE");
        check(rgb != null && "#c86432".equals(rgb.path("fg").asText()) && "#0a141e".equals(rgb.path("bg").asText()), "TRUE: true colour fg #c86432 on bg #0a141e: " + rgb);
        var und = run(first, "UND");
        check(und != null && und.path("u").asBoolean(), "UND underlined: " + und);
        var inv = run(first, "INV");
        check(inv != null && inv.path("inv").asBoolean() && "#0f1324".equals(inv.path("fg").asText()) && "#e8eaf2".equals(inv.path("bg").asText()), "INV: inverse, colours as painted (swapped): " + inv);
        var ita = run(first, "ITA");
        check(ita != null && ita.path("i").asBoolean(), "ITA italic: " + ita);
        var idx = run(first, "IDX256");
        check(idx != null && "#ff0000".equals(idx.path("fg").asText()), "IDX256: 256-colour 196 resolved to #ff0000: " + idx);
        var wide = run(first, "中");
        var x = run(first, "X");
        check(wide != null && "#cd3131".equals(wide.path("fg").asText()) && x != null && x.path("x").asInt() == wide.path("x").asInt() + 2, "wide character takes two columns, the next run starts 2 columns on: " + wide + " " + x);
        check(!first.toString().contains("") && !first.toString().contains("\\u0000"), "no wide-character filler or NUL in the text");
        var cursor = first.path("cursor");
        var ready = rowOf(first, "READY");
        check(cursor.path("y").asInt() == ready + 1 && cursor.path("visible").asBoolean(), "cursor on the row under READY (" + ready + "): " + cursor);
        check(first.path("lines").size() == first.path("rows").asInt(), "one line entry per row");
    }

    static void version() throws Exception {
        var v = first.path("version").asLong();
        var same = screen(id, v);
        check(same.path("unchanged").asBoolean() && same.path("version").asLong() == v && !same.has("lines"), "since=<version> with nothing new: unchanged, no lines: " + same);
        check(!screen(id, v - 1).path("unchanged").asBoolean() && screen(id, v - 1).has("lines"), "an older since gets the whole screen");
        var before = System.nanoTime();
        ctl("{\"cmd\":\"send\",\"id\":\"" + id + "\",\"text\":\"hello\",\"enter\":true}");
        JsonNode now = null;
        for (var i = 0; i < 200; i++) {
            now = screen(id, v);
            if (!now.path("unchanged").asBoolean() && run(now, "MORE hello") != null) break;
            Thread.sleep(20);
        }
        var ms = (System.nanoTime() - before) / 1_000_000;
        var more = run(now, "MORE hello");
        check(!now.path("unchanged").asBoolean() && now.path("version").asLong() > v, "after output the version is new: " + v + " -> " + now.path("version"));
        check(more != null && "#0dbc79".equals(more.path("fg").asText()), "the new line reached the answer, green: " + more);
        check(ms < 1500, "a change reached a poll within " + ms + " ms");
        check(now.path("cols").asInt() == first.path("cols").asInt() && now.path("rows").asInt() == first.path("rows").asInt(), "reading did not resize the tab");
        var again = screen(id, now.path("version").asLong());
        check(again.path("unchanged").asBoolean(), "and quiet again: unchanged");
        var latest = now.path("version").asLong();
        var t0 = System.nanoTime();
        for (var i = 0; i < 50; i++) screen(id, latest);
        var cheap = (System.nanoTime() - t0) / 50_000;
        t0 = System.nanoTime();
        for (var i = 0; i < 50; i++) screen(id, -1);
        var full = (System.nanoTime() - t0) / 50_000;
        check(cheap < 50_000 && full < 100_000, "a poll costs " + cheap + " us (since=), " + full + " us (whole screen), socket round trip included");
        var log = Files.readString(Path.of(state + ".events.jsonl"));
        check(log.contains("\"cmd\":\"send\"") && !log.contains("\"cmd\":\"screen\""), "polling leaves no trace in the event log (it would push out the useful events), other requests still do");
    }

    static void errors() throws Exception {
        check(!screen("nope", -1).path("ok").asBoolean(), "unknown tab: ok false");
        check(!ctl("{\"cmd\":\"screen\"}").path("ok").asBoolean(), "no id: ok false");
        var sep = ctl("{\"cmd\":\"separator\",\"title\":\"G\"}").path("id").asText();
        check(!screen(sep, -1).path("ok").asBoolean(), "a group label has no screen: ok false");
    }

    static void asleep() throws Exception {
        ctl("{\"cmd\":\"hibernate\",\"id\":\"" + id + "\"}");
        JsonNode s = null;
        for (var i = 0; i < 200; i++) {
            s = screen(id, -1);
            if (s.path("asleep").asBoolean()) break;
            Thread.sleep(100);
        }
        check(s.path("asleep").asBoolean() && s.path("ok").asBoolean(), "a hibernated tab answers asleep:true: " + s.path("asleep"));
        check(run(s, "READY") != null && run(s, "READY").size() == 2, "its saved text, unstyled (x and t only)");
        ctl("{\"cmd\":\"wake\",\"id\":\"" + id + "\"}");
        JsonNode w = null;
        for (var i = 0; i < 200; i++) {
            w = screen(id, -1);
            if (!w.path("asleep").asBoolean() && w.path("cursor").has("x")) break;
            Thread.sleep(100);
        }
        check(w.path("cursor").has("x") && w.path("version").asLong() != s.path("version").asLong(), "after wake: a live screen again, a new version: " + w.path("version"));
    }
}
