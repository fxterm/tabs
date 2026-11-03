import javafx.application.Platform;
import javafx.scene.*;
import javafx.scene.image.WritableImage;
import javafx.scene.layout.Pane;
import javafx.scene.paint.Color;
import javafx.scene.shape.*;
import java.util.concurrent.CountDownLatch;

/** Is every pixel of the comet on the ring's band? Draws the indicator at many moments, renders it 8x, and finds the
 *  distance of each blue pixel from the ring's centre. A comet that turns about the ring's centre stays inside the
 *  band (radius 6.4, stroke 1.8: 5.5 to 7.3); one that turns about another point strays outside it.
 *  Also builds the same arcs the first way (Node.setRotate, which pivots on the moving bounds) to show the difference.
 *  Run: java -cp shade/fxterm-tabs.jar test/shake/CometGeometry.java */
public class CometGeometry {
    static final double SIZE = 18, R = 6.4, W = 1.8, SCALE = 8;

    public static void main(String[] a) throws Exception {
        var done = new CountDownLatch(1);
        Platform.startup(() -> {
            try {
                var cls = Class.forName("fxterm.tabs.ActivityMark");
                var ctor = cls.getDeclaredConstructor(); ctor.setAccessible(true);
                var show = cls.getDeclaredMethod("show", Class.forName("fxterm.tabs.TerminalTab"), Color.class, Color.class); show.setAccessible(true);
                var preview = cls.getDeclaredMethod("preview", double.class); preview.setAccessible(true);
                var mark = (javafx.scene.layout.Region) ctor.newInstance();
                show.invoke(mark, null, Color.WHITE, Color.DODGERBLUE);
                report("the indicator as built now", t -> { try { preview.invoke(mark, t); } catch (Exception e) { throw new RuntimeException(e); } }, mark);
                // the first version: three arcs in a Group, turned with setRotate
                var arcs = new Arc[3]; var g = new Group(); var pane = new Pane(g); pane.setPrefSize(SIZE, SIZE);
                var op = new double[] {0.22, 0.5, 1.0};
                for (int i = 0; i < 3; i++) { var arc = new Arc(SIZE / 2, SIZE / 2, R, R, 0, 90); arc.setType(ArcType.OPEN); arc.setFill(null); arc.setStroke(Color.DODGERBLUE);
                    arc.setStrokeWidth(W); arc.setStrokeLineCap(StrokeLineCap.ROUND); arc.setOpacity(op[i]); arcs[i] = arc; g.getChildren().add(arc); }
                report("the first version (setRotate about the moving bounds)", t -> {
                    var sweep = 135 + 65 * Math.sin(2 * Math.PI * t / 1.7); var sc = new double[] {1.0, 0.62, 0.3};
                    for (int i = 0; i < 3; i++) arcs[i].setLength(sweep * sc[i]);
                    g.setRotate(t / 1.15 * 360); }, pane);
            } catch (Exception e) { e.printStackTrace(); }
            done.countDown();
        });
        done.await();
        Platform.exit();
    }

    static void report(String label, java.util.function.DoubleConsumer at, Node node) {
        double rmin = 1e9, rmax = 0;
        for (double t = 0; t < 3.4; t += 0.037) {
            at.accept(t);
            var sp = new SnapshotParameters(); sp.setFill(Color.BLACK); sp.setTransform(javafx.scene.transform.Transform.scale(SCALE, SCALE));
            sp.setViewport(new javafx.geometry.Rectangle2D(-6 * SCALE, -6 * SCALE, (SIZE + 12) * SCALE, (SIZE + 12) * SCALE));
            var im = new Scene(new Group()); // keeps the toolkit happy
            WritableImage img = node.snapshot(sp, null);
            var pr = img.getPixelReader();
            double cx = (SIZE / 2 + 6) * SCALE;
            for (int y = 0; y < img.getHeight(); y++) for (int x = 0; x < img.getWidth(); x++) {
                var c = pr.getColor(x, y);
                if (c.getBlue() > c.getRed() + 0.3 && c.getBlue() > c.getGreen() + 0.1) {
                    double d = Math.hypot(x + 0.5 - cx, y + 0.5 - cx) / SCALE;
                    rmin = Math.min(rmin, d); rmax = Math.max(rmax, d);
                }
            }
        }
        boolean ok = rmin >= 5.0 && rmax <= 7.8;
        System.out.printf("%-58s comet radius %.2f .. %.2f px (band 5.5 .. 7.3)  %s%n", label, rmin, rmax, ok ? "ON THE RING" : "STRAYS OFF THE RING");
    }
}
