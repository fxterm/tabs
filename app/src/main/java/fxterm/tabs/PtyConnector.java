package fxterm.tabs;

import module java.base;
import com.pty4j.*;
import com.techsenger.jeditermfx.core.TtyConnector;
import com.techsenger.jeditermfx.core.util.TermSize;

/** A shell on a ConPTY, as the widget's connector. Reads straight off the process, no queue in between. */
public final class PtyConnector implements TtyConnector {

    /** Set by a claude-code session for its own children. A terminal started from one must not pass them on, or
     *  every claude in its tabs runs as a child session with transcript saving off. */
    static final List<String> CLAUDE_MARKERS = List.of("CLAUDECODE", "CLAUDE_CODE_CHILD_SESSION", "CLAUDE_CODE_ENTRYPOINT",
            "CLAUDE_CODE_SSE_PORT", "CLAUDE_CODE_SESSION_ID", "CLAUDE_PID", "CLAUDE_CODE_MESSAGING_SOCKET",
            "CLAUDE_CODE_MESSAGING_TOKEN", "CLAUDE_CODE_SESSION_ATTENDED", "CLAUDE_CODE_EXECPATH", "CLAUDE_EFFORT");

    /** cmd's prompt also sends its folder as OSC 9;9 (what Windows Terminal uses), invisible on screen; that is how a
     *  plain shell's folder after `cd` is known and saved. */
    static final String CWD_PROMPT = "$E]9;9;$P$E\\";
    static final Pattern OSC_CWD = Pattern.compile("\u001b]9;9;([^\u0007\u001b]*)(?:\u0007|\u001b\\\\)");

    final PtyProcess process;
    final Reader reader;
    final String name;
    final StringBuilder tail = new StringBuilder();
    volatile String promptCwd;

    PtyConnector(List<String> argv, String cwd, int columns, int rows) throws IOException {
        var env = new HashMap<>(System.getenv());
        env.put("TERM", "xterm-256color");
        CLAUDE_MARKERS.forEach(env::remove);
        if (!argv.isEmpty() && Path.of(argv.getFirst()).getFileName().toString().equalsIgnoreCase("cmd.exe"))
            env.put("PROMPT", CWD_PROMPT + env.getOrDefault("PROMPT", "$P$G").replaceFirst("^CLINK ", ""));
        name = String.join(" ", argv);
        var dir = cwd != null && Files.isDirectory(Path.of(cwd)) ? cwd : System.getProperty("user.home");
        process = new PtyProcessBuilder().setCommand(argv.toArray(String[]::new)).setEnvironment(env)
                .setDirectory(dir).setInitialColumns(columns).setInitialRows(rows)
                .setConsole(false).setUseWinConPty(true).start();
        reader = new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8);
    }

    @Override public int read(char[] buf, int offset, int length) throws IOException {
        var n = reader.read(buf, offset, length);
        IdleTrim.activity();
        if (n > 0) watchCwd(buf, offset, n);
        return n;
    }

    /** Looks for the folder report in what was just read, keeping a short tail so one split across reads is found. */
    void watchCwd(char[] buf, int offset, int n) {
        tail.append(buf, offset, n);
        if (tail.indexOf("\u001b]9;9;") >= 0) {
            var m = OSC_CWD.matcher(tail);
            var end = -1;
            while (m.find()) { promptCwd = m.group(1); end = m.end(); }
            if (end > 0) tail.delete(0, end);
        }
        if (tail.length() > 1024) tail.delete(0, tail.length() - 512);
    }

    @Override public void write(byte[] b) throws IOException {
        var out = process.getOutputStream();
        out.write(b);
        out.flush();
    }

    @Override public void write(String s) throws IOException { write(s.getBytes(StandardCharsets.UTF_8)); }

    @Override public boolean isConnected() { return process.isAlive(); }

    @Override public void resize(TermSize size) { if (process.isAlive()) process.setWinSize(new WinSize(size.getColumns(), size.getRows())); }

    @Override public int waitFor() throws InterruptedException { return process.waitFor(); }

    @Override public boolean ready() throws IOException { return reader.ready(); }

    @Override public String getName() { return name; }

    @Override public void close() { process.destroy(); }
}
