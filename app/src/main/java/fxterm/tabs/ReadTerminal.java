package fxterm.tabs;

import module java.base;
import com.techsenger.jeditermfx.core.compatibility.Point;
import com.techsenger.jeditermfx.core.model.*;
import javafx.application.Platform;
import speechfx.readaloud.*;

/** Read aloud for the terminal: the selected text is spoken and the word being said is lit in the terminal itself,
 *  scrolling to keep it in sight. The voice engine is the speechfx one the editors use, and speed and voice are the
 *  same remembered choice (~/prosemirrorfx/writer/reading.json), so a voice picked in notepadfx is the voice here.
 *  One reading at a time in the process. */
public final class ReadTerminal implements SpeechListener {

    static final double MIN_RATE = 0.5, MAX_RATE = 3.0;
    static final Path PREFS = Path.of(System.getProperty("user.home"), "prosemirrorfx", "writer", "reading.json");
    static final Pattern RATE = Pattern.compile("\"rate\"\\s*:\\s*([0-9.]+)"), VOICE = Pattern.compile("\"voice\"\\s*:\\s*\"((?:[^\"\\\\]|\\\\.)*)\"");
    static final String BOX = "│┃╭╮╰╯┌┐└┘─━═├┤┬┴┼║";

    /** What is spoken, and where each of its characters sits in the terminal. */
    record Spoken(String text, int[] rows, int[] cols) { }

    static CompletableFuture<Voice> shared;

    final Consumer<String> problem;
    /** Told whenever reading starts, pauses or ends, to redraw the controls. */
    Runnable changed = () -> { };
    double rate = 1.0;
    String voiceName;
    volatile SpeechHandle handle;
    TerminalTab tab;
    Spoken spoken;
    int run, lastFrom;

    ReadTerminal(Consumer<String> problem) {
        this.problem = problem;
        try {
            var s = Files.readString(PREFS);
            var r = RATE.matcher(s);
            if (r.find()) rate = Math.clamp(Double.parseDouble(r.group(1)), MIN_RATE, MAX_RATE);
            var v = VOICE.matcher(s);
            if (v.find()) voiceName = v.group(1).replace("\\\"", "\"").replace("\\\\", "\\");
        } catch (Exception ignored) {
            // first run: defaults
        }
    }

    static synchronized CompletableFuture<Voice> voice(String saved) {
        if (shared == null) shared = CompletableFuture.supplyAsync(() -> {
            var v = ReadAloud.all();
            if (saved != null && v.voices().contains(saved)) v.select(saved);
            return v;
        });
        return shared;
    }

    boolean reading() {
        var h = handle;
        return h != null && !h.done();
    }

    boolean paused() {
        var h = handle;
        return h != null && !h.done() && h.paused();
    }

    /** Reads the selection of this tab's terminal from its first word, lighting each word as it is said. */
    void start(TerminalTab t, TerminalTextBuffer buffer, TerminalSelection selection) {
        stop();
        // WORKAROUND until speechfx has a macOS voice (speechfx PRP 02): its only free voice is the Windows one
        if (Os.MAC && !Files.exists(Path.of(System.getProperty("user.home"), "speechfx", "keys", "deepgram_keys.xml"))) {
            problem.accept("Read aloud has no voice on a Mac yet: it needs a Deepgram key in ~/speechfx/keys, and the free macOS voices are not connected.");
            return;
        }
        var s = extract(buffer, selection);
        if (s.text.isBlank()) {
            problem.accept("There is nothing to read in the selection.");
            return;
        }
        tab = t;
        spoken = s;
        var mine = ++run;
        speakFrom(0, mine);
    }

    /** Tests only: no voice is started and nothing is said; the test drives speaking() itself. */
    static boolean silent;

    static final class SilentHandle implements SpeechHandle {
        volatile boolean stopped, paused;
        double rate = 1.0;
        @Override public void pause() { paused = true; }
        @Override public void resume() { paused = false; }
        @Override public void stop() { stopped = true; }
        @Override public void rate(double r) { rate = r; }
        @Override public double rate() { return rate; }
        @Override public boolean paused() { return paused; }
        @Override public boolean done() { return stopped; }
    }

    void speakFrom(int offset, int mine) {
        lastFrom = offset;
        var text = spoken.text.substring(offset);
        var base = offset;
        if (silent) {
            this.base = base;
            handle = new SilentHandle();
            changed.run();
            return;
        }
        voice(voiceName).whenComplete((v, err) -> Platform.runLater(() -> {
            if (mine != run) return;
            if (err != null) {
                problem.accept("Read aloud could not start: " + err.getMessage());
                return;
            }
            this.base = base;
            handle = v.speak(text, rate, this);
            changed.run();
        }));
    }

    int base;

    void toggle() {
        var h = handle;
        if (h == null || h.done()) return;
        if (h.paused()) h.resume();
        else h.pause();
        changed.run();
    }

    void stop() {
        run++;
        var h = handle;
        handle = null;
        if (h != null) h.stop();
        light(null);
        tab = null;
        spoken = null;
        changed.run();
    }

    void rate(double r) {
        rate = Math.round(Math.clamp(r, MIN_RATE, MAX_RATE) * 10) / 10.0;
        var h = handle;
        if (h != null) h.rate(rate);
        save();
        changed.run();
    }

    /** The voices to choose from (the engine may need a moment to start). */
    CompletableFuture<List<String>> voices() {
        return voice(voiceName).thenApply(Voice::voices);
    }

