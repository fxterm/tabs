package fxterm.tabs;

import module java.base;

/** A tab as written to the session file: enough to show it asleep and to wake it later. */
public final class SavedTab {

    public String title = "shell", cwd, claudeSessionId, customTitle, color;
    /** The shell a plain tab ran (cmd.exe, powershell.exe, pwsh.exe, bash.exe, wsl.exe); null means cmd.exe. */
    public String shell;
    public List<String> claudeArgs = new ArrayList<>();
    /** For a plain shell: the claude it ran last, ended before fxterm-tabs closed; offered on waking. */
    public SavedTab lastClaude;
    public long savedAt = System.currentTimeMillis();
    /** The tab's text when it was saved: up to SCREEN_LINES lines of scrollback plus the screen, trailing blanks cut. */
    public String screen;

    static final int SCREEN_LINES = 1000;

    static final Set<String> SHELLS = Set.of("cmd.exe", "powershell.exe", "pwsh.exe", "bash.exe", "wsl.exe");

    boolean claude() {
        return claudeSessionId != null;
    }

    /** cmd.exe stays the parent, as in a fresh tab, so leaving claude drops back to a prompt rather than closing it.
     *  A plain tab gets its shell back, without whatever command it was started with. */
    List<String> command() {
        if (!claude()) return new ArrayList<>(List.of(shell != null ? shell : "cmd.exe"));
        var argv = new ArrayList<>(List.of("cmd.exe", "/k", "claude"));
        argv.addAll(claudeArgs);
        argv.addAll(List.of("--resume", claudeSessionId));
        return argv;
    }

    /** The command as it would be typed, for the wake confirmation. */
    String commandLine() {
        return command().stream().map(a -> a.contains(" ") ? "\"" + a + "\"" : a).collect(Collectors.joining(" "));
    }

    /** The line that brings it back in any cmd window: cd into its folder, then claude --resume with its flags, or
     *  its shell. */
    String resumeLine() {
        var dir = cwd != null ? cwd : System.getProperty("user.home");
        List<String> what = claude() ? command().subList(2, command().size()) : command();
        return "cd /d \"" + dir + "\" & " + what.stream().map(a -> a.contains(" ") ? "\"" + a + "\"" : a).collect(Collectors.joining(" "));
    }

    /** The shell of a started command line, when it is one of the known shells. */
    static String shellOf(List<String> argv) {
        if (argv.isEmpty()) return null;
        var exe = Path.of(argv.getFirst()).getFileName().toString().toLowerCase(Locale.ROOT);
        if (!exe.endsWith(".exe")) exe += ".exe";
        return SHELLS.contains(exe) ? exe : null;
    }
}
