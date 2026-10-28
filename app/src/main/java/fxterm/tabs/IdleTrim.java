package fxterm.tabs;

import module java.base;
import java.lang.foreign.*;

/** Gives RAM back while the user is not typing. After QUIET_MS without a key: page the working set out (nearly free,
 *  the pages come back in milliseconds when touched), then again every REPEAT_MS while no key is pressed, since a claude
 *  reply or spinner keeps producing output (and garbage) for minutes; every DEEP_MS, collect garbage first. Output is not
 *  activity: a busy claude in any tab used to keep the RAM from ever being given back. */
public final class IdleTrim {

    static final long QUIET_MS = 15_000, REPEAT_MS = 60_000, DEEP_MS = 300_000;

    static volatile long last = System.currentTimeMillis();
    static volatile long lastTrim, lastDeep;

    /** A key press (the only thing that counts as activity). */
    public static void activity() {
        last = System.currentTimeMillis();
    }

    public static void start() {
        var trim = trimmer();
        Thread.ofPlatform().name("idle-trim").daemon().start(() -> {
            while (true) {
                try { Thread.sleep(5_000); } catch (InterruptedException e) { return; }
                var now = System.currentTimeMillis();
                if (now - last < QUIET_MS) continue;
                if (now - lastDeep >= DEEP_MS) {
                    System.gc();
                    trim.run();
                    lastDeep = lastTrim = now;
                } else if (now - lastTrim >= REPEAT_MS || lastTrim < last) {
                    trim.run();
                    lastTrim = now;
                }
            }
        });
    }

    static Runnable trimmer() {
        try {
            var linker = Linker.nativeLinker();
            var k32 = SymbolLookup.libraryLookup("kernel32", Arena.global());
            var current = linker.downcallHandle(k32.find("GetCurrentProcess").orElseThrow(), FunctionDescriptor.of(ValueLayout.ADDRESS));
            var setSize = linker.downcallHandle(k32.find("SetProcessWorkingSetSize").orElseThrow(),
                    FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.JAVA_LONG, ValueLayout.JAVA_LONG));
            return () -> {
                try {
                    var h = (MemorySegment) current.invoke();
                    var ignored = (int) setSize.invoke(h, -1L, -1L);
                } catch (Throwable e) { }
            };
        } catch (Throwable e) {
            return () -> { };
        }
    }

    private IdleTrim() { }
}
