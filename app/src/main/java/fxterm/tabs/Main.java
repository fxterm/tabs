package fxterm.tabs;

import module java.base;

/** Plain holder: JavaFX refuses to launch from a shaded jar when main lives on the Application.
 *  `fxterm-tabs open [--group label] [dir]` / `fxterm-tabs run [--group label] <command...>` / no arguments go to an already running window if there is one
 *  (as Tabby's own command line does); --options start a window of their own (tests use --offscreen).
 *  Errors also go to ~/fxterm/tabs/fxterm-tabs.log, because a launch from Explorer has no console to show them. The log
 *  is capped at 10 MB (RotatingLog keeps one older file) and a message repeated back to back is written once, then counted (DedupLog). */
public final class Main {
    static final long MAX_LOG = 10L << 20;

    public static void main(String[] args) throws IOException {
        var list = List.of(args);
        // `fxterm-tabs ctl ...` talks to the running Tabs and prints its JSON reply (see Control)
        if (!list.isEmpty() && list.getFirst().equals("ctl")) System.exit(Control.client(list.subList(1, list.size())));
        logToFile();
        var request = (Request) null;
        try {
            request = Request.parse(list);
        } catch (IllegalArgumentException e) {
            System.out.println("fxterm-tabs: " + e.getMessage());
            System.exit(2);
        }
        // the registered window first; then any running window through its control socket (see Control.join); else a window of its own
        if (request != null && (Instance.forward(request) || Control.join(request))) System.exit(0);
        TabsApp.request = request;
        var options = request == null ? args : new String[0];
        javafx.application.Application.launch(TabsApp.class, options);
    }

    static void logToFile() throws IOException {
        var log = Path.of(System.getProperty("user.home"), "fxterm", "tabs", "fxterm-tabs.log");
        Files.createDirectories(log.getParent());
        System.setErr(new PrintStream(new DedupLog(System.err, new RotatingLog(log, MAX_LOG)), true, StandardCharsets.UTF_8));
        System.err.println("--- fxterm-tabs start " + java.time.LocalDateTime.now());
        Thread.setDefaultUncaughtExceptionHandler((t, e) -> { System.err.println("uncaught in " + t.getName()); e.printStackTrace(); });
    }
}
