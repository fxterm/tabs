import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.*;

/** Reads frames of the tab list (png files, in order) and says where pixels change from one frame to the next.
 *  A tab bar that is steady changes only inside the animated glyph's slot; any other change is a shake.
 *  Usage: java Shake.java <sidebarWidth> <titleBarHeight> <glyphX0> <glyphX1> <glyphY0> <glyphY1> frame1.png frame2.png ... */
public class Shake {
    /** Pixels at the left and right edge to ignore: the invisible resize border of a window shows what is behind it. */
    static final int INSET = Integer.parseInt(System.getenv().getOrDefault("INSET", "0"));
    public static void main(String[] a) throws Exception {
        int side = Integer.parseInt(a[0]), tb = Integer.parseInt(a[1]), gx0 = Integer.parseInt(a[2]), gx1 = Integer.parseInt(a[3]), gy0 = Integer.parseInt(a[4]), gy1 = Integer.parseInt(a[5]);
        var frames = new ArrayList<BufferedImage>();
        for (int i = 6; i < a.length; i++) frames.add(ImageIO.read(new File(a[i])));
        int bad = 0, inside = 0;
        for (int f = 1; f < frames.size(); f++) {
            var p = frames.get(f - 1); var q = frames.get(f);
            int n = 0, outside = 0, minX = 9999, maxX = -1, minY = 9999, maxY = -1;
            var rows = new TreeMap<Integer, Integer>();
            for (int y = 0; y < Math.min(p.getHeight(), q.getHeight()); y++)
                for (int x = INSET; x < (y < tb ? Math.min(p.getWidth(), q.getWidth()) - INSET : Math.min(side, p.getWidth())); x++)
                    if (p.getRGB(x, y) != q.getRGB(x, y)) {
                        n++;
                        if (x >= gx0 && x < gx1 && y >= gy0 && y < gy1) { inside++; continue; }
                        outside++;
                        minX = Math.min(minX, x); maxX = Math.max(maxX, x); minY = Math.min(minY, y); maxY = Math.max(maxY, y);
                        rows.merge(y / 16 * 16, 1, Integer::sum);
                    }
            if (outside > 0) bad++;
            System.out.printf("frame %2d->%2d: %5d px changed, %5d outside the glyph slot%s%s%n", f, f + 1, n, outside,
                    outside > 0 ? " x " + minX + ".." + maxX + " y " + minY + ".." + maxY : "", outside > 0 ? " rows " + rows : "");
        }
        if (inside == 0 && System.getenv("STILL") == null) { System.out.println("HARNESS: the glyph slot never changed, so no animation was captured"); System.exit(2); }
        System.out.println("animation live: the glyph slot changed " + inside + " px over the frames");
        System.out.println(bad == 0 ? "STEADY: nothing outside the glyph slot moved" : "SHAKY: " + bad + " of " + (frames.size() - 1) + " frame pairs changed outside the glyph slot");
        System.exit(bad == 0 ? 0 : 1);
    }
}
