package fxterm.tabs;

import module java.base;

/** Writes what is printed to every sink, but a message (a line and the indented lines, `Caused by:` and `...` that belong
 *  to it, so a whole stack trace) identical to the one just before it is written once and then counted:
 *  `[previous message repeated N more times, last at HH:mm:ss]`, at the next different message, after a second of quiet,
 *  and at least every 30 seconds inside a storm. A render error that repeats every frame costs a few lines, not gigabytes.
 *  A message is held until the next one starts or a second passes, so the last one is written by the idle thread
 *  and by a hook at exit. */
final class DedupLog extends OutputStream {
    static final int BLOCK_MAX = 1 << 20;
    static final long QUIET = 1000, SUMMARY_EVERY = 30_000;
    final OutputStream[] sinks;
    final ByteArrayOutputStream line = new ByteArrayOutputStream();
    final StringBuilder block = new StringBuilder();
    String last;
    int count;
    long lastWrite = System.currentTimeMillis(), lastSummary = lastWrite;

    DedupLog(OutputStream... sinks) {
        this.sinks = sinks;
        Thread.ofPlatform().daemon().name("log-dedup").start(() -> {
            while (true) {
                try { Thread.sleep(QUIET); } catch (InterruptedException e) { return; }
                idle();
            }
        });
        Runtime.getRuntime().addShutdownHook(Thread.ofPlatform().unstarted(this::drain));
    }

    @Override public synchronized void write(int b) {
        write(new byte[] { (byte) b }, 0, 1);
    }

    @Override public synchronized void write(byte[] b, int off, int len) {
        lastWrite = System.currentTimeMillis();
        for (var i = off; i < off + len; i++) {
            line.write(b[i]);
            if (b[i] == '\n') addLine();
        }
    }

    void addLine() {
        var text = line.toString(StandardCharsets.UTF_8);
        line.reset();
        if (!block.isEmpty() && !continuation(text)) endBlock();
        block.append(text);
        if (block.length() > BLOCK_MAX) endBlock();
    }

    static boolean continuation(String t) {
        return t.startsWith(" ") || t.startsWith("\t") || t.startsWith("Caused by:") || t.startsWith("Suppressed:") || t.startsWith("...");
    }

    void endBlock() {
        var text = block.toString();
        block.setLength(0);
        var now = System.currentTimeMillis();
        if (text.equals(last)) {
            count++;
            if (now - lastSummary > SUMMARY_EVERY) summary();
            return;
        }
        summary();
        emit(text);
        last = text;
    }

    void summary() {
        lastSummary = System.currentTimeMillis();
        if (count == 0) return;
        emit("  [previous message repeated " + count + " more times, last at " + java.time.LocalTime.now().withNano(0) + "]\n");
        count = 0;
    }

    void emit(String text) {
        var bytes = text.getBytes(StandardCharsets.UTF_8);
        for (var s : sinks) {
            try { s.write(bytes, 0, bytes.length); s.flush(); } catch (IOException ignored) { }
        }
    }

    synchronized void idle() {
        if (System.currentTimeMillis() - lastWrite >= QUIET) drain();
    }

    synchronized void drain() {
        if (line.size() > 0) addLine();
        if (!block.isEmpty()) endBlock();
        summary();
    }
}
