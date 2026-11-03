package fxterm.tabs;

import javafx.animation.*;
import javafx.scene.effect.*;
import javafx.scene.layout.Region;
import javafx.scene.paint.Color;
import javafx.scene.shape.SVGPath;
import javafx.util.Duration;

/** The bell on a row whose tab is asking for the user: amber, with a soft glow that swells and fades about once a second
 *  and a half. Its size never changes (only the glow does), so the row cannot twitch; the animation runs only while the
 *  bell is shown. */
final class BellMark extends Region {

    static final double SIZE = 16;
    static final String AMBER = "#f2a900";

    final DropShadow glow = new DropShadow(BlurType.GAUSSIAN, Color.web(AMBER), 3, 0.3, 0, 0);
    final Timeline pulse = new Timeline(
            new KeyFrame(Duration.ZERO, new KeyValue(glow.radiusProperty(), 2), new KeyValue(opacityProperty(), 0.72)),
            new KeyFrame(Duration.millis(1900), new KeyValue(glow.radiusProperty(), 7, Interpolator.EASE_BOTH), new KeyValue(opacityProperty(), 0.95, Interpolator.EASE_BOTH)));

    BellMark() {
        var shape = new SVGPath();
        shape.setContent(Icons.BELL);
        setShape(shape);
        setMinSize(SIZE, SIZE);
        setPrefSize(SIZE, SIZE);
        setMaxSize(SIZE, SIZE);
        setStyle("-fx-background-color: " + AMBER + ";");
        setEffect(glow);
        setMouseTransparent(true);
        pulse.setCycleCount(Animation.INDEFINITE);
        pulse.setAutoReverse(true);
        show(false);
    }

    void show(boolean on) {
        if (on == isVisible() && on == (pulse.getStatus() == Animation.Status.RUNNING)) return;
        setVisible(on);
        setManaged(on);
        if (on) pulse.play(); else pulse.stop();
    }
}
