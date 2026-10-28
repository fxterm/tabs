package fxterm.tabs;

import module java.base;
import com.fasterxml.jackson.databind.*;

/** Every tab that closed, hibernated, or was open when Tabs closed: what it ran, where, and its text, so it can be
 *  found again, read, copied and resumed. One line per entry in history.jsonl; the text in text/<id>.txt beside it.
 *  Written on one background thread, so closing a tab never waits for the disk. */
public final class History {

    /** One entry, as stored. */
    public static final class Entry {
        public String id, event, title, cwd, shell, claudeSessionId, customTitle;
        public List<String> claudeArgs = new ArrayList<>();
        public long at;
        public int lines;

        boolean claude() {
            return claudeSessionId != null;
        }

        /** What waking it again runs, as a saved tab. */
        SavedTab asSaved() {
            var s = new SavedTab();
            s.title = title;
            s.cwd = cwd;
            s.shell = shell;
            s.claudeSessionId = claudeSessionId;
            s.claudeArgs = new ArrayList<>(claudeArgs);
            s.customTitle = customTitle;
            return s;
        }

        String shown() {
            return customTitle != null && !customTitle.isBlank() ? customTitle : title;
        }
    }

    static final ObjectMapper JSON = new ObjectMapper().configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    final Path dir, index, texts;
    final ExecutorService writer = Executors.newSingleThreadExecutor(r -> Thread.ofPlatform().daemon().name("history").unstarted(r));

    /** ~/fxterm/tabs/history, or beside the --state file, so a test never writes into the real history. */
    History(Options o) {
        dir = o.state != null ? Path.of(o.state + ".history") : Path.of(System.getProperty("user.home"), "fxterm", "tabs", "history");
        index = dir.resolve("history.jsonl");
        texts = dir.resolve("text");
    }

    /** Adds an entry for this saved tab (closed, hibernated, open at quit). */
    void record(SavedTab s, String event) {
        if (s == null) return;
        var e = new Entry();
        e.id = UUID.randomUUID().toString();
        e.event = event;
        e.at = System.currentTimeMillis();
        e.title = s.title;
        e.customTitle = s.customTitle;
        e.cwd = s.cwd;
        e.shell = s.shell;
        e.claudeSessionId = s.claudeSessionId;
        e.claudeArgs = new ArrayList<>(s.claudeArgs);
        var text = s.screen == null ? "" : s.screen;
        e.lines = text.isEmpty() ? 0 : (int) text.lines().count();
        writer.execute(() -> {
            try {
                Files.createDirectories(texts);
                Files.writeString(texts.resolve(e.id + ".txt"), text);
                Files.writeString(index, JSON.writeValueAsString(e) + "\n", StandardOpenOption.CREATE, StandardOpenOption.APPEND);
            } catch (IOException ex) {
                System.err.println("fxterm-tabs: history not written: " + ex);
            }
        });
    }

    /** Waits for writes queued so far, so a quit does not lose the last entries. */
    void flush() {
        try {
            writer.submit(() -> { }).get(5, TimeUnit.SECONDS);
        } catch (InterruptedException | ExecutionException | TimeoutException e) {
            System.err.println("fxterm-tabs: history flush: " + e);
        }
    }

    /** Every entry, newest first; a broken line is skipped. */
    List<Entry> load() {
        if (!Files.isReadable(index)) return List.of();
        var out = new ArrayList<Entry>();
        try {
            for (var line : Files.readAllLines(index)) {
                if (line.isBlank()) continue;
                try { out.add(JSON.readValue(line, Entry.class)); } catch (IOException ignored) { }
            }
        } catch (IOException e) {
            System.err.println("fxterm-tabs: history unreadable: " + e);
        }
        out.sort(Comparator.comparingLong((Entry x) -> x.at).reversed());
        return out;
    }

    String text(Entry e) {
        try {
            return Files.readString(texts.resolve(e.id + ".txt"));
        } catch (IOException ex) {
            return "";
        }
    }

    /** Removes entries older than the cut-off (0: all), with their text; keeps the rest. */
    void clear(long olderThanMillis) {
        flush();
        var keep = new ArrayList<Entry>();
        for (var e : load()) {
            if (olderThanMillis == 0 || e.at < olderThanMillis) {
                try { Files.deleteIfExists(texts.resolve(e.id + ".txt")); } catch (IOException ignored) { }
            } else {
                keep.add(e);
            }
        }
        try {
            var sb = new StringBuilder();
            for (var e : keep.reversed()) sb.append(JSON.writeValueAsString(e)).append('\n');
            Files.createDirectories(dir);
            Files.writeString(index, sb.toString());
        } catch (IOException e) {
            System.err.println("fxterm-tabs: history not cleared: " + e);
        }
    }

    /** Disk used, for the clear button's label. */
    long bytes() {
        try (var files = Files.walk(dir)) {
            return files.filter(Files::isRegularFile).mapToLong(p -> p.toFile().length()).sum();
        } catch (IOException | UncheckedIOException e) {
            return 0;
        }
    }
}
