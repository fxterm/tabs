package tabbyfx;

import module java.base;
import javafx.application.Platform;
import javafx.event.Event;
import javafx.scene.input.*;

/** Keystroke latency, measured inside the app. Each keystroke is fired at the visible tab's canvas (no OS focus
 *  needed, so it never disturbs the desktop) and timed twice: until the screen buffer changes (the echo is in) and
 *  until the repaint after that finishes (the echo is drawn). Alternates a letter and a backspace so input stays short. */
public final class Bench {

    final TerminalTab tab;
    final Options o;
    final List<Double> echo = new ArrayList<>(), drawn = new ArrayList<>();
    volatile long t0, t1;
    volatile boolean pending;

    Bench(TerminalTab tab, Options o) {
        this.tab = tab;
        this.o = o;
        tab.widget.getTerminalTextBuffer().addModelListener(() -> {
            if (pending && t1 == 0) t1 = System.nanoTime();
        });
        tab.widget.getTerminalPanel().setRepaintListener(() -> {
            if (!pending || t1 == 0) return;
            var t2 = System.nanoTime();
            echo.add((t1 - t0) / 1e6);
            drawn.add((t2 - t0) / 1e6);
            pending = false;
        });
    }

    void run() {
        Thread.ofPlatform().daemon().name("bench").start(() -> {
            sleep(o.benchWait);
            if (o.benchPrompt != null) {
                Platform.runLater(() -> {
                    o.benchPrompt.chars().forEach(c -> key(KeyEvent.KEY_TYPED, String.valueOf((char) c), KeyCode.UNDEFINED));
                    key(KeyEvent.KEY_PRESSED, KeyEvent.CHAR_UNDEFINED, KeyCode.ENTER);
                    key(KeyEvent.KEY_TYPED, "\r", KeyCode.UNDEFINED);
                    key(KeyEvent.KEY_RELEASED, KeyEvent.CHAR_UNDEFINED, KeyCode.ENTER);
                });
                sleep(o.benchPromptWait);
            }
            for (var i = 0; i < o.bench; i++) {
                var letter = i % 2 == 0;
                t1 = 0;
                Platform.runLater(() -> {
                    t0 = System.nanoTime();
                    pending = true;
                    fire(letter);
                });
                sleep(o.benchGap);
                pending = false;
            }
            report();
            dump();
            if (o.benchExit) Platform.runLater(() -> { tab.close(); Platform.exit(); System.exit(0); });
        });
    }

    void fire(boolean letter) {
        var code = letter ? KeyCode.X : KeyCode.BACK_SPACE;
        var ch = letter ? "x" : "\b";
        key(KeyEvent.KEY_PRESSED, KeyEvent.CHAR_UNDEFINED, code);
        key(KeyEvent.KEY_TYPED, ch, KeyCode.UNDEFINED);
        key(KeyEvent.KEY_RELEASED, KeyEvent.CHAR_UNDEFINED, code);
    }

    /** The order a real keyboard produces; the panel relies on it (a handled press suppresses the next typed). */
    void key(javafx.event.EventType<KeyEvent> type, String ch, KeyCode code) {
        Event.fireEvent(tab.widget.getTerminalPanel().getCanvas(), new KeyEvent(type, ch, "", code, false, false, false, false));
    }

    void report() {
        var line = "bench %s keys=%d measured=%d echo_ms %s drawn_ms %s".formatted(o.benchLabel, o.bench, drawn.size(), stats(echo), stats(drawn));
        System.out.println(line);
        if (o.benchOut != null) {
            try { Files.writeString(Path.of(o.benchOut), line + System.lineSeparator(), StandardOpenOption.CREATE, StandardOpenOption.APPEND); }
            catch (IOException e) { System.err.println(e); }
        }
    }

    void dump() {
        if (o.benchDump == null) return;
        var buffer = tab.widget.getTerminalTextBuffer();
        buffer.lock();
        try {
            var text = buffer.getHistoryBuffer().getLines() + System.lineSeparator() + "=== screen ===" + System.lineSeparator() + buffer.getScreenLines();
            Files.writeString(Path.of(o.benchDump), text);
        } catch (IOException e) {
            System.err.println(e);
        } finally {
            buffer.unlock();
        }
    }

    static String stats(List<Double> xs) {
        if (xs.isEmpty()) return "none";
        var s = xs.stream().sorted().toList();
        return "p50=%.1f p95=%.1f max=%.1f".formatted(s.get(s.size() / 2), s.get((int) (s.size() * 0.95)), s.getLast());
    }

    static void sleep(long ms) {
        try { Thread.sleep(ms); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
    }
}
