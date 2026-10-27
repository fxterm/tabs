package tabbyfx;

import module java.base;

/** Plain holder: JavaFX refuses to launch from a shaded jar when main lives on the Application.
 *  `tabbyfx open [dir]` / `tabbyfx run <command...>` / no arguments go to an already running window if there is one
 *  (as Tabby's own command line does); --options start a window of their own (tests use --offscreen).
 *  Errors also go to ~/tabbyfx/tabbyfx/tabbyfx.log, because a launch from Explorer has no console to show them. */
public final class Main {
    public static void main(String[] args) throws IOException {
        logToFile();
        var list = List.of(args);
        var request = Request.parse(list);
        if (request != null && Instance.forward(request)) System.exit(0);
        TabbyApp.request = request;
        var options = request == null ? args : new String[0];
        javafx.application.Application.launch(TabbyApp.class, options);
    }

    static void logToFile() throws IOException {
        var log = Path.of(System.getProperty("user.home"), "tabbyfx", "tabbyfx", "tabbyfx.log");
        Files.createDirectories(log.getParent());
        var file = new PrintStream(new FileOutputStream(log.toFile(), true), true, StandardCharsets.UTF_8);
        var console = System.err;
        System.setErr(new PrintStream(new OutputStream() {
            @Override public void write(int b) { console.write(b); file.write(b); }
            @Override public void write(byte[] b, int off, int len) { console.write(b, off, len); file.write(b, off, len); }
        }, true, StandardCharsets.UTF_8));
        System.err.println("--- tabbyfx start " + java.time.LocalDateTime.now());
        Thread.setDefaultUncaughtExceptionHandler((t, e) -> { System.err.println("uncaught in " + t.getName()); e.printStackTrace(); });
    }
}
