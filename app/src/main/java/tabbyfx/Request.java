package tabbyfx;

import module java.base;

/** What a launch asks for, in Tabby's own command-line words so callers written for Tabby keep working:
 *  `open [dir]` a shell in dir, `run <command...>` a tab running that command, nothing at all: just show the window. */
public final class Request {

    public String kind = "show", cwd;
    public List<String> argv = new ArrayList<>();

    /** Null when the arguments are not a Tabby-style command (then they are tabbyfx options). */
    static Request parse(List<String> args) {
        var r = new Request();
        r.cwd = System.getProperty("user.dir");
        if (args.isEmpty()) return r;
        switch (args.getFirst()) {
            case "open" -> {
                r.kind = "open";
                if (args.size() > 1) r.cwd = Path.of(r.cwd).resolve(args.get(1)).toString();
            }
            case "run" -> {
                r.kind = "run";
                r.argv = new ArrayList<>(args.subList(1, args.size()));
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
