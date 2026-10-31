package fxterm.tabs;

import module java.base;

/** RAM in use now of any process: psapi on Windows (WinMem), ps on macOS. Java has no portable way to ask for another
 *  process's memory. Returns 0 for a process that has gone or cannot be opened. */
public final class Mem {

    static long workingSet(long pid) {
        return Os.WIN ? WinMem.workingSet(pid) : Os.rss(List.of(pid));
    }

    /** The shell and its direct children (for example claude): what a tab costs, without the deeper helpers. */
    static long tab(long shellPid) {
        if (Os.WIN) return WinMem.tab(shellPid);
        var pids = new ArrayList<Long>(List.of(shellPid));
        ProcessHandle.of(shellPid).stream().flatMap(ProcessHandle::children).forEach(p -> pids.add(p.pid()));
        return Os.rss(pids);
    }

    static String mb(long bytes) {
        var mb = bytes / (1024.0 * 1024);
        return mb >= 1024 ? "%.1f GB".formatted(mb / 1024) : "%.0f MB".formatted(mb);
    }

    private Mem() { }
}
