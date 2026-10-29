package fxterm.tabs;

import module java.base;
import com.techsenger.jeditermfx.core.TextStyle;
import com.techsenger.jeditermfx.core.compatibility.Point;
import com.techsenger.jeditermfx.core.model.*;

/** "Quote & reply": a terminal selection turned into a markdown quote to answer point by point with `>>`, and the
 *  file such a reply is written to when it is edited in notepadfx. */
public final class Quote {

    /** The selection as plain text, as a copy gives it. */
    static String plain(TerminalTextBuffer b, TerminalSelection s) {
        b.lock();
        try {
            return SelectionUtil.getSelectedText(s, b);
        } finally {
            b.unlock();
        }
    }

    /** The selection as a markdown quote: bold kept as **, the terminal's line wraps and padding and claude's own
     *  markers (the reply dot, the tool-result corner, rules) taken out, every line prefixed with "> ". */
    static String markdown(TerminalTextBuffer b, TerminalSelection s) {
        var pts = SelectionUtil.sortPoints(s.getStart(), s.getEnd());
        Point from = pts.getFirst(), to = pts.getSecond();
        var lines = new ArrayList<String>();
        b.lock();
        try {
            var joinNext = false;
            for (var y = from.y; y <= to.y; y++) {
                var x0 = y == from.y ? from.x : 0;
                var x1 = y == to.y ? to.x : Integer.MAX_VALUE;
                var line = b.getLine(y);
                var text = styled(line, x0, x1);
                // a line the terminal itself wrapped goes on straight after the previous one, without a space
                if (joinNext && !lines.isEmpty()) lines.set(lines.size() - 1, (lines.getLast() + text).replace("****", ""));
                else lines.add(text);
                joinNext = line.isWrapped();
            }
        } finally {
            b.unlock();
        }
        return quote(clean(lines));
    }

    /** One line's columns x0..x1 with bold runs between ** (spaces kept outside the markers). */
    static String styled(TerminalLine line, int x0, int x1) {
        var text = new StringBuilder();
        var bold = new ArrayList<Boolean>();
        var col = 0;
        for (var e : line.getEntries()) {
            var buf = e.getText();
            var isBold = e.getStyle().hasOption(TextStyle.Option.BOLD);
            for (var i = 0; i < buf.length(); i++, col++) {
                var c = buf.charAt(i);
                if (col < x0 || col >= x1 || c == '' || c == 0) continue;
                text.append(c);
                bold.add(isBold && !Character.isWhitespace(c));
            }
        }
        var out = new StringBuilder();
        var in = false;
        for (var i = 0; i < text.length(); i++) {
            var c = text.charAt(i);
            var b = bold.get(i);
            // a run ends at the last bold character, so trailing spaces stay outside the markers
            if (!in && b) { out.append("**"); in = true; }
            out.append(c);
            if (in && (i + 1 == text.length() || !boldAhead(bold, text, i + 1))) { out.append("**"); in = false; }
        }
        return out.toString().stripTrailing();
    }

    /** True when the run goes on: the next non-space character is bold too. */
    static boolean boldAhead(List<Boolean> bold, CharSequence text, int i) {
        for (; i < text.length(); i++) if (!Character.isWhitespace(text.charAt(i))) return bold.get(i);
        return false;
    }

    static final Pattern MARKER = Pattern.compile("^(\\s*)(?:[●⎿]\\s?)");
    static final Pattern RULE = Pattern.compile("^\\s*[─━═-]{3,}\\s*$");
    static final Pattern BLOCK_START = Pattern.compile("^\\s*(?:[-*•>#|]|\\d+[.)]|```)");

    /** Undoes what the terminal did to the text: markers and rules out, the common indent out, and lines the
     *  terminal broke at its width joined back into their paragraph. */
    static List<String> clean(List<String> raw) {
        var lines = new ArrayList<String>();
        for (var l : raw) {
            if (RULE.matcher(l).matches()) continue;
            lines.add(MARKER.matcher(l).replaceFirst("$1"));
        }
        var indent = lines.stream().filter(l -> !l.isBlank()).mapToInt(Quote::indent).min().orElse(0);
        lines.replaceAll(l -> l.isBlank() ? "" : l.substring(Math.min(indent, l.length())));
        while (!lines.isEmpty() && lines.getFirst().isEmpty()) lines.removeFirst();
        while (!lines.isEmpty() && lines.getLast().isEmpty()) lines.removeLast();
        var longest = lines.stream().mapToInt(String::length).max().orElse(0);
        var out = new ArrayList<String>();
        for (var l : lines) {
            var prev = out.isEmpty() ? "" : out.getLast();
            // a line that reached (nearly) the widest one was wrapped by the terminal; the next line goes on it,
            // unless that line starts something of its own (a bullet, a number, a heading, a quote, a table)
            var wrapped = !prev.isEmpty() && prev.length() >= longest - 12 && longest > 40;
            if (wrapped && !l.isEmpty() && !BLOCK_START.matcher(l).find()) {
                out.set(out.size() - 1, prev + " " + l.strip());
            } else {
                out.add(l);
            }
        }
        // two bold runs that a wrap split ("**a** **b**") become one
        out.replaceAll(l -> l.replace("** **", " "));
        return out;
    }

    static int indent(String l) {
        var i = 0;
        while (i < l.length() && l.charAt(i) == ' ') i++;
        return i;
    }

