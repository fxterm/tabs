package fxterm.tabs;

import module java.base;

/** PRP 20: the default folder (setting, precedence, missing folder, re-read on change), a bare launch taking no folder of its own,
 *  and "no colour" kept as "none" in the saved window state. Runs with user.home in a temp folder; no window is shown.
 *  Exit 0: all passed, 1: a check failed. */
public class SettingsTest {

    static int failed = 0;

    static void check(boolean ok, String what) {
        System.out.println((ok ? "PASS " : "FAIL ") + what);
        if (!ok) failed++;
    }

    public static void main(String[] a) throws Exception {
        var home = Files.createTempDirectory("fxs");
        System.setProperty("user.home", home.toString());   // before WindowState is loaded: its FILE is fixed at class init
        try {
            var work = Files.createDirectories(home.resolve("work")).toString();
            var other = Files.createDirectories(home.resolve("other")).toString();
            DefaultFolder.init(true);

            check(DefaultFolder.get() == null && DefaultFolder.pick(null) == null, "nothing set: no default folder (home applies)");
            check("x".equals(DefaultFolder.pick("x")), "nothing set: an explicit folder is used");

            DefaultFolder.set(work);
            check(work.equals(DefaultFolder.pick(null)), "set: a tab with no folder gets the default folder");
            check(other.equals(DefaultFolder.pick(other)), "set: an explicit folder wins over the default");
            check(Files.exists(DefaultFolder.FILE), "set: folder.json written");

            var json = "{\"dir\":" + WindowState.JSON.writeValueAsString(other) + "}";
            Files.writeString(DefaultFolder.FILE, json);
            Files.setLastModifiedTime(DefaultFolder.FILE, java.nio.file.attribute.FileTime.fromMillis(System.currentTimeMillis() + 5000));
            check(other.equals(DefaultFolder.get()), "folder.json changed on disk: re-read without a restart");

            var gone = home.resolve("gone").toString();
            DefaultFolder.set(gone);
            check(DefaultFolder.get() == null && gone.equals(DefaultFolder.saved()), "saved folder missing: falls back to home, setting kept");

            DefaultFolder.set("  ");
            check(DefaultFolder.saved() == null && DefaultFolder.pick(null) == null, "blank: cleared, back to the home folder");

            // a bare launch has no folder of its own; a launch that named one keeps it
            check(Request.parse(List.of()).cwd == null, "bare launch: no folder (the default folder applies)");
            check(System.getProperty("user.dir").equals(Request.parse(List.of("open")).cwd), "open: the caller's folder is kept");
            check(Path.of(System.getProperty("user.dir")).resolve("sub").toString().equals(Request.parse(List.of("open", "sub")).cwd), "open sub: that folder is kept");

            // no colour is "none" in windows.json and survives a round trip; a null stays null (a window saved before this)
            var s = new WindowState();
            s.colour = "none";
            var back = WindowState.JSON.readValue(WindowState.JSON.writeValueAsString(s), WindowState.class);
            check("none".equals(back.colour), "no colour: \"none\" survives the saved state");
            check(WindowState.JSON.readValue("{}", WindowState.class).colour == null, "old state without a colour: null (a free colour is chosen)");
        } finally {
            try (var s = Files.walk(home)) { s.sorted(Comparator.reverseOrder()).forEach(p -> p.toFile().delete()); } catch (IOException ignored) { }
        }
        System.out.println(failed == 0 ? "ALL PASSED" : failed + " FAILED");
        System.exit(failed == 0 ? 0 : 1);
    }
}
