package fxterm.tabs;

import module java.base;
import java.lang.foreign.*;
import java.lang.invoke.MethodHandle;

/** Colours a window's own title bar (the strip with minimise/close) and its edge through DWM: dark mode for the
 *  buttons, and on Windows 11 the caption, its text and the border. JavaFX has no API for it. Each window gets its own
 *  colour (the window's colour mixed into the terminal's), so windows tell apart in the taskbar thumbnails and when
 *  stacked. The native window is found by its title: the title is set to a unique token for an instant, the window with
 *  that title is looked up, and the real title is put back (all on the FX thread, so nothing is drawn in between). */
public final class TitleBar {

    static final int DARK_MODE = 20, BORDER_COLOR = 34, CAPTION_COLOR = 35, TEXT_COLOR = 36;

    /** The native window becomes visible a moment after show(); try a few times until it is found. The call runs on
     *  the FX thread, which owns the window: made from another thread, Windows accepted it and drew nothing. */
    static void paintSoon(TabsWindow w) {
        if (!Os.WIN) return;
        Thread.ofPlatform().daemon().start(() -> {
            for (var wait : new long[] {150, 400, 800, 1500}) {
                try { Thread.sleep(wait); } catch (InterruptedException e) { return; }
                var found = new java.util.concurrent.CompletableFuture<Boolean>();
                javafx.application.Platform.runLater(() -> found.complete(paint(w)));
                if (found.join()) return;
            }
        });
    }

    /** Colours this window's title bar and edge now (on the FX thread); false when its native window is not there yet. */
    static boolean paint(TabsWindow w) {
        if (!Os.WIN) return true;
        var real = w.stage.getTitle();
        var token = "tabs-look-" + Long.toHexString(System.nanoTime());
        w.stage.setTitle(token);
        try (var arena = Arena.ofConfined()) {
            var linker = Linker.nativeLinker();
            var user32 = SymbolLookup.libraryLookup("user32", arena);
            var dwm = SymbolLookup.libraryLookup("dwmapi", arena);
            var find = linker.downcallHandle(user32.find("FindWindowExW").orElseThrow(), FunctionDescriptor.of(ValueLayout.ADDRESS,
                    ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.ADDRESS));
            var set = linker.downcallHandle(dwm.find("DwmSetWindowAttribute").orElseThrow(), FunctionDescriptor.of(ValueLayout.JAVA_INT,
                    ValueLayout.ADDRESS, ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.JAVA_INT));
            var title = arena.allocateFrom(token, java.nio.charset.StandardCharsets.UTF_16LE);
            var hwnd = (MemorySegment) find.invoke(MemorySegment.NULL, MemorySegment.NULL, MemorySegment.NULL, title);
            if (hwnd.address() == 0) return false;
            apply(set, arena, hwnd, w.theme, w.colour);
            System.err.println("fxterm-tabs: title bar of '" + w.windowLabel() + "' coloured " + w.colour);
            return true;
        } catch (Throwable e) {
            System.err.println("fxterm-tabs: title bar colour not applied: " + e);
            return true;
        } finally {
            w.stage.setTitle(real);
        }
    }

    static void apply(MethodHandle set, Arena arena, MemorySegment hwnd, TabbyTheme theme, String colour) throws Throwable {
        var caption = colour == null ? theme.background : Ui.mix(theme.background, colour, 0.5);
        var text = colour == null ? theme.foreground : Ui.textOn(caption);
        var border = colour == null ? theme.background : colour;
        for (var a : new int[][] {{DARK_MODE, 1}, {CAPTION_COLOR, colorref(caption)}, {TEXT_COLOR, colorref(text)}, {BORDER_COLOR, colorref(border)}}) {
            var v = arena.allocate(ValueLayout.JAVA_INT);
            v.set(ValueLayout.JAVA_INT, 0, a[1]);
            var hr = (int) set.invoke(hwnd, a[0], v, 4);
            if (hr != 0) System.err.println("fxterm-tabs: DwmSetWindowAttribute(" + a[0] + ") = 0x" + Integer.toHexString(hr));
        }
    }

    /** Where the mouse is now, in JavaFX screen units (Windows reports device pixels; the primary screen's scale turns
     *  them back). A drag that ended outside every window asks this to place the new window. */
    static double[] cursor() {
        if (!Os.WIN) return null;
        try (var arena = Arena.ofConfined()) {
            var get = Linker.nativeLinker().downcallHandle(SymbolLookup.libraryLookup("user32", arena).find("GetCursorPos").orElseThrow(),
                    FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS));
            var point = arena.allocate(8);
            if ((int) get.invoke(point) == 0) return null;
            var scale = javafx.stage.Screen.getPrimary().getOutputScaleX();
            return new double[] {point.get(ValueLayout.JAVA_INT, 0) / scale, point.get(ValueLayout.JAVA_INT, 4) / scale};
        } catch (Throwable e) {
            System.err.println("fxterm-tabs: cursor position unknown: " + e);
            return null;
        }
    }

    /** Windows wants 0x00BBGGRR. */
    static int colorref(String hex) {
        var rgb = TabbyTheme.rgb(hex);
        return ((rgb & 0xff) << 16) | (rgb & 0xff00) | ((rgb >> 16) & 0xff);
    }

    private TitleBar() { }
}
