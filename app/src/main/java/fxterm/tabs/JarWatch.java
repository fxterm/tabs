package fxterm.tabs;

import module java.base;

/** Notices that the jar this window runs from was replaced on disk. A running JVM reads classes and resources (JavaFX's
 *  shaders among them) from that file as it goes, so a jar copied over it makes every later read fail: the log filled
 *  with 339,221 `Error loading D3D shader object` caused by `ZipException: ZipFile invalid LOC header`. The window cannot
 *  repair that; it says so once, in the log and in the footer, and the user restarts it (restart.cmd / swap.ps1). */
final class JarWatch {
    static final String MESSAGE = "Restart Tabs: the jar it runs from was replaced";
    static final long EVERY = 5_000;
    static volatile boolean changed;

    static void start(Runnable onChange) {
        var jar = jar();
        if (jar == null) return;
        var was = stamp(jar);
        Thread.ofPlatform().daemon().name("jar-watch").start(() -> {
            while (!changed) {
                try { Thread.sleep(EVERY); } catch (InterruptedException e) { return; }
                if (!stamp(jar).equals(was)) {
                    changed = true;
                    System.err.println("fxterm-tabs: " + jar + " was replaced while this window runs from it (" + was + " -> " + stamp(jar)
                            + "). Classes and resources not loaded yet will fail to load; restart Tabs.");
                    onChange.run();
                }
            }
        });
    }

    static Path jar() {
        try {
            var p = Path.of(Main.class.getProtectionDomain().getCodeSource().getLocation().toURI());
            return Files.isRegularFile(p) && p.toString().endsWith(".jar") ? p : null;
        } catch (Exception e) {
            return null;
        }
    }

    static String stamp(Path jar) {
        try {
            return Files.size(jar) + " bytes, " + Files.getLastModifiedTime(jar).toInstant();
        } catch (IOException e) {
            return "missing";
        }
    }
}
