package tabbyfx;

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

    final PtyProcess process;
    final Reader reader;
    final String name;

    PtyConnector(List<String> argv, String cwd, int columns, int rows) throws IOException {
        var env = new HashMap<>(System.getenv());
        env.put("TERM", "xterm-256color");
        CLAUDE_MARKERS.forEach(env::remove);
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
        return n;
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
