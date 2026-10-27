package tabbyfx;

import module java.base;
import java.lang.foreign.*;
import java.lang.invoke.MethodHandle;

/** Colours the window's own title bar (the strip with minimise/close) to match the terminal, through DWM:
 *  dark mode for the buttons, and on Windows 11 the caption and text colour themselves. JavaFX has no API for it.
 *  The window is found by walking the top-level windows for the visible one owned by this process. */
public final class TitleBar {

    static final int DARK_MODE = 20, CAPTION_COLOR = 35, TEXT_COLOR = 36;

    /** The native window becomes visible a moment after show(); try a few times until it is found. The call runs on
     *  the FX thread, which owns the window: made from another thread, Windows accepted it and drew nothing. */
    static void paintSoon(TabbyTheme theme) {
        Thread.ofPlatform().daemon().start(() -> {
            for (var wait : new long[] {150, 400, 800, 1500}) {
                try { Thread.sleep(wait); } catch (InterruptedException e) { return; }
                var found = new java.util.concurrent.CompletableFuture<Integer>();
                javafx.application.Platform.runLater(() -> found.complete(paint(theme)));
                if (found.join() > 0) return;
            }
        });
    }

    static int paint(TabbyTheme theme) {
        try (var arena = Arena.ofConfined()) {
            var linker = Linker.nativeLinker();
            var user32 = SymbolLookup.libraryLookup("user32", arena);
            var dwm = SymbolLookup.libraryLookup("dwmapi", arena);
            var next = linker.downcallHandle(user32.find("FindWindowExW").orElseThrow(), FunctionDescriptor.of(ValueLayout.ADDRESS,
                    ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.ADDRESS));
            var owner = linker.downcallHandle(user32.find("GetWindowThreadProcessId").orElseThrow(),
                    FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.ADDRESS));
            var visible = linker.downcallHandle(user32.find("IsWindowVisible").orElseThrow(),
                    FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS));
            var set = linker.downcallHandle(dwm.find("DwmSetWindowAttribute").orElseThrow(), FunctionDescriptor.of(ValueLayout.JAVA_INT,
                    ValueLayout.ADDRESS, ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.JAVA_INT));
            var pid = (int) ProcessHandle.current().pid();
            var pidOut = arena.allocate(ValueLayout.JAVA_INT);
            var count = 0;
            var hwnd = MemorySegment.NULL;
            while (true) {
                hwnd = (MemorySegment) next.invoke(MemorySegment.NULL, hwnd, MemorySegment.NULL, MemorySegment.NULL);
                if (hwnd.address() == 0) break;
                var ignored = (int) owner.invoke(hwnd, pidOut);
                if (pidOut.get(ValueLayout.JAVA_INT, 0) == pid && (int) visible.invoke(hwnd) != 0) {
                    apply(set, arena, hwnd, theme);
                    count++;
                }
            }
            if (count > 0) System.err.println("tabbyfx: title bar coloured");
            return count;
        } catch (Throwable e) {
            System.err.println("tabbyfx: title bar colour not applied: " + e);
            return -1;
        }
    }

    static void apply(MethodHandle set, Arena arena, MemorySegment hwnd, TabbyTheme theme) throws Throwable {
        for (var a : new int[][] {{DARK_MODE, 1}, {CAPTION_COLOR, colorref(theme.background)}, {TEXT_COLOR, colorref(theme.foreground)}}) {
            var v = arena.allocate(ValueLayout.JAVA_INT);
            v.set(ValueLayout.JAVA_INT, 0, a[1]);
            var hr = (int) set.invoke(hwnd, a[0], v, 4);
            if (hr != 0) System.err.println("tabbyfx: DwmSetWindowAttribute(" + a[0] + ") = 0x" + Integer.toHexString(hr));
        }
    }

    /** Windows wants 0x00BBGGRR. */
    static int colorref(String hex) {
        var rgb = TabbyTheme.rgb(hex);
        return ((rgb & 0xff) << 16) | (rgb & 0xff00) | ((rgb >> 16) & 0xff);
    }

    private TitleBar() { }
}
