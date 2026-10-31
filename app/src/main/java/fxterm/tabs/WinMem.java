package fxterm.tabs;

import module java.base;
import java.lang.foreign.*;
import java.lang.invoke.MethodHandle;

/** Working set (RAM in use now) of any process, through psapi: Java has no portable way to ask for another
 *  process's memory. Returns 0 for a process that has gone or cannot be opened. */
final class WinMem {

    static final int QUERY_LIMITED = 0x1000;
    static final MethodHandle OPEN, INFO, CLOSE;

    static {
        var linker = Linker.nativeLinker();
        var k32 = SymbolLookup.libraryLookup("kernel32", Arena.global());
        OPEN = linker.downcallHandle(k32.find("OpenProcess").orElseThrow(),
                FunctionDescriptor.of(ValueLayout.ADDRESS, ValueLayout.JAVA_INT, ValueLayout.JAVA_INT, ValueLayout.JAVA_INT));
        INFO = linker.downcallHandle(k32.find("K32GetProcessMemoryInfo").orElseThrow(),
                FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.JAVA_INT));
        CLOSE = linker.downcallHandle(k32.find("CloseHandle").orElseThrow(),
                FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS));
    }

    /** PROCESS_MEMORY_COUNTERS on x64: cb, PageFaultCount (4 bytes each), then SIZE_T fields; WorkingSetSize at 16. */
    static long workingSet(long pid) {
        try (var arena = Arena.ofConfined()) {
            var h = (MemorySegment) OPEN.invoke(QUERY_LIMITED, 0, (int) pid);
            if (h.address() == 0) return 0;
            try {
                var counters = arena.allocate(72);
                counters.set(ValueLayout.JAVA_INT, 0, 72);
                var ok = (int) INFO.invoke(h, counters, 72);
                return ok == 0 ? 0 : counters.get(ValueLayout.JAVA_LONG, 16);
            } finally {
                var ignored = (int) CLOSE.invoke(h);
            }
        } catch (Throwable e) {
            return 0;
        }
    }

    /** The shell and its direct children (for example claude): what a tab costs, without the deeper helpers. */
    static long tab(long shellPid) {
        return workingSet(shellPid) + ProcessHandle.of(shellPid).stream().flatMap(ProcessHandle::children)
                .mapToLong(p -> workingSet(p.pid())).sum();
    }

    private WinMem() { }
}
