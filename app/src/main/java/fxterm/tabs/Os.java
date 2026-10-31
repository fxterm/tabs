package fxterm.tabs;

import module java.base;

/** What differs between Windows and macOS: the default shell and font, how another process's RAM and folder are read,
 *  how a file or link is opened. Everything else in Tabs is the same on both. */
public final class Os {

    static final String NAME = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
    public static final boolean WIN = NAME.startsWith("win"), MAC = NAME.contains("mac");


    /** Key names as the platform writes them: on a Mac Ctrl+Shift+E reads ⌘⇧E (the Mac uses Cmd for Tabs' own keys). */
    static String keys(String text) {
        return MAC ? text.replace("Ctrl+Shift+", "⌘⇧").replace("Ctrl+", "⌘") : text;
    }

    /** The shell for a new tab, as a command line. */
    static String shell() {
        return WIN ? "cmd.exe" : shellPath() + " -l";
    }

    static String shellPath() {
        var s = System.getenv("SHELL");
        return s != null && Files.isExecutable(Path.of(s)) ? s : "/bin/zsh";
    }

    static String family() {
        return WIN ? "Consolas" : "Menlo";
    }

    /** Tabby's configuration file, where its colours and font size are read from. */
    static Path tabbyConfig() {
        var home = System.getProperty("user.home");
        return WIN ? Path.of(System.getenv().getOrDefault("APPDATA", ""), "tabby", "config.yaml")
                : MAC ? Path.of(home, "Library", "Application Support", "tabby", "config.yaml")
                : Path.of(home, ".config", "tabby", "config.yaml");
    }

    /** One word of a shell command line, quoted for sh/zsh/bash when it needs it. */
    static String quote(String a) {
        return a.matches("[\\w@%+=:,./-]+") ? a : "'" + a.replace("'", "'\\''") + "'";
    }

    /** Standard output of a command, "" when it cannot run. */
    static String out(String... argv) {
        try {
            var p = new ProcessBuilder(argv).redirectErrorStream(true).start();
            var text = new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            p.waitFor(15, TimeUnit.SECONDS);
            return text;
        } catch (IOException | InterruptedException e) {
            return "";
        }
    }

    /** Resident memory of the given processes together, in bytes (ps, one call). */
    static long rss(List<Long> pids) {
        if (pids.isEmpty()) return 0;
        var list = pids.stream().map(String::valueOf).collect(Collectors.joining(","));
        return out("ps", "-o", "rss=", "-p", list).lines().map(String::strip).filter(l -> l.matches("\\d+"))
                .mapToLong(Long::parseLong).sum() * 1024;
    }

    /** The current folder of a process, null when unknown. */
    static String cwdOf(long pid) {
        return out("lsof", "-a", "-d", "cwd", "-Fn", "-p", String.valueOf(pid)).lines()
                .filter(l -> l.startsWith("n")).map(l -> l.substring(1)).findFirst().orElse(null);
    }

    /** Memory that can be had without pushing anything out: free, inactive and speculative pages (vm_stat). */
    static long freeMb() {
        var text = out("vm_stat");
        var page = Pattern.compile("page size of (\\d+) bytes").matcher(text);
        var size = page.find() ? Long.parseLong(page.group(1)) : 16384;
        var pages = 0L;
        for (var key : List.of("Pages free", "Pages inactive", "Pages speculative")) {
            var m = Pattern.compile(key + ":\\s+(\\d+)").matcher(text);
            if (m.find()) pages += Long.parseLong(m.group(1));
        }
        return pages * size / (1024 * 1024);
    }

    private Os() { }
}
