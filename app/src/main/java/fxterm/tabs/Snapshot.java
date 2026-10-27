package fxterm.tabs;

import module java.base;
import java.awt.image.BufferedImage;
import javafx.application.Platform;
import javafx.scene.Scene;

/** Renders the window's own scene to a PNG after a delay. Works while the window is off-screen, which a desktop
 *  capture cannot see, so unattended tests can check what the screen looks like without showing it to anyone. */
public final class Snapshot {

    static void later(Scene scene, String file, long waitMs) {
        Thread.ofPlatform().daemon().start(() -> {
            try { Thread.sleep(waitMs); } catch (InterruptedException e) { return; }
            Platform.runLater(() -> write(scene, file));
        });
    }

    static void write(Scene scene, String file) {
        var img = scene.snapshot(null);
        var w = (int) img.getWidth();
        var h = (int) img.getHeight();
        var out = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        var px = img.getPixelReader();
        for (var y = 0; y < h; y++) for (var x = 0; x < w; x++) out.setRGB(x, y, px.getArgb(x, y));
        try {
            javax.imageio.ImageIO.write(out, "png", new File(file));
        } catch (IOException e) {
            System.err.println("fxterm-tabs: snapshot failed: " + e);
        }
    }
}
