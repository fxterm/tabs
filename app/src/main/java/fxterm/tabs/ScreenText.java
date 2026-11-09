package fxterm.tabs;

import module java.base;
import com.fasterxml.jackson.databind.node.*;
import com.techsenger.jeditermfx.core.model.TerminalTextBuffer;
import com.techsenger.jeditermfx.core.util.CharUtils;

/** `ctl tail id= [n=40]`: the last n rows the tab printed, scrollback included, as plain text. Read-only, takes the buffer lock only to
 *  copy those n rows, and reads from the end so a long scrollback costs nothing. */
final class ScreenText {

    static void tail(TerminalTab t, int n, ObjectNode out) {
        if (t.separator) throw new IllegalArgumentException("tab " + t.id + " is a group label, not a terminal");
        out.put("version", t.version.get());
        var rows = t.live() ? tail(t.widget.getTerminalTextBuffer(), n) : saved(t, n);
        if (!t.live()) out.put("asleep", true);
        var lines = out.putArray("lines");
        rows.forEach(lines::add);
    }

    /** The last n rows, trailing blanks of each cut, the empty rows below the last printed one left out. */
    static List<String> tail(TerminalTextBuffer b, int n) {
        var rows = new ArrayList<String>();
        b.lock();
        try {
            var screen = b.getScreenBuffer();
            var history = b.getHistoryBuffer();
            var h = history.getLineCount();
            var trailing = true;
            for (var i = h + screen.getLineCount() - 1; i >= 0 && rows.size() < n; i--) {
                var text = text(i >= h ? screen.getLineText(i - h) : history.getLineText(i));
                if (trailing && text.isEmpty()) continue;
                trailing = false;
                rows.add(text);
            }
        } finally {
            b.unlock();
        }
        Collections.reverse(rows);
        return rows;
    }

    /** The screen rows (not the scrollback), one string, a newline after each row: what a `wait` looks in. */
    static String visible(TerminalTextBuffer b) {
        var sb = new StringBuilder();
        b.lock();
        try {
            var screen = b.getScreenBuffer();
            for (var y = 0; y < screen.getLineCount(); y++) sb.append(text(screen.getLineText(y))).append('\n');
        } finally {
            b.unlock();
        }
        return sb.toString();
    }

    static String text(String row) {
        return row.replace(String.valueOf(CharUtils.DWC), "").stripTrailing();
    }

    private static List<String> saved(TerminalTab t, int n) {
        var screen = t.sleeping != null ? t.sleeping.screen : null;
        if (screen == null) return List.of();
        var all = screen.split("\n", -1);
        return Arrays.asList(all).subList(Math.max(0, all.length - n), all.length);
    }

    private ScreenText() { }
}
