package fxterm.tabs;

import module java.base;

/** What a launch asks for, in Tabby's own command-line words so callers written for Tabby keep working:
 *  `open [dir]` a shell in dir, `run <command...>` a tab running that command, nothing at all: just show the window.
 *  `--group <label>` right after the verb puts the tab at the end of that group; without it the tab goes to the group of the
 *  tab the launch came from (`from`, that tab's FXTERM_TABS_TAB). */
public final class Request {

    public String kind = "show", cwd, group, from;
    public List<String> argv = new ArrayList<>();

    /** Null when the arguments are not a Tabby-style command (then they are fxterm-tabs options). */
    static Request parse(List<String> args) {
        var r = new Request();
        // a bare launch (Start menu, taskbar) has no folder of its own: the default folder applies
        if (args.isEmpty()) return r;
        r.cwd = System.getProperty("user.dir");
        r.from = System.getenv("FXTERM_TABS_TAB");
        var rest = args.subList(1, args.size());
        switch (args.getFirst()) {
            case "open", "run" -> {
                r.kind = args.getFirst();
                if (!rest.isEmpty() && rest.getFirst().equals("--group")) {
                    if (rest.size() < 2) throw new IllegalArgumentException("--group needs a label: fxterm-tabs " + r.kind + " --group <label> ...");
                    r.group = rest.get(1);
                    rest = rest.subList(2, rest.size());
                }
                if (r.kind.equals("open") && !rest.isEmpty()) r.cwd = Path.of(r.cwd).resolve(rest.getFirst()).toString();
                if (r.kind.equals("run")) r.argv = new ArrayList<>(rest);
            }
            default -> { return null; }
        }
        return r;
    }

    /** The command for a new tab; a plain shell when nothing was given. */
    List<String> command(Options o) {
        return argv.isEmpty() ? ClaudeProbe.tokens(o.shell) : argv;
    }
}
