package fxterm.tabs;

import module java.base;
import com.fasterxml.jackson.databind.node.*;

/** `ctl screen id= [since=<version>]`: the tab's visible screen with its styling, as structured data for another app to paint.
 *  Read-only: it never types, resizes or scrolls, and takes the buffer lock only for the copy of ~rows x cols cells. The
 *  version is the tab's change counter (TerminalTab.version): asking with since=<version> costs a number compare. */
final class ScreenView {

    static void read(TerminalTab t, long since, ObjectNode out) {
        if (t.separator) throw new IllegalArgumentException("tab " + t.id + " is a group label, not a terminal");
        var version = t.version.get();
        out.put("version", version);
        if (!t.live()) out.put("asleep", true);
        if (version == since) { out.put("unchanged", true); return; }
        var theme = t.theme;
        out.putObject("default").put("fg", theme.foreground).put("bg", theme.background);
        if (t.live()) live(t, theme, out); else asleep(t, out);
    }

    static void live(TerminalTab t, TabbyTheme theme, ObjectNode out) {
        var panel = t.widget.getTerminalPanel();
        var buffer = t.widget.getTerminalTextBuffer();
        var cursor = panel.cursorCell();
        buffer.lock();
        try {
            var rows = buffer.getHeight();
            out.put("cols", buffer.getWidth()).put("rows", rows).put("history", buffer.getHistoryLinesCount()).put("alt", buffer.isUsingAlternateBuffer());
            out.putObject("cursor").put("x", cursor[0]).put("y", cursor[1]).put("visible", panel.cursorShown);
            var runs = new ScreenRuns(theme, rows, out.putArray("lines"));
            buffer.processScreenLines(0, rows, runs);
            runs.finish();
        } finally {
            buffer.unlock();
        }
    }

    /** A sleeping tab has no screen buffer: its saved text, without styling. */
    static void asleep(TerminalTab t, ObjectNode out) {
        var saved = t.sleeping != null ? t.sleeping.screen : null;
        var lines = out.putArray("lines");
        var cols = 0;
        if (saved != null) for (var line : saved.split("\n", -1)) {
            lines.addArray().addObject().put("x", 0).put("t", line);
            cols = Math.max(cols, line.length());
        }
        out.put("cols", cols).put("rows", lines.size());
    }

    private ScreenView() { }
}
