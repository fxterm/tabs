package tabbyfx;

import module java.base;
import java.lang.management.ManagementFactory;
import javafx.application.Platform;

/** Puts tabs to sleep when RAM runs short, like a browser's tab hibernation. Once a minute: if free physical memory
 *  is under --hibernate-free-mb, the hidden live tab whose claude has been idle longest (and at least
 *  --hibernate-idle-min) goes to sleep. One tab per minute at most; busy sessions and plain shells are never touched. */
public final class Hibernator {

    final Supplier<List<TerminalTab>> hidden;
    final Options o;

    Hibernator(Supplier<List<TerminalTab>> hidden, Options o) {
        this.hidden = hidden;
        this.o = o;
    }

    void start() {
        if (o.hibernateFreeMb <= 0) return;
        Thread.ofPlatform().daemon().name("hibernator").start(() -> {
            while (true) {
                try { Thread.sleep(60_000); } catch (InterruptedException e) { return; }
                try { tick(); } catch (RuntimeException e) { System.err.println("tabbyfx: hibernator: " + e); }
            }
        });
    }

    static long freeMb() {
        var os = (com.sun.management.OperatingSystemMXBean) ManagementFactory.getOperatingSystemMXBean();
        return os.getFreeMemorySize() / (1024 * 1024);
    }

    void tick() {
        if (freeMb() >= o.hibernateFreeMb) return;
        var tabs = new CompletableFuture<List<TerminalTab>>();
        Platform.runLater(() -> tabs.complete(hidden.get()));
        var cutoff = System.currentTimeMillis() - o.hibernateIdleMin * 60_000L;
        record Candidate(TerminalTab tab, ClaudeProbe claude) { }
        tabs.join().stream()
                .filter(t -> t.connector != null)
                .flatMap(t -> ClaudeProbe.of(t.connector.process.pid()).map(c -> new Candidate(t, c)).stream())
                .filter(c -> c.claude().idle() && c.claude().statusUpdatedAt < cutoff)
                .min(Comparator.comparingLong(c -> c.claude().statusUpdatedAt))
                .ifPresent(c -> {
                    var saved = c.tab().decorate(c.claude().withArgs(ClaudeProbe.commandLines()).saved(c.tab().title.get()));
                    Platform.runLater(() -> { if (c.tab().live()) c.tab().sleep(saved); });
                });
    }
}
