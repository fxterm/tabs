package fxterm.tabs;

import module java.base;
import javafx.application.Platform;

/** Every few seconds: each live tab's RAM (its shell plus direct children, so claude counts) and, for a tab
 *  running claude, the working directory claude reports. Values reach the FX thread only when they changed,
 *  so an idle window is not woken for nothing. */
public final class RamWatch {

    static final long PERIOD_MS = 3_000, MIN_CHANGE = 4 * 1024 * 1024;

    final Supplier<List<TerminalTab>> tabs;
    final Runnable changed;

    RamWatch(Supplier<List<TerminalTab>> tabs, Runnable changed) {
        this.tabs = tabs;
        this.changed = changed;
    }

    void start() {
        Thread.ofPlatform().daemon().name("ram-watch").start(() -> {
            while (true) {
                try { Thread.sleep(PERIOD_MS); } catch (InterruptedException e) { return; }
                try { tick(); } catch (RuntimeException e) { System.err.println("fxterm-tabs: ram watch: " + e); }
            }
        });
    }

    void tick() {
        var snapshot = new CompletableFuture<List<TerminalTab>>();
        Platform.runLater(() -> snapshot.complete(List.copyOf(tabs.get())));
        var any = false;
        for (var t : snapshot.join()) {
            var c = t.connector;
            var bytes = c == null ? 0 : Mem.tab(c.process.pid());
            var probe = c == null ? Optional.<ClaudeProbe>empty() : ClaudeProbe.of(c.process.pid());
            var cwd = probe.map(p -> p.cwd).orElse(null);
            // remember each claude the tab runs, flags included (read once per new claude), so the tab can offer it
            // again after claude was ended with /exit and fxterm-tabs was restarted
            probe.filter(p -> p.pid != t.lastClaudePid).ifPresent(p -> {
                t.lastClaude = p.withArgs(ClaudeProbe.commandLines()).saved(null);
                t.lastClaudePid = p.pid;
            });
            if (Math.abs(bytes - t.ramBytes) >= MIN_CHANGE || !Objects.equals(cwd, t.liveCwd)) {
                t.ramBytes = bytes;
                t.liveCwd = cwd;
                any = true;
            }
        }
        var self = Mem.workingSet(ProcessHandle.current().pid());
        if (any || Math.abs(self - selfBytes) >= MIN_CHANGE) {
            selfBytes = self;
            Platform.runLater(changed);
        }
    }

    volatile long selfBytes;
}
