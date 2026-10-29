package fxterm.tabs;

import module java.base;
import com.fasterxml.jackson.databind.ObjectMapper;

/** What the UI did, one JSON object per line in ~/fxterm/tabs/events.jsonl (beside the --state file in tests): drags
 *  with the rows involved, moves, selection, windows opening and closing, control requests. Meant to be grepped when
 *  something went wrong ("I dragged one tab and another moved"). Kept under 5 MB: the older half goes to events.1.jsonl. */
public final class Events {

    static final ObjectMapper JSON = new ObjectMapper();
    static final long MAX = 5L << 20;
    static Path file = Path.of(System.getProperty("user.home"), "fxterm", "tabs", "events.jsonl");

    static void init(Options o) {
        if (o.state != null) file = Path.of(o.state + ".events.jsonl");
    }

    /** One event: its kind and pairs of name, value. */
    static synchronized void log(String kind, Object... pairs) {
        var n = JSON.createObjectNode();
        n.put("t", LocalDateTime.now().toString());
        n.put("ev", kind);
        for (var i = 0; i + 1 < pairs.length; i += 2) n.set(String.valueOf(pairs[i]), JSON.valueToTree(pairs[i + 1]));
        try {
            Files.createDirectories(file.getParent());
            if (Files.exists(file) && Files.size(file) > MAX)
                Files.move(file, file.resolveSibling(file.getFileName().toString().replace(".jsonl", ".1.jsonl")), StandardCopyOption.REPLACE_EXISTING);
            Files.writeString(file, JSON.writeValueAsString(n) + "\n", StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (IOException e) {
            System.err.println("fxterm-tabs: event not logged: " + e);
        }
    }

    /** A tab as it appears in the log: id, place, title. */
    static Map<String, Object> tab(TerminalTab t) {
        if (t == null) return null;
        var m = new LinkedHashMap<String, Object>();
        m.put("id", t.id);
        m.put("index", t.owner == null ? -1 : t.owner.tabs.indexOf(t));
        m.put("window", t.owner == null ? -1 : t.owner.app.windows.indexOf(t.owner));
        m.put("title", t.shown());
        if (t.separator) m.put("separator", true);
        return m;
    }

    private Events() { }
}
