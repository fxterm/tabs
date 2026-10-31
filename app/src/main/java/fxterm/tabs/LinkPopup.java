package fxterm.tabs;

import module java.base;
import javafx.animation.PauseTransition;
import javafx.geometry.Insets;
import javafx.geometry.Rectangle2D;
import javafx.scene.canvas.Canvas;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.layout.HBox;
import javafx.stage.Popup;
import javafx.util.Duration;

/** The gentle popup under a link the mouse rests on: a small Copy button for its address or path. It appears after a
 *  short rest (not while the mouse only passes), stays while the mouse is on it, and goes when the mouse leaves. */
final class LinkPopup {

    static final Duration REST = Duration.millis(450), GRACE = Duration.millis(400), STAY = Duration.seconds(4);

    static Popup popup;
    static String shownFor;
    static boolean over;
    static final PauseTransition appear = new PauseTransition(REST), leave = new PauseTransition(GRACE), stay = new PauseTransition(STAY);

    static {
        leave.setOnFinished(e -> { if (!over) hide(); });
        // the terminal tells no one when the mouse leaves its window for the popup or beyond: this ends it anyway
        stay.setOnFinished(e -> { if (!over) hide(); });
    }

    /** The mouse came onto a link. */
    static void entered(String target, Canvas canvas, Rectangle2D link) {
        leave.stop();
        appear.stop();
        if (popup != null && popup.isShowing() && target.equals(shownFor)) return;
        hide();
        appear.setOnFinished(e -> show(target, canvas, link));
        appear.playFromStart();
    }

    /** The mouse went off the link: a moment to reach the popup before it goes. */
    static void exited() {
        appear.stop();
        leave.playFromStart();
    }

    static void hide() {
        appear.stop();
        leave.stop();
        stay.stop();
        over = false;
        if (popup != null) popup.hide();
        popup = null;
        shownFor = null;
    }

    static void show(String target, Canvas canvas, Rectangle2D link) {
        var window = canvas.getScene() == null ? null : canvas.getScene().getWindow();
        var theme = Footer.theme;
        if (window == null || theme == null) return;
        var web = target.contains("://");
        var copy = Footer.small(web ? "Copy link" : "Copy path", web ? "Copy the address" : "Copy the path", null);
        copy.setOnAction(e -> {
            var c = new ClipboardContent();
            c.putString(target);
            Clipboard.getSystemClipboard().setContent(c);
            copy.setText("Copied");
            copy.setDisable(true);
            var done = new PauseTransition(Duration.millis(700));
            done.setOnFinished(x -> hide());
            done.play();
        });
        var row = new HBox(copy);
        row.setPadding(new Insets(3));
        row.getStylesheets().add(Ui.stylesheet(theme));
        row.setStyle("-fx-background-color: " + Ui.shade(theme.background, -6) + "; -fx-background-radius: 5; -fx-border-color: "
                + Ui.shade(theme.background, 26) + "; -fx-border-radius: 5; -fx-effect: dropshadow(gaussian, rgba(0,0,0,0.4), 6, 0.2, 0, 1);");
        row.setOnMouseEntered(e -> { over = true; leave.stop(); stay.stop(); });
        row.setOnMouseExited(e -> { over = false; leave.playFromStart(); });
        popup = new Popup();
        popup.getContent().add(row);
        popup.setAutoHide(true);
        popup.setConsumeAutoHidingEvents(false);
        shownFor = target;
        // right under the link, touching it, so the mouse can slide down onto the button
        var at = canvas.localToScreen(link.getMinX(), link.getMaxY());
        popup.show(window, at.getX(), at.getY() - 2);
        stay.playFromStart();
    }
}
