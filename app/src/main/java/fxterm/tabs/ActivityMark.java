package fxterm.tabs;

import javafx.animation.Animation;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.scene.Group;
import javafx.scene.layout.Region;
import javafx.scene.paint.Color;
import javafx.scene.shape.Arc;
import javafx.scene.shape.ArcType;
import javafx.scene.shape.Circle;
import javafx.scene.shape.StrokeLineCap;
import javafx.util.Duration;

/** What a program's animated title symbol stands for, drawn as a shape instead of a character. Programs (claude, npm,
 *  pip, ...) cycle through braille dots, stars, | / - \ and the like; those characters differ in width and baseline
 *  from one frame to the next and come from whatever font has them, so in text they made the row twitch.
 *
 *  Working: a thin ring with a comet sweeping round it, its tail fading out behind, the tail growing and shrinking
 *  as it turns (the indeterminate progress ring of modern interfaces). Not working: one quiet dot. The two cross-fade
 *  over a quarter of a second. Built of vector arcs, so it is as sharp at 125 % or 200 % as at 100 %, and its size
 *  never changes: nothing outside the 18 x 18 square is touched. */
final class ActivityMark extends Region {

    static final double SIZE = 18, RADIUS = 6.4, WIDTH = 1.8;
    /** One turn in this many seconds, and how often the tail breathes. */
    static final double TURN = 1.15, BREATH = 1.7;

    private TerminalTab tab;
    private final Circle track = new Circle(SIZE / 2, SIZE / 2, RADIUS);
    private final Circle dot = new Circle(SIZE / 2, SIZE / 2, 2.4);
    private final Arc[] tail = new Arc[3];
    private final Group comet = new Group();
    /** Turns about the ring's centre. (Node.setRotate turns about the middle of the node's bounds, which move as the
     *  tail grows and shrinks, so the comet wandered off the ring.) */
    private final javafx.scene.transform.Rotate turn = new javafx.scene.transform.Rotate(0, SIZE / 2, SIZE / 2);
    private final Group ring = new Group(track, comet);
    private double level;
    private Color accent = Color.DODGERBLUE;
    private final long t0 = System.nanoTime();
    private final Timeline clock = new Timeline(new KeyFrame(Duration.millis(30), e -> tick()));
    /** The title changed: a program that started animating it starts the ring, without the list being redrawn. */
    private final javafx.beans.value.ChangeListener<String> watch = (ov, was, now) -> update();

    ActivityMark() {
        setMinSize(SIZE, SIZE);
        setPrefSize(SIZE, SIZE);
        setMaxSize(SIZE, SIZE);
        setMouseTransparent(true);
        track.setFill(null);
        track.setStrokeWidth(WIDTH);
        // three arcs ending at the same head, each shorter and brighter than the one before: a tail that fades
        var alphas = new double[] {0.22, 0.5, 1.0};
        for (var i = 0; i < tail.length; i++) {
            var a = new Arc(SIZE / 2, SIZE / 2, RADIUS, RADIUS, 0, 90);
            a.setType(ArcType.OPEN);
            a.setFill(null);
            a.setStrokeWidth(WIDTH);
            a.setStrokeLineCap(StrokeLineCap.ROUND);
            a.setOpacity(alphas[i]);
            tail[i] = a;
        }
        comet.getChildren().addAll(tail);
        comet.getTransforms().add(turn);
        // the rotation is about the ring's centre
        comet.setManaged(false);
        ring.setManaged(false);
        dot.setManaged(false);
        getChildren().addAll(ring, dot);
        clock.setCycleCount(Animation.INDEFINITE);
        // a row that is off screen (or a cell reused) must not keep a clock running
        sceneProperty().addListener((ov, was, now) -> { if (now == null) clock.stop(); else update(); });
        paint();
    }

    /** The tab this mark shows, the row's text colour (the quiet dot and the ring's track) and the accent (the comet). */
    void show(TerminalTab tab, Color text, Color accent) {
        if (this.tab != tab) {
            if (this.tab != null) this.tab.title.removeListener(watch);
            if (tab != null) tab.title.addListener(watch);
        }
        this.tab = tab;
        this.accent = accent;
        track.setStroke(Color.color(text.getRed(), text.getGreen(), text.getBlue(), 0.17));
        dot.setFill(Color.color(text.getRed(), text.getGreen(), text.getBlue(), 0.55));
        for (var a : tail) a.setStroke(accent);
        update();
    }

    private boolean busy() {
        return tab != null && tab.busy();
    }

    private void update() {
        if ((busy() || level > 0.01) && getScene() != null) {
            if (clock.getStatus() != Animation.Status.RUNNING) clock.play();
        } else {
            clock.stop();
            level = busy() ? 1 : 0;
        }
        paint();
    }

    private void tick() {
        // fade in while the program works, out when it stops
        level += ((busy() ? 1 : 0) - level) * 0.22;
        if (!busy() && level < 0.02) {
            level = 0;
            clock.stop();
        }
        paint();
    }

    /** For the geometry test: the ring at full strength, drawn as it is `seconds` after the start. */
    void preview(double seconds) {
        level = 1;
        paintAt(seconds);
    }

    private void paint() {
        paintAt((System.nanoTime() - t0) / 1e9);
    }

    private void paintAt(double t) {
        ring.setOpacity(level);
        dot.setOpacity(1 - level);
        ring.setVisible(level > 0.01);
        dot.setVisible(level < 0.99);
        if (level <= 0.01) return;
        // the head goes round once per TURN; the tail is between 70 and 200 degrees long and breathes
        var head = (t / TURN) * 360;
        var sweep = 135 + 65 * Math.sin(2 * Math.PI * t / BREATH);
        turn.setAngle(head);
        var scale = new double[] {1.0, 0.62, 0.3};
        for (var i = 0; i < tail.length; i++) tail[i].setLength(sweep * scale[i]);
    }
}
