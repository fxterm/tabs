package fxterm.tabs;

import module java.base;
import java.lang.foreign.*;
import java.lang.invoke.MethodHandle;

/** The command line as Windows holds it, in UTF-16. Java decodes main's arguments with the ANSI code page, so a 🔔 (or any
 *  character the page lacks) arrives as `??`; `fxterm-tabs ctl rename title=🔔` then names the tab "??". This asks Windows
 *  for the line itself and takes the arguments from its tail. Quotes a shell removed before starting us cannot come back. */
final class WinArgs {

    /** The arguments main got, with the characters the ANSI page lost put back; the same arguments when nothing is lost or the line cannot be read. */
    static String[] of(String[] args) {
        if (!Os.WIN || args.length == 0 || Arrays.stream(args).allMatch(WinArgs::plain)) return args;
        try {
            var all = commandLine();
            if (all.size() < args.length) return args;
            var tail = all.subList(all.size() - args.length, all.size());
            for (var i = 0; i < args.length; i++) if (!skeleton(args[i]).equals(skeleton(tail.get(i)))) return args;
            return tail.toArray(String[]::new);
        } catch (Throwable e) {
            return args;
        }
    }

    static boolean plain(String s) { return s.chars().allMatch(c -> c < 0x80 && c != '?'); }

    /** Only the printable ASCII of an argument, without the `?` that stands in for a lost character: enough to tell it is the same argument. */
    static String skeleton(String s) { return s.replaceAll("[^\\x20-\\x7E]|\\?", ""); }

    static List<String> commandLine() throws Throwable {
        var linker = Linker.nativeLinker();
        var k32 = SymbolLookup.libraryLookup("kernel32", Arena.global());
        var shell = SymbolLookup.libraryLookup("shell32", Arena.global());
        var get = linker.downcallHandle(k32.find("GetCommandLineW").orElseThrow(), FunctionDescriptor.of(ValueLayout.ADDRESS));
        var split = linker.downcallHandle(shell.find("CommandLineToArgvW").orElseThrow(),
                FunctionDescriptor.of(ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.ADDRESS));
        try (var arena = Arena.ofConfined()) {
            var line = ((MemorySegment) get.invoke()).reinterpret(Integer.MAX_VALUE);
            var count = arena.allocate(ValueLayout.JAVA_INT);
            var argv = ((MemorySegment) split.invoke(line, count)).reinterpret(ValueLayout.ADDRESS.byteSize() * count.get(ValueLayout.JAVA_INT, 0));
            var out = new ArrayList<String>();
            for (var i = 0; i < count.get(ValueLayout.JAVA_INT, 0); i++)
                out.add(argv.getAtIndex(ValueLayout.ADDRESS, i).reinterpret(Integer.MAX_VALUE).getString(0, StandardCharsets.UTF_16LE));
            return out;
        }
    }

    private WinArgs() { }
}
