package fxterm.tabs;

import module java.base;

/** A tab as written to the session file: enough to show it asleep and to wake it later. */
public final class SavedTab {

    public String title = "shell", cwd, claudeSessionId, customTitle, color;
    /** The shell a plain tab ran (cmd.exe, powershell.exe, pwsh.exe, bash.exe, wsl.exe); null means cmd.exe. */
    public String shell;
    public List<String> claudeArgs = new ArrayList<>();
    /** A separator: no program, just a label (title) that groups the tabs under it. */
    public boolean separator;
    /** A separator whose group was folded away. */
    public boolean collapsed;
    /** The tab that was shown in its window when Tabs closed; shown again on the next start. */
    public boolean selected;
    /** Which window it was in (0 = the first). */
    public int window;
    /** For a plain shell: the claude it ran last, ended before fxterm-tabs closed; offered on waking. */
    public SavedTab lastClaude;
    public long savedAt = System.currentTimeMillis();
    /** The tab's text when it was saved: up to SCREEN_LINES lines of scrollback plus the screen, trailing blanks cut. */
    public String screen;

    static final int SCREEN_LINES = 1000;

    static final Set<String> SHELLS = Os.WIN ? Set.of("cmd.exe", "powershell.exe", "pwsh.exe", "bash.exe", "wsl.exe")
            : Set.of("zsh", "bash", "fish", "sh", "pwsh");

    boolean claude() {
        return claudeSessionId != null;
    }

    /** claude with its flags, resuming this session. */
    List<String> claudeCommand() {
        var argv = new ArrayList<>(List.of("claude"));
        argv.addAll(claudeArgs);
        argv.addAll(List.of("--resume", claudeSessionId));
        return argv;
    }

    /** The shell stays the parent, as in a fresh tab, so leaving claude drops back to a prompt rather than closing it
     *  (cmd /k on Windows; on a Mac a login shell runs claude, then replaces itself with an interactive one).
     *  A plain tab gets its shell back, without whatever command it was started with. */
    List<String> command() {
        if (!Os.WIN) {
            var sh = shell != null ? shell : Os.shellPath();
            if (!claude()) return new ArrayList<>(List.of(sh, "-l"));
            var line = claudeCommand().stream().map(Os::quote).collect(Collectors.joining(" "));
            // not interactive: a login-and-interactive zsh with a heavy .zshrc took 12 s on one Mac before claude even started;
            // the places claude is installed are put on PATH here instead, and the shell that follows is the interactive one
            var path = "export PATH=\"$HOME/.local/bin:/opt/homebrew/bin:/usr/local/bin:$PATH\"; ";
            return new ArrayList<>(List.of(sh, "-l", "-c", path + line + "; exec " + Os.quote(sh) + " -l"));
        }
        if (!claude()) return new ArrayList<>(List.of(shell != null ? shell : "cmd.exe"));
        var argv = new ArrayList<>(List.of("cmd.exe", "/k"));
        argv.addAll(claudeCommand());
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
        if (!Os.WIN) return "cd " + Os.quote(dir) + " && " + (claude() ? claudeCommand() : List.of(shell != null ? shell : Os.shellPath()))
                .stream().map(Os::quote).collect(Collectors.joining(" "));
        List<String> what = claude() ? claudeCommand() : command();
        return "cd /d \"" + dir + "\" & " + what.stream().map(a -> a.contains(" ") ? "\"" + a + "\"" : a).collect(Collectors.joining(" "));
    }

    /** The shell of a started command line, when it is one of the known shells. */
    static String shellOf(List<String> argv) {
        if (argv.isEmpty()) return null;
        var exe = Path.of(argv.getFirst()).getFileName().toString().toLowerCase(Locale.ROOT);
        if (!Os.WIN) return SHELLS.contains(exe) ? argv.getFirst() : null;
        if (!exe.endsWith(".exe")) exe += ".exe";
        return SHELLS.contains(exe) ? exe : null;
    }
}
