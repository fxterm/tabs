package fxterm.tabs;

import module java.base;
import java.lang.foreign.*;

/** Gives RAM back while idle. Stage one after QUIET_MS without activity: page the working set out (nearly free, and
 *  the pages come back in milliseconds when touched). Stage two after DEEP_MS: collect garbage first, then page out.
 *  Each stage runs once per idle stretch. Activity is output from any shell or a key press. */
public final class IdleTrim {

    static final long QUIET_MS = 15_000, DEEP_MS = 120_000;

    static volatile long last = System.currentTimeMillis();
    static volatile int stage = 2;

    public static void activity() {
        last = System.currentTimeMillis();
        stage = 0;
    }

    public static void start() {
        var trim = trimmer();
        Thread.ofPlatform().name("idle-trim").daemon().start(() -> {
            while (true) {
                try { Thread.sleep(5_000); } catch (InterruptedException e) { return; }
                var idle = System.currentTimeMillis() - last;
                if (stage == 0 && idle >= QUIET_MS) { trim.run(); stage = 1; }
                if (stage == 1 && idle >= DEEP_MS) { System.gc(); trim.run(); stage = 2; }
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
