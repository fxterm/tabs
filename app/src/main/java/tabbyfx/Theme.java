package tabbyfx;

import com.techsenger.jeditermfx.core.*;
import com.techsenger.jeditermfx.core.emulator.ColorPalette;
import com.techsenger.jeditermfx.ui.settings.DefaultSettingsProvider;
import javafx.scene.text.Font;

/** The widget's settings: Tabby's colours, a capped scrollback, no bell. */
public final class Theme extends DefaultSettingsProvider {

    final double size;
    final String family;
    final int scrollback, fps;
    final TerminalColor fg, bg;
    final ColorPalette palette;

    Theme(TabbyTheme t, Options o) {
        this.family = o.family;
        this.size = o.font > 0 ? o.font : t.fontSize;
        this.scrollback = o.scrollback;
        this.fps = o.fps;
        this.fg = color(t.foreground);
        this.bg = color(t.background);
        this.palette = new Palette(t.colors);
    }

    static TerminalColor color(String hex) {
        var rgb = TabbyTheme.rgb(hex);
        return new TerminalColor((rgb >> 16) & 0xff, (rgb >> 8) & 0xff, rgb & 0xff);
    }

    @Override public Font getTerminalFont() { return Font.font(family, size); }
    @Override public float getTerminalFontSize() { return (float) size; }
    @Override public ColorPalette getTerminalColorPalette() { return palette; }
    @Override public TextStyle getDefaultStyle() { return new TextStyle(fg, bg); }
    @Override public TerminalColor getDefaultBackground() { return bg; }
    @Override public TerminalColor getDefaultForeground() { return fg; }
    @Override public boolean useInverseSelectionColor() { return false; }
    @Override public TextStyle getSelectionColor() { return new TextStyle(new TerminalColor(255, 255, 255), new TerminalColor(38, 79, 120)); }
    @Override public int getBufferMaxLinesCount() { return scrollback; }
    @Override public boolean audibleBell() { return false; }
    @Override public int maxRefreshRate() { return fps; }
    @Override public boolean copyOnSelect() { return true; }
}
