package fxterm.tabs;

import module java.base;

/** Command line, all "--name value" pairs. Flat on purpose: this is an experiment.
 *  Terminal: --tabs N --shell CMD --scrollback N --font N (default: Tabby's) --family F --fps N --theme tabby|builtin
 *  Session: --restore true|false (reopen the last session's tabs asleep) --state FILE (session file) --cwd DIR (for new tabs) --quit-after MS (tests) --wake-all true (start every restored tab now)
 *  Sleep: --hibernate-free-mb N (0 = never; sleep idle hidden claude tabs when free RAM is below this) --hibernate-idle-min N
 *  Window: --offscreen true (unattended tests: off the visible desktop and out of the taskbar) --snapshot FILE.png --snapshot-wait MS
 *  Bench: --bench N (keystrokes) --bench-wait MS --bench-gap MS --bench-label S --bench-out FILE --bench-exit true
 *         --bench-prompt TEXT (typed and sent first) --bench-prompt-wait MS --bench-dump FILE (buffer text at the end) */
public final class Options {

    int tabs = 1, scrollback = 5000, fps = 30, bench = 0, hibernateFreeMb = 2048, hibernateIdleMin = 30;
    long benchWait = 20_000, benchGap = 150, benchPromptWait = 3_000, snapshotWait = 8_000, quitAfter = 0;
    double font = 0;
    boolean offscreen, benchExit, restore = true, wakeAll, testKeys, testWake, testWakeAll, testMenu, testCompose, testEsc, serve;
    String shell = "cmd.exe", family = "Consolas", theme = "tabby", benchLabel = "run", benchOut, benchPrompt, benchDump, state, snapshot, cwd, testLook;

    static Options parse(List<String> args) {
        var o = new Options();
        for (var i = 0; i + 1 < args.size(); i += 2) {
            var v = args.get(i + 1);
            switch (args.get(i)) {
                case "--tabs" -> o.tabs = Integer.parseInt(v);
                case "--scrollback" -> o.scrollback = Integer.parseInt(v);
                case "--font" -> o.font = Double.parseDouble(v);
                case "--shell" -> o.shell = v;
                case "--family" -> o.family = v;
                case "--fps" -> o.fps = Integer.parseInt(v);
                case "--theme" -> o.theme = v;
                case "--restore" -> o.restore = Boolean.parseBoolean(v);
                case "--state" -> o.state = v;
                case "--hibernate-free-mb" -> o.hibernateFreeMb = Integer.parseInt(v);
                case "--hibernate-idle-min" -> o.hibernateIdleMin = Integer.parseInt(v);
                case "--offscreen" -> o.offscreen = Boolean.parseBoolean(v);
                case "--bench" -> o.bench = Integer.parseInt(v);
                case "--bench-wait" -> o.benchWait = Long.parseLong(v);
                case "--bench-gap" -> o.benchGap = Long.parseLong(v);
                case "--bench-label" -> o.benchLabel = v;
                case "--bench-out" -> o.benchOut = v;
                case "--bench-exit" -> o.benchExit = Boolean.parseBoolean(v);
                case "--bench-prompt" -> o.benchPrompt = v;
                case "--bench-prompt-wait" -> o.benchPromptWait = Long.parseLong(v);
                case "--bench-dump" -> o.benchDump = v;
                case "--snapshot" -> o.snapshot = v;
                case "--cwd" -> o.cwd = v;
                case "--wake-all" -> o.wakeAll = Boolean.parseBoolean(v);
                case "--test-keys" -> o.testKeys = Boolean.parseBoolean(v);
                case "--test-wake" -> o.testWake = Boolean.parseBoolean(v);
                case "--test-wake-all" -> o.testWakeAll = Boolean.parseBoolean(v);
                case "--test-menu" -> o.testMenu = Boolean.parseBoolean(v);
                case "--test-look" -> o.testLook = v;
                case "--test-compose" -> o.testCompose = Boolean.parseBoolean(v);
                case "--test-esc" -> o.testEsc = Boolean.parseBoolean(v);
                case "--serve" -> o.serve = Boolean.parseBoolean(v);
                case "--quit-after" -> o.quitAfter = Long.parseLong(v);
                case "--snapshot-wait" -> o.snapshotWait = Long.parseLong(v);
                default -> throw new IllegalArgumentException("unknown option " + args.get(i));
            }
        }
        return o;
    }
}
