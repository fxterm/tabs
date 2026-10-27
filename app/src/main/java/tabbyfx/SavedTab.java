package tabbyfx;

import module java.base;

/** A tab as written to the session file: enough to show it asleep and to wake it later. */
public final class SavedTab {

    public String title = "shell", cwd, claudeSessionId, customTitle, color;
    public List<String> claudeArgs = new ArrayList<>();
    public long savedAt = System.currentTimeMillis();

    boolean claude() {
        return claudeSessionId != null;
    }

    /** cmd.exe stays the parent, as in a fresh tab, so leaving claude drops back to a prompt rather than closing it. */
    List<String> command() {
        var argv = new ArrayList<>(List.of("cmd.exe"));
        if (!claude()) return argv;
        argv.addAll(List.of("/k", "claude"));
        argv.addAll(claudeArgs);
        argv.addAll(List.of("--resume", claudeSessionId));
        return argv;
    }
}
