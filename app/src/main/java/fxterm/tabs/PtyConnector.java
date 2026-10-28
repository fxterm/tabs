package fxterm.tabs;

import module java.base;
import com.pty4j.*;
import com.techsenger.jeditermfx.core.TtyConnector;
import com.techsenger.jeditermfx.core.util.TermSize;

/** A shell on a ConPTY, as the widget's connector. Reads straight off the process, no queue in between. */
public final class PtyConnector implements TtyConnector {

    /** Set by a claude-code session for its own children. A terminal started from one must not pass them on, or
     *  every claude in its tabs runs as a child session with transcript saving off. */
    /** By rule, not by list, because each claude version adds markers (AI_AGENT made claude draw itself as a
     *  subagent): every CLAUDE* variable, AI_AGENT, and cmd's NoDefaultCurrentDirectoryInExePath that claude sets for
     *  its own shells - except a variable the user defined in Windows (HKCU/HKLM Environment), which is theirs. */
    static boolean inheritedFromClaude(String name) {
        var n = name.toUpperCase(Locale.ROOT);
        return (n.startsWith("CLAUDE") || n.equals("AI_AGENT") || n.equals("NODEFAULTCURRENTDIRECTORYINEXEPATH"))
                && !userDefined().contains(n);
    }

    static Set<String> userDefined;

    /** Names set in the registry's Environment keys, read once with reg.exe. */
    static synchronized Set<String> userDefined() {
        if (userDefined != null) return userDefined;
        var names = new HashSet<String>();
        for (var key : List.of("HKCU\\Environment", "HKLM\\SYSTEM\\CurrentControlSet\\Control\\Session Manager\\Environment")) {
            try {
                var p = new ProcessBuilder("reg", "query", key).redirectErrorStream(true).start();
                for (var line : new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8).split("\\R")) {
                    var m = Pattern.compile("^\\s{4}(\\S+)\\s+REG_").matcher(line);
                    if (m.find()) names.add(m.group(1).toUpperCase(Locale.ROOT));
                }
                p.waitFor(10, TimeUnit.SECONDS);
            } catch (IOException | InterruptedException e) {
                System.err.println("fxterm-tabs: could not read " + key + ": " + e);
            }
        }
        return userDefined = names;
    }

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
        env.keySet().removeIf(PtyConnector::inheritedFromClaude);
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
