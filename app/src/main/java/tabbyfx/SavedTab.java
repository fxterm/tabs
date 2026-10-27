package tabbyfx;

import module java.base;

/** A tab as written to the session file: enough to show it asleep and to wake it later. */
public final class SavedTab {

    public String title = "shell", cwd, claudeSessionId, customTitle, color;
    /** The shell a plain tab ran (cmd.exe, powershell.exe, pwsh.exe, bash.exe, wsl.exe); null means cmd.exe. */
    public String shell;
    public List<String> claudeArgs = new ArrayList<>();
    /** For a plain shell: the claude it ran last, ended before tabbly closed; offered on waking. */
    public SavedTab lastClaude;
    public long savedAt = System.currentTimeMillis();

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

    /** The shell of a started command line, when it is one of the known shells. */
    static String shellOf(List<String> argv) {
        if (argv.isEmpty()) return null;
        var exe = Path.of(argv.getFirst()).getFileName().toString().toLowerCase(Locale.ROOT);
        if (!exe.endsWith(".exe")) exe += ".exe";
        return SHELLS.contains(exe) ? exe : null;
    }
}