    static String quote(List<String> lines) {
        return lines.stream().map(l -> l.isEmpty() ? ">" : "> " + l).collect(Collectors.joining("\n"));
    }

    // --- where a reply edited outside Tabs is kept ---

    static final DateTimeFormatter STAMP = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");

    /** A new file for a reply: a sub-file of the PRP the tab's claude session is working on (NN-prp.XX.reply.md
     *  beside it), or, when the folder has no PRP, a file in the temp folder. */
    public static Path replyFile(String cwd, String sessionId) {
        try {
            var prp = prpDir(cwd);
            if (prp != null) {
                var n = prpOf(prp, sessionId);
                if (n != null) {
                    var next = 1;
                    var sub = Pattern.compile("^" + n + "-prp\\.(\\d\\d)\\.");
                    try (var files = Files.list(prp)) {
                        for (var f : files.toList()) {
                            var m = sub.matcher(f.getFileName().toString());
                            if (m.find() && !m.group(1).equals("99")) next = Math.max(next, Integer.parseInt(m.group(1)) + 1);
                        }
                    }
                    return prp.resolve("%s-prp.%02d.reply.md".formatted(n, next));
                }
            }
        } catch (IOException e) {
            System.err.println("fxterm-tabs: no PRP file for the reply: " + e);
        }
        var dir = Path.of(System.getProperty("java.io.tmpdir"), "fxterm-tabs");
        return dir.resolve("reply-" + LocalDateTime.now().format(STAMP) + ".md");
    }

    static final Pattern PRP_FILE = Pattern.compile("(\\d\\d)-prp-.*\\.md");

    /** The prp folder holding PRP files, in the tab's folder or one of its two parents (an empty one is passed by). */
    public static Path prpDir(String cwd) {
        if (cwd == null) return null;
        for (var d = Path.of(cwd); d != null && d.getNameCount() >= 1; d = d.getParent()) {
            var prp = d.resolve("prp");
            if (Files.isDirectory(prp)) {
                try (var files = Files.list(prp)) {
                    if (files.anyMatch(f -> PRP_FILE.matcher(f.getFileName().toString()).matches())) return prp;
                } catch (IOException e) {
                    // unreadable: look further up
                }
            }
            if (Path.of(cwd).getNameCount() - d.getNameCount() >= 2) break;
        }
        return null;
    }

    static final Pattern PRP_NAME = Pattern.compile("\\b(\\d\\d)-prp[-.]");

    /** Which PRP: the first one named in the session's own transcript that exists in this folder (a session is
     *  opened with "@prp/NN-prp-...md"); else the open one (not .closed) whose PRP or status file changed last. */
    public static String prpOf(Path prp, String sessionId) throws IOException {
        Set<String> here;
        try (var files = Files.list(prp)) {
            here = files.map(f -> PRP_FILE.matcher(f.getFileName().toString())).filter(Matcher::matches)
                    .map(m -> m.group(1)).collect(Collectors.toSet());
        }
        if (sessionId != null) {
            var projects = Path.of(System.getProperty("user.home"), ".claude", "projects");
            try (var dirs = Files.list(projects)) {
                for (var d : dirs.toList()) {
                    var t = d.resolve(sessionId + ".jsonl");
                    if (!Files.isReadable(t)) continue;
                    // the last one named: a long session moves on from the PRP it opened on
                    try (var in = Files.lines(t)) {
                        var found = in.flatMap(l -> PRP_NAME.matcher(l).results()).map(r -> r.group(1))
                                .filter(here::contains).reduce((a, b) -> b);
                        if (found.isPresent()) return found.get();
                    }
                }
            } catch (UncheckedIOException e) {
                // an unreadable transcript just means falling back
            }
        }
        try (var files = Files.list(prp)) {
            return files.filter(f -> PRP_FILE.matcher(f.getFileName().toString()).matches() && !f.toString().endsWith(".closed.md"))
                    .max(Comparator.comparingLong(f -> touched(prp, f)))
                    .map(f -> f.getFileName().toString().substring(0, 2)).orElse(null);
        }
    }

    static long touched(Path prp, Path file) {
        var status = prp.resolve(file.getFileName().toString().substring(0, 2) + "-prp.status.md").toFile();
        return Math.max(file.toFile().lastModified(), status.lastModified());
    }

    /** Opens the file in notepadfx, else in Notepad. Started directly, never through a shell. */
    static void edit(Path file) {
        var notepadfx = Path.of(System.getenv().getOrDefault("LOCALAPPDATA", ""), "Programs", "notepadfx", "notepadfx.exe");
        try {
            new ProcessBuilder(Files.isExecutable(notepadfx) ? notepadfx.toString() : "notepad.exe", file.toString()).start();
        } catch (IOException e) {
            System.err.println("fxterm-tabs: could not open " + file + ": " + e);
        }
    }

    /** Writes the text to a new reply file for the tab and opens it; the path, for `@path`. */
    static Path writeAndEdit(TerminalTab t, String text) throws IOException {
        var session = t.live() ? ClaudeProbe.of(t.connector.process.pid()).map(c -> c.sessionId).orElse(null) : null;
        var file = replyFile(t.where(), session);
        Files.createDirectories(file.getParent());
        Files.writeString(file, text.endsWith("\n") ? text : text + "\n", StandardCharsets.UTF_8);
        edit(file);
        return file;
    }
}
