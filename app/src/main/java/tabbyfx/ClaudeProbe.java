package tabbyfx;

import module java.base;
import com.fasterxml.jackson.databind.ObjectMapper;

/** What claude-code is running inside a tab. claude writes ~/.claude/sessions/<pid>.json for every live session
 *  (sessionId, cwd, status idle/busy, name), so nothing has to be guessed from the screen. The flags it was started
 *  with are not in that file, so they come from the process command line. */
public final class ClaudeProbe {

    static final Path SESSIONS = Path.of(System.getProperty("user.home"), ".claude", "sessions");
    static final ObjectMapper JSON = new ObjectMapper();
    static final Set<String> DROP_WITH_VALUE = Set.of("--resume", "-r", "--session-id"),
            DROP = Set.of("--continue", "-c", "--fork-session");

    long pid;
    String sessionId, cwd, status, name;
    long statusUpdatedAt;
    List<String> args = List.of();

    static Optional<ClaudeProbe> of(long ptyPid) {
        return ProcessHandle.of(ptyPid).stream().flatMap(ProcessHandle::descendants)
                .filter(p -> p.info().command().map(c -> c.toLowerCase().endsWith("claude.exe")).orElse(false))
                .findFirst().flatMap(p -> read(p.pid()));
    }

    static Optional<ClaudeProbe> read(long pid) {
        var file = SESSIONS.resolve(pid + ".json");
        if (!Files.isReadable(file)) return Optional.empty();
        try {
            var n = JSON.readTree(file.toFile());
            var c = new ClaudeProbe();
            c.pid = pid;
            c.sessionId = n.path("sessionId").asText(null);
            c.cwd = n.path("cwd").asText(null);
            c.status = n.path("status").asText("");
            c.name = n.path("name").asText(null);
            c.statusUpdatedAt = n.path("statusUpdatedAt").asLong(0);
            return c.sessionId == null ? Optional.empty() : Optional.of(c);
        } catch (IOException e) {
            return Optional.empty();
        }
    }

    boolean idle() {
        return "idle".equals(status);
    }

    /** Adds the flags it was started with, from a command-line map made once for all tabs (see commandLines). */
    ClaudeProbe withArgs(Map<Long, String> commandLines) {
        args = flags(commandLines.getOrDefault(pid, ""));
        return this;
    }

    /** Every claude.exe command line, pid to text, in one WMI call: ProcessHandle does not expose arguments on
     *  Windows, and one call per tab would take about a second each. */
    static Map<Long, String> commandLines() {
        var out = new HashMap<Long, String>();
        try {
            // encoded, because Java's Windows argument quoting mangles the nested quotes of a plain -Command
            var script = "Get-CimInstance Win32_Process -Filter \"Name='claude.exe'\" | ForEach-Object { \"$($_.ProcessId)`t$($_.CommandLine)\" }";
            var encoded = Base64.getEncoder().encodeToString(script.getBytes(StandardCharsets.UTF_16LE));
            var p = new ProcessBuilder("powershell.exe", "-NoProfile", "-EncodedCommand", encoded).redirectErrorStream(true).start();
            for (var line : new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8).split("\\R")) {
                var tab = line.indexOf('\t');
                if (tab > 0) out.put(Long.parseLong(line.substring(0, tab).strip()), line.substring(tab + 1).strip());
            }
            p.waitFor(15, TimeUnit.SECONDS);
        } catch (IOException | InterruptedException | NumberFormatException e) {
            System.err.println("tabbyfx: could not read claude command lines: " + e);
        }
        return out;
    }

    /** The flags to start it with again, minus whatever chose the session (the resume adds its own). */
    static List<String> flags(String commandLine) {
        var tokens = tokens(commandLine);
        var out = new ArrayList<String>();
        for (var i = 1; i < tokens.size(); i++) {
            var t = tokens.get(i);
            if (DROP_WITH_VALUE.contains(t)) i++;
            else if (!DROP.contains(t)) out.add(t);
        }
        return out;
    }

    static List<String> tokens(String s) {
        var out = new ArrayList<String>();
        var m = java.util.regex.Pattern.compile("\"([^\"]*)\"|(\\S+)").matcher(s);
        while (m.find()) out.add(m.group(1) != null ? m.group(1) : m.group(2));
        return out;
    }

    SavedTab saved(String title) {
        var s = new SavedTab();
        s.title = title != null && !title.isBlank() ? title : name;
        s.cwd = cwd;
        s.claudeSessionId = sessionId;
        s.claudeArgs = new ArrayList<>(args);
        return s;
    }
}
