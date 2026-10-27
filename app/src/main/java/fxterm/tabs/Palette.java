package fxterm.tabs;

import module java.base;
import com.techsenger.jeditermfx.core.Color;
import com.techsenger.jeditermfx.core.emulator.ColorPalette;

/** The 16 ANSI colours of a theme, as the emulator wants them. */
public final class Palette extends ColorPalette {

    final Color[] colors;

    Palette(List<String> hex) {
        colors = hex.stream().map(h -> new Color(TabbyTheme.rgb(h))).toArray(Color[]::new);
    }

    @Override protected Color getForegroundByColorIndex(int i) { return colors[i]; }
    @Override protected Color getBackgroundByColorIndex(int i) { return colors[i]; }
}
