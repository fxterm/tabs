package fxterm.tabs;

import module java.base;
import com.fasterxml.jackson.databind.node.*;
import com.techsenger.jeditermfx.core.*;
import com.techsenger.jeditermfx.core.model.CharBuffer;
import com.techsenger.jeditermfx.core.util.CharUtils;

/** Reads the screen lines of a text buffer into runs of equal style, one array of runs per row, with every colour resolved
 *  to the hex the terminal paints with (the same palette, the same inverse and dim rules as the widget). */
final class ScreenRuns implements StyledTextConsumer {

    final int defFg, defBg;
    final int[] palette;
    final ArrayNode[] rows;
    final ObjectNode[] open;
    final StringBuilder[] text;
    final String[] keys;

    ScreenRuns(TabbyTheme theme, int rowCount, ArrayNode out) {
        defFg = TabbyTheme.rgb(theme.foreground);
        defBg = TabbyTheme.rgb(theme.background);
        palette = theme.colors.stream().mapToInt(TabbyTheme::rgb).toArray();
        rows = new ArrayNode[rowCount];
        open = new ObjectNode[rowCount];
        text = new StringBuilder[rowCount];
        keys = new String[rowCount];
        for (var y = 0; y < rowCount; y++) { rows[y] = out.addArray(); text[y] = new StringBuilder(); }
    }

    static String hex(int rgb) { return "#%06x".formatted(rgb & 0xffffff); }

    int rgb(TerminalColor c, int fallback) {
        if (c == null) return fallback;
        if (c.isIndexed()) return c.getColorIndex() < palette.length ? palette[c.getColorIndex()] : fallback;
        return c.toColor().getRGB() & 0xffffff;
    }

    static int mix(int a, int b) {
        return (((a >> 16 & 0xff) + (b >> 16 & 0xff)) / 2 << 16) | (((a >> 8 & 0xff) + (b >> 8 & 0xff)) / 2 << 8) | (((a & 0xff) + (b & 0xff)) / 2);
    }

    @Override public void consume(int x, int y, TextStyle style, CharBuffer chars, int startRow) {
        if (y < 0 || y >= rows.length) return;
        var underline = style instanceof HyperlinkStyle;
        if (style instanceof HyperlinkStyle h && h.getOriginalStyle() != null) style = h.getOriginalStyle();
        var inv = style.hasOption(TextStyle.Option.INVERSE);
        var fg = rgb(style.getForeground(), defFg);
        var bg = rgb(style.getBackground(), defBg);
        if (inv) { var s = fg; fg = bg; bg = s; }
        if (style.hasOption(TextStyle.Option.DIM)) fg = mix(fg, bg);
        var key = fg + "/" + bg + "/" + style.hasOption(TextStyle.Option.BOLD) + style.hasOption(TextStyle.Option.ITALIC)
                + (underline || style.hasOption(TextStyle.Option.UNDERLINED)) + inv;
        if (!key.equals(keys[y])) {
            flush(y);
            keys[y] = key;
            var run = open[y] = Control.JSON.createObjectNode().put("x", x);
            if (fg != defFg) run.put("fg", hex(fg));
            if (bg != defBg) run.put("bg", hex(bg));
            if (style.hasOption(TextStyle.Option.BOLD)) run.put("b", true);
            if (style.hasOption(TextStyle.Option.ITALIC)) run.put("i", true);
            if (underline || style.hasOption(TextStyle.Option.UNDERLINED)) run.put("u", true);
            if (inv) run.put("inv", true);
        }
        var buf = chars.getBuf();
        for (var i = chars.getStart(); i < chars.getStart() + chars.length(); i++) {
            var c = buf[i];
            if (c == CharUtils.DWC) continue;
            text[y].append(c < ' ' ? ' ' : c);
        }
    }

    @Override public void consumeNul(int x, int y, int nulIndex, TextStyle style, CharBuffer chars, int startRow) {
        consume(x, y, style, new CharBuffer(' ', chars.length()), startRow);
    }

    @Override public void consumeQueue(int x, int y, int nulIndex, int startRow) { }

    private void flush(int y) {
        var run = open[y];
        if (run == null) return;
        run.put("t", text[y].toString());
        rows[y].add(run);
        open[y] = null;
        text[y].setLength(0);
    }

    /** Ends every row: the last run is written, and blanks at the end that show nothing (no background, no underline) are cut. */
    void finish() {
        for (var y = 0; y < rows.length; y++) {
            flush(y);
            var row = rows[y];
            while (!row.isEmpty()) {
                var last = (ObjectNode) row.get(row.size() - 1);
                if (last.has("bg") || last.has("u")) break;
                var t = last.get("t").asText().stripTrailing();
                if (t.isEmpty()) row.remove(row.size() - 1); else { last.put("t", t); break; }
            }
        }
    }
}
