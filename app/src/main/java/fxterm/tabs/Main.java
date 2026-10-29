package fxterm.tabs;

import module java.base;

/** Plain holder: JavaFX refuses to launch from a shaded jar when main lives on the Application.
 *  `fxterm-tabs open [dir]` / `fxterm-tabs run <command...>` / no arguments go to an already running window if there is one
 *  (as Tabby's own command line does); --options start a window of their own (tests use --offscreen).
 *  Errors also go to ~/fxterm/tabs/fxterm-tabs.log, because a launch from Explorer has no console to show them. */
public final class Main {
    public static void main(String[] args) throws IOException {
        var list = List.of(args);
        // `fxterm-tabs ctl ...` talks to the running Tabs and prints its JSON reply (see Control)
        if (!list.isEmpty() && list.getFirst().equals("ctl")) System.exit(Control.client(list.subList(1, list.size())));
        logToFile();
        var request = Request.parse(list);
        if (request != null && Instance.forward(request)) System.exit(0);
        TabsApp.request = request;
        var options = request == null ? args : new String[0];
        javafx.application.Application.launch(TabsApp.class, options);
    }

    static void logToFile() throws IOException {
        var log = Path.of(System.getProperty("user.home"), "fxterm", "tabs", "fxterm-tabs.log");
        Files.createDirectories(log.getParent());
        var file = new PrintStream(new FileOutputStream(log.toFile(), true), true, StandardCharsets.UTF_8);
        var console = System.err;
        System.setErr(new PrintStream(new OutputStream() {
            @Override public void write(int b) { console.write(b); file.write(b); }
            @Override public void write(byte[] b, int off, int len) { console.write(b, off, len); file.write(b, off, len); }
        }, true, StandardCharsets.UTF_8));
        System.err.println("--- fxterm-tabs start " + java.time.LocalDateTime.now());
        Thread.setDefaultUncaughtExceptionHandler((t, e) -> { System.err.println("uncaught in " + t.getName()); e.printStackTrace(); });
    }
}
