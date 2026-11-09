package fxterm.tabs;

import module java.base;
import javafx.scene.image.*;
import javafx.scene.paint.Color;

/** What tells one window from another: a colour of its own (from this palette, never one that an open window already
 *  wears) and the taskbar icon painted in it. The colour also tints the window's title bar and edge (TitleBar). */
final class WindowLook {

    /** Eight colours far enough apart to tell at a glance on a taskbar. */
    static final List<String> PALETTE = List.of("#e5534b", "#3fb950", "#d29922", "#58a6ff", "#bc8cff", "#39c5cf", "#f778ba", "#ff8c42");
    /** What windows.json holds for "no colour"; a null there is a window saved before this existed (a free colour is chosen). */
    static final String NONE = "none";
    static final List<String> NAMES = List.of("Red", "Green", "Amber", "Blue", "Violet", "Cyan", "Pink", "Orange");

    private static final Map<String, Image> ICONS = new HashMap<>();

    /** The first palette colour no window in `taken` wears; when all are taken, they are reused in turn. */
    static String free(Collection<String> taken) {
        for (var c : PALETTE) if (!taken.contains(c)) return c;
        return PALETTE.get(taken.size() % PALETTE.size());
    }

    /** The application icon repainted in the colour: its dark parts and greys take the colour (a dark shade of it for
     *  the background), its white parts (the prompt and the title line) stay white, so it still reads as the same icon. */
    static Image icon(String resource, String hex) {
        var plain = new Image(TabsApp.class.getResourceAsStream(resource));
        return hex == null ? plain : ICONS.computeIfAbsent(resource + hex, k -> loaded(tint(plain, Color.web(hex))));
    }

    /** On Windows JavaFX silently ignores a WritableImage among a stage's icons (the taskbar keeps the generic glyph, with
     *  no error), so a drawn image goes through PNG in memory and comes back from the image loader. */
    static Image loaded(WritableImage drawn) {
        try {
            var bytes = new ByteArrayOutputStream();
            javax.imageio.ImageIO.write(javafx.embed.swing.SwingFXUtils.fromFXImage(drawn, null), "png", bytes);
            return new Image(new ByteArrayInputStream(bytes.toByteArray()));
        } catch (IOException e) {
            return drawn;
        }
    }

    static WritableImage tint(Image src, Color colour) {
        var w = (int) src.getWidth();
        var h = (int) src.getHeight();
        var out = new WritableImage(w, h);
        var in = src.getPixelReader();
        var write = out.getPixelWriter();
        for (var y = 0; y < h; y++) {
            for (var x = 0; x < w; x++) {
                var p = in.getColor(x, y);
                if (p.getOpacity() == 0) {
                    write.setColor(x, y, p);
                    continue;
                }
                var l = 0.2126 * p.getRed() + 0.7152 * p.getGreen() + 0.0722 * p.getBlue();
                // how much of the pixel turns to the colour: all of it for the dark and grey parts, none for the white
                var k = 1 - smooth(0.62, 0.92, l);
                var f = Math.min(1, 0.30 + l * 1.15);
                var tinted = new Color(colour.getRed() * f, colour.getGreen() * f, colour.getBlue() * f, p.getOpacity());
                write.setColor(x, y, p.interpolate(tinted, k));
            }
        }
        return out;
    }

    private static double smooth(double a, double b, double v) {
        var t = Math.clamp((v - a) / (b - a), 0, 1);
        return t * t * (3 - 2 * t);
    }

    private WindowLook() { }
}
