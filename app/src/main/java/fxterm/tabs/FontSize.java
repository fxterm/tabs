package fxterm.tabs;

import module java.base;

/** The terminals' font size: one for every tab and window. Ctrl+= / Ctrl+- / Ctrl+0, Ctrl+wheel and pinch change
 *  it; kept in ~/fxterm/tabs/font.json, so it outlives a restart. */
public final class FontSize {

    static final Path FILE = WindowState.FILE.resolveSibling("font.json");
    static final double MIN = 8, MAX = 40, STEP = 1;

    public double size;

    /** The size from the command line or the theme, used when nothing was saved and by Ctrl+0. */
    static double base = 13.5;
    static double current;
    static boolean save = true;

    static void init(double fallback, boolean persist) {
        base = fallback;
        save = persist;
        current = base;
        try {
            if (persist && Files.isReadable(FILE)) {
                var s = WindowState.JSON.readValue(FILE.toFile(), FontSize.class).size;
                if (s >= MIN && s <= MAX) current = s;
            }
        } catch (IOException e) {
            System.err.println("fxterm-tabs: font.json unreadable: " + e);
        }
    }

    static double get() {
        return current;
    }

    /** Sets it within MIN..MAX, rounded to half points; true when it changed. */
    static boolean set(double size) {
        var s = Math.clamp(Math.round(size * 2) / 2.0, MIN, MAX);
        if (s == current) return false;
        current = s;
        if (save) {
            var f = new FontSize();
            f.size = s;
            try {
                Files.createDirectories(FILE.getParent());
                WindowState.JSON.writeValue(FILE.toFile(), f);
            } catch (IOException e) {
                System.err.println("fxterm-tabs: font size not saved: " + e);
            }
        }
        return true;
    }
}
