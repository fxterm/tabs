import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;

/** Does the comet go round the ring's own centre? Over all frames: the box of the blue comet pixels, and the box of
 *  the whole mark (ring included). Going round the ring's centre, the comet's box is centred on the ring's box and
 *  inside it; a comet turning about some other point sits off-centre or reaches outside.
 *  Usage: java Comet.java x0 y0 x1 y1 frame.png ...   (the square holding only the activity mark) */
public class Comet {
    public static void main(String[] a) throws Exception {
        int x0 = Integer.parseInt(a[0]), y0 = Integer.parseInt(a[1]), x1 = Integer.parseInt(a[2]), y1 = Integer.parseInt(a[3]);
        int[] blue = {9999, -1, 9999, -1}, mark = {9999, -1, 9999, -1};
        int seen = 0;
        for (int i = 4; i < a.length; i++) {
            BufferedImage im = ImageIO.read(new File(a[i]));
            int bg = im.getRGB(x0, y0);
            for (int y = y0; y < y1; y++) for (int x = x0; x < x1; x++) {
                int c = im.getRGB(x, y), r = c >> 16 & 255, g = c >> 8 & 255, b = c & 255;
                boolean isBlue = b > r + 50 && b > g + 20;
                boolean differs = Math.abs(r - (bg >> 16 & 255)) > 12 || Math.abs(g - (bg >> 8 & 255)) > 12 || Math.abs(b - (bg & 255)) > 12;
                if (isBlue) { seen++; grow(blue, x, y); }
                if (differs) grow(mark, x, y);
            }
        }
        if (seen == 0) { System.out.println("HARNESS: no blue comet pixels seen"); System.exit(2); }
        double dx = (blue[0] + blue[1]) / 2.0 - (mark[0] + mark[1]) / 2.0, dy = (blue[2] + blue[3]) / 2.0 - (mark[2] + mark[3]) / 2.0;
        System.out.printf("whole mark: x %d..%d y %d..%d   comet over all frames: x %d..%d y %d..%d%n", mark[0], mark[1], mark[2], mark[3], blue[0], blue[1], blue[2], blue[3]);
        System.out.printf("the comet's orbit is off the ring's centre by %.1f px across, %.1f px down%n", dx, dy);
        boolean ok = Math.abs(dx) <= 1 && Math.abs(dy) <= 1 && blue[0] >= mark[0] - 1 && blue[1] <= mark[1] + 1 && blue[2] >= mark[2] - 1 && blue[3] <= mark[3] + 1;
        System.out.println(ok ? "CENTRED: the comet goes round the ring" : "OFF-CENTRE: the comet does not go round the ring's centre");
        System.exit(ok ? 0 : 1);
    }
    static void grow(int[] b, int x, int y) { b[0] = Math.min(b[0], x); b[1] = Math.max(b[1], x); b[2] = Math.min(b[2], y); b[3] = Math.max(b[3], y); }
}