    String selectedVoice() {
        var v = shared == null || !shared.isDone() ? null : shared.getNow(null);
        return v == null ? voiceName : v.selected();
    }

    /** A new voice carries on from the word being read. */
    void select(String name) {
        voice(voiceName).thenAccept(v -> Platform.runLater(() -> {
            var carryOn = reading() && !handle.paused();
            var at = lastFrom;
            if (handle != null) { handle.stop(); handle = null; }
            v.select(name);
            voiceName = name;
            save();
            if (carryOn && spoken != null) speakFrom(at, run);
            changed.run();
        }));
    }

    void save() {
        try {
            Files.createDirectories(PREFS.getParent());
            var v = voiceName == null ? "null" : "\"" + voiceName.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
            Files.writeString(PREFS, "{\"rate\": " + rate + ", \"voice\": " + v + "}\n");
        } catch (IOException e) {
            System.err.println("fxterm-tabs: could not save reading preferences: " + e);
        }
    }

    // --- the engine's callbacks (its own thread) ---

    @Override public void speaking(int from, int to, boolean estimated) {
        Platform.runLater(() -> {
            var s = spoken;
            var t = tab;
            if (s == null || t == null || handle == null) return;
            var a = Math.clamp(base + from, 0, s.text.length() - 1);
            var b = Math.clamp(base + to, a + 1, s.text.length());
            lastFrom = a;
            var ranges = new ArrayList<int[]>();
            var start = a;
            for (var i = a + 1; i <= b; i++) {
                if (i == b || s.rows[i] != s.rows[start]) {
                    ranges.add(new int[] {s.rows[start], s.cols[start], s.cols[i - 1] + 1});
                    start = i;
                }
            }
            var panel = t.widget.getTerminalPanel();
            panel.scrollToShow(s.rows[a]);
            panel.setReading(ranges.toArray(new int[0][]));
        });
    }

    @Override public void finished(boolean stopped) {
        Platform.runLater(() -> {
            if (stopped) return;
            handle = null;
            light(null);
            tab = null;
            spoken = null;
            changed.run();
        });
    }

    @Override public void error(String message) {
        Platform.runLater(() -> {
            problem.accept("Read aloud: " + message);
            stop();
        });
    }

    void light(int[][] ranges) {
        var t = tab;
        if (t != null && t.widget != null) t.widget.getTerminalPanel().setReading(ranges);
    }

    // --- the selection as speech ---

    /** The selected lines as one text for the voice: claude's markers and rules and the box-drawing characters left
     *  out, the terminal's own wraps joined, blank lines kept as paragraph breaks. Each character remembers its row
     *  and column, so the voice's word positions can be lit in the terminal. */
    static Spoken extract(TerminalTextBuffer b, TerminalSelection s) {
        var pts = SelectionUtil.sortPoints(s.getStart(), s.getEnd());
        Point from = pts.getFirst(), to = pts.getSecond();
        var text = new StringBuilder();
        var rows = new ArrayList<Integer>();
        var cols = new ArrayList<Integer>();
        b.lock();
        try {
            var joinNext = false;
            for (var y = from.y; y <= to.y; y++) {
                var x0 = y == from.y ? from.x : 0;
                var x1 = y == to.y ? to.x : Integer.MAX_VALUE;
                var line = b.getLine(y);
                var chars = new StringBuilder();
                var at = new ArrayList<Integer>();
                var col = 0;
                for (var e : line.getEntries()) {
                    var buf = e.getText();
                    for (var i = 0; i < buf.length(); i++, col++) {
                        var c = buf.charAt(i);
                        // U+E000 is what the terminal puts after a double-width character
                        if (col < x0 || col >= x1 || c == '' || c == 0) continue;
                        chars.append(BOX.indexOf(c) >= 0 ? ' ' : c);
                        at.add(col);
                    }
                }
                var wrapped = joinNext;
                joinNext = line.isWrapped();
                // claude's reply dot and tool-result corner, and its horizontal rules, are not words
                var lead = 0;
                while (lead < chars.length() && Character.isWhitespace(chars.charAt(lead))) lead++;
                if (lead < chars.length() && (chars.charAt(lead) == '●' || chars.charAt(lead) == '⎿')) lead++;
                var end = chars.length();
                while (end > lead && Character.isWhitespace(chars.charAt(end - 1))) end--;
                while (lead < end && Character.isWhitespace(chars.charAt(lead))) lead++;
                var rule = Quote.RULE.matcher(chars.substring(lead, end)).matches();
                if (lead >= end || rule) {
                    // a blank line ends the paragraph
                    if (!text.isEmpty() && text.charAt(text.length() - 1) != '\n') {
                        text.append('\n');
                        rows.add(rows.getLast());
                        cols.add(cols.getLast());
                    }
                    continue;
                }
                if (!text.isEmpty() && text.charAt(text.length() - 1) != '\n' && !wrapped) {
                    text.append(' ');
                    rows.add(rows.getLast());
                    cols.add(cols.getLast() + 1);
                }
                for (var i = lead; i < end; i++) {
                    text.append(chars.charAt(i));
                    rows.add(y);
                    cols.add(at.get(i));
                }
            }
        } finally {
            b.unlock();
        }
        return new Spoken(text.toString(), rows.stream().mapToInt(Integer::intValue).toArray(), cols.stream().mapToInt(Integer::intValue).toArray());
    }
}
