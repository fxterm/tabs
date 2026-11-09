package fxterm.tabs;

import module java.base;

/** The folder where a new tab opens when nothing asked for another one: a single setting for the whole app, kept in
 *  ~/fxterm/tabs/folder.json beside font.json and read again whenever that file changes. */
public final class DefaultFolder {

    static final Path FILE = WindowState.FILE.resolveSibling("folder.json");

    public String dir;

    static boolean save = true;
    static String current;
    static long stamp = -1;
    static boolean warned;

    static void init(boolean persist) {
        save = persist;
    }

    /** The folder as saved, even when it no longer exists; null when none is set. */
    static synchronized String saved() {
        if (save) reload();
        return current;
    }

    /** The saved folder when it still exists, else null (a tab then starts in the home folder). */
    static String get() {
        var d = saved();
        if (d == null) return null;
        if (exists(d)) {
            warned = false;
            return d;
        }
        if (!warned) {
            warned = true;
            System.err.println("fxterm-tabs: default folder " + d + " is gone, new tabs start in the home folder");
        }
        return null;
    }

    /** A folder asked for explicitly wins; otherwise the default folder (null: the home folder). */
    static String pick(String explicit) {
        return explicit != null ? explicit : get();
    }

    static boolean exists(String d) {
        try {
            return Files.isDirectory(Path.of(d));
        } catch (InvalidPathException e) {
            return false;
        }
    }

    /** Sets it (blank: none, back to the home folder) and saves it. */
    static synchronized void set(String dir) {
        current = dir == null || dir.isBlank() ? null : dir.strip();
        if (!save) return;
        var f = new DefaultFolder();
        f.dir = current;
        try {
            Files.createDirectories(FILE.getParent());
            WindowState.JSON.writeValue(FILE.toFile(), f);
            stamp = -1;
        } catch (IOException e) {
            System.err.println("fxterm-tabs: default folder not saved: " + e);
        }
    }

    private static void reload() {
        try {
            var t = Files.isReadable(FILE) ? Files.getLastModifiedTime(FILE).toMillis() : 0;
            if (t == stamp) return;
            stamp = t;
            var d = t == 0 ? null : WindowState.JSON.readValue(FILE.toFile(), DefaultFolder.class).dir;
            current = d == null || d.isBlank() ? null : d.strip();
        } catch (IOException e) {
            System.err.println("fxterm-tabs: folder.json unreadable: " + e);
            current = null;
        }
    }

    private DefaultFolder() { }
}
