package fxterm.tabs;

import java.util.function.IntBinaryOperator;
import javafx.scene.control.*;
import javafx.scene.input.KeyCode;
import javafx.stage.*;

/** Styles taken from the theme, and the one dialog. The side list and footer sit a shade darker than the terminal,
 *  the selected tab a shade lighter with an accent bar in the theme's blue, as Tabby does. */
public final class Ui {

    static String sidebar(TabbyTheme t) {
        return "-fx-background-color: " + shade(t.background, -10) + "; -fx-control-inner-background: " + shade(t.background, -10)
                + "; -fx-border-color: transparent; -fx-background-insets: 0; -fx-padding: 4 0 0 0;";
    }

    /** What inline styles cannot reach (the split divider, every scroll bar, the list's focus ring), as a stylesheet
     *  made from the theme. */
    static String stylesheet(TabbyTheme t) {
        var side = shade(t.background, -10);
        var css = ".split-pane > .split-pane-divider { -fx-background-color: " + side + "; -fx-padding: 0 2 0 2; }\n"
                + ".split-pane { -fx-background-color: " + side + "; -fx-padding: 0; -fx-background-insets: 0; }\n"
                + ".list-view, .list-view:focused { -fx-background-color: " + side + "; -fx-background-insets: 0; -fx-padding: 0; }\n"
                + ".scroll-bar { -fx-background-color: transparent; }\n"
                + ".scroll-bar .thumb { -fx-background-color: " + shade(t.background, 45) + "; -fx-background-radius: 4; }\n"
                + ".scroll-bar .increment-button, .scroll-bar .decrement-button { -fx-padding: 0; -fx-background-color: transparent; }\n"
                + ".scroll-bar .increment-arrow, .scroll-bar .decrement-arrow { -fx-shape: \"\"; -fx-padding: 0; }\n"
                + ".context-menu { -fx-background-color: " + shade(t.background, 8) + "; }\n"
                + ".context-menu .label { -fx-text-fill: " + t.foreground + "; }\n"
                + ".menu-item:focused { -fx-background-color: " + shade(t.background, 30) + "; }\n";
        return "data:text/css;base64," + java.util.Base64.getEncoder().encodeToString(css.getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }

    static String plus(TabbyTheme t) {
        return "-fx-background-color: transparent; -fx-text-fill: " + t.foreground + "; -fx-font-size: 20; -fx-padding: 4 14 8 14; -fx-cursor: hand;";
    }

    /** A tab row. A coloured tab keeps its colour as the side bar and a faint tint; otherwise the bar shows only on the
     *  selected tab, in the theme's blue. */
    static String cell(TabbyTheme t, boolean selected, String colour) {
        var base = selected ? shade(t.background, 14) : shade(t.background, -10);
        var bar = colour != null ? colour : selected ? t.colors.get(4) : "transparent";
        var fill = colour != null ? mix(base, colour, selected ? 0.28 : 0.16) : base;
        return "-fx-padding: 4 6 4 8; -fx-border-width: 0 0 0 3; -fx-border-color: " + bar + "; -fx-background-color: " + fill + ";";
    }

    static String mix(String a, String b, double amount) {
        var x = TabbyTheme.rgb(a);
        var y = TabbyTheme.rgb(b);
        IntBinaryOperator ch = (p, q) -> (int) Math.round(p + (q - p) * amount);
        var r = ch.applyAsInt((x >> 16) & 0xff, (y >> 16) & 0xff);
        var g = ch.applyAsInt((x >> 8) & 0xff, (y >> 8) & 0xff);
        var bl = ch.applyAsInt(x & 0xff, y & 0xff);
        return "#%02x%02x%02x".formatted(r, g, bl);
    }

    static String footer(TabbyTheme t) {
        return "-fx-background-color: " + shade(t.background, -10) + "; -fx-padding: 3 10 3 10;";
    }

    static String footerText(TabbyTheme t) {
        return "-fx-text-fill: " + t.foreground + "; -fx-opacity: 0.75; -fx-font-size: 11.5;";
    }

    /** The colour lighter (positive) or darker (negative) by that many steps of 255. */
    static String shade(String hex, int by) {
        var rgb = TabbyTheme.rgb(hex);
        var r = Math.clamp(((rgb >> 16) & 0xff) + by, 0, 255);
        var g = Math.clamp(((rgb >> 8) & 0xff) + by, 0, 255);
        var b = Math.clamp((rgb & 0xff) + by, 0, 255);
        return "#%02x%02x%02x".formatted(r, g, b);
    }

    /** F2 confirms, Esc cancels; keys are ignored for the first moments, so a keystroke meant for a terminal
     *  cannot answer it. */
    static boolean confirm(Stage owner, String message) {
        var a = new Alert(Alert.AlertType.CONFIRMATION, message + "\n\nF2: close anyway    Esc: keep working", ButtonType.OK, ButtonType.CANCEL);
        a.initOwner(owner);
        a.setHeaderText("Close Tabs?");
        var armedAt = System.currentTimeMillis() + 1500;
        a.getDialogPane().addEventFilter(javafx.scene.input.KeyEvent.KEY_PRESSED, e -> {
            if (System.currentTimeMillis() < armedAt) { e.consume(); return; }
            if (e.getCode() == KeyCode.F2) { a.setResult(ButtonType.OK); a.close(); e.consume(); }
            else if (e.getCode() == KeyCode.ENTER || e.getCode() == KeyCode.SPACE) e.consume();
        });
        return a.showAndWait().orElse(ButtonType.CANCEL) == ButtonType.OK;
    }

    private Ui() { }
}
