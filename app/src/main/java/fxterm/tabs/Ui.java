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
                + "; -fx-border-width: 0; -fx-background-insets: 0; -fx-padding: 4 0 0 0;";
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
                + ".menu-item:focused { -fx-background-color: " + shade(t.background, 30) + "; }\n"
                // the History view's list, filters and search box, in the theme's dark colours
                + ".list-cell:filled:selected, .list-cell:filled:selected:focused { -fx-background-color: " + shade(t.background, 22) + "; }\n"
                + ".list-cell { -fx-background-color: transparent; }\n"
                + ".choice-box { -fx-background-color: " + shade(t.background, 18) + "; -fx-background-radius: 4; }\n"
                + ".choice-box > .label { -fx-text-fill: " + t.foreground + "; }\n"
                + ".choice-box > .open-button > .arrow { -fx-background-color: " + t.foreground + "; }\n"
                + ".text-field { -fx-background-color: " + shade(t.background, 12) + "; -fx-text-fill: " + t.foreground + ";"
                + " -fx-prompt-text-fill: " + shade(t.foreground, -70) + "; -fx-background-radius: 4; }\n"
                + ".text-field:focused { -fx-background-color: " + t.colors.get(4) + ", " + shade(t.background, 12) + "; -fx-background-insets: 0, 1; }\n"
                + ".date-picker > .arrow-button { -fx-background-color: " + shade(t.background, 18) + "; }\n"
                // the quiet buttons of the footer, the compose box and the selection bar: a shade above the bar, a hairline edge
                + ".tabs-button { -fx-background-color: " + shade(t.background, 14) + "; -fx-text-fill: " + mix(t.foreground, t.background, 0.15) + ";"
                + " -fx-font-size: 11; -fx-padding: 2 9 2 9; -fx-background-radius: 5; -fx-border-color: " + shade(t.background, 30)
                + "; -fx-border-radius: 5; -fx-border-width: 1; -fx-cursor: hand; }\n"
                + ".tabs-button:hover { -fx-background-color: " + shade(t.background, 28) + "; }\n"
                + ".tabs-button:pressed { -fx-background-color: " + shade(t.background, 38) + "; }\n"
                + ".tabs-button:disabled { -fx-opacity: 0.4; }\n"
                + ".tabs-button.small { -fx-font-size: 10; -fx-padding: 1 6 1 6; -fx-background-radius: 4; -fx-border-radius: 4; }\n";
        return "data:text/css;base64," + java.util.Base64.getEncoder().encodeToString(css.getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }

    static String plus(TabbyTheme t) {
        return "-fx-background-color: transparent; -fx-text-fill: " + t.foreground + "; -fx-font-size: 20; -fx-padding: 4 14 8 14; -fx-cursor: hand;";
    }

    /** A tab row. A coloured tab keeps its colour as the side bar and a faint tint; otherwise the bar shows only on the
     *  selected tab, in the theme's blue. */
    /** A tab's colour fills its row and the frame (--tab-fill full); by default it shows only as the strip at the
     *  row's edge, and rows and frame keep the standard greys. */
    static boolean fullColour;

    static String cell(TabbyTheme t, boolean selected, String colour) {
        if (!fullColour) {
            var bar = colour != null ? colour : selected ? frame(t, null) : "transparent";
            var fill = selected ? frame(t, null) : shade(t.background, -10);
            return "-fx-padding: 3 2 3 4; -fx-border-width: 0 0 0 3; -fx-border-color: " + bar + "; -fx-background-color: " + fill + ";";
        }
        if (selected) {
            // the active tab is filled with the frame colour, which also borders its terminal: one shape
            var frame = frame(t, colour);
            return "-fx-padding: 3 2 3 4; -fx-border-width: 0 0 0 3; -fx-border-color: " + (colour != null ? colour : frame)
                    + "; -fx-background-color: " + frame + ";";
        }
        var base = shade(t.background, -10);
        var bar = colour != null ? colour : "transparent";
        var fill = colour != null ? mix(base, colour, 0.16) : base;
        return "-fx-padding: 3 2 3 4; -fx-border-width: 0 0 0 3; -fx-border-color: " + bar + "; -fx-background-color: " + fill + ";";
    }

    /** The colour that joins the active tab to its terminal: the tab's own colour (toned down so white text stays
     *  readable on it), or for a tab without one a neutral grey a few steps lighter than the background. */
    static String frame(TabbyTheme t, String colour) {
        return colour != null && fullColour ? mix(t.background, colour, 0.45) : shade(t.background, 26);
    }

    /** White or near-black, whichever has the higher contrast ratio against the fill (WCAG relative luminance). */
    static String textOn(String fill) {
        var rgb = TabbyTheme.rgb(fill);
        java.util.function.DoubleUnaryOperator lin = c -> { c /= 255; return c <= 0.03928 ? c / 12.92 : Math.pow((c + 0.055) / 1.055, 2.4); };
        var l = 0.2126 * lin.applyAsDouble((rgb >> 16) & 0xff) + 0.7152 * lin.applyAsDouble((rgb >> 8) & 0xff)
                + 0.0722 * lin.applyAsDouble(rgb & 0xff);
        return (1.05) / (l + 0.05) >= (l + 0.05) / 0.05 ? "#ffffff" : "#111111";
    }

    /** The border around the shown terminal, in the active tab's frame colour. */
    static final int FRAME = 5;

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
        return ask(owner, "Close Tabs?", message, "F2: close anyway    Esc: keep working");
    }

    /** The same question with its own header and hint line (the update asks it too). */
    static boolean ask(Stage owner, String header, String message, String hint) {
        var a = new Alert(Alert.AlertType.CONFIRMATION, message + "\n\n" + hint, ButtonType.OK, ButtonType.CANCEL);
        a.initOwner(owner);
        a.setHeaderText(header);
        var armedAt = System.currentTimeMillis() + 1500;
        a.getDialogPane().addEventFilter(javafx.scene.input.KeyEvent.KEY_PRESSED, e -> {
            if (System.currentTimeMillis() < armedAt) { e.consume(); return; }
            if (e.getCode() == KeyCode.F2) { a.setResult(ButtonType.OK); a.close(); e.consume(); }
            else if (e.getCode() == KeyCode.ENTER || e.getCode() == KeyCode.SPACE) e.consume();
        });
        return a.showAndWait().orElse(ButtonType.CANCEL) == ButtonType.OK;
    }

    /** Closing one of several windows, as a browser asks: 0 close all windows (Tabs quits; every window and tab comes
     *  back at the next start), 1 close only this window, -1 cancel. F2 all, F3 this window, Esc cancel; keys are
     *  ignored for the first moments, as in confirm. */
    static int closeWhich(Stage owner, int windows, int tabsHere) {
        var all = new ButtonType("Close all " + windows + " windows (F2)", ButtonBar.ButtonData.OK_DONE);
        var one = new ButtonType("Close this window (F3)", ButtonBar.ButtonData.OTHER);
        var a = new Alert(Alert.AlertType.CONFIRMATION, "Close all windows: Tabs closes and every window comes back, with its tabs, the next time"
                + " it starts.\nClose this window: only its " + tabsHere + " tabs end (they stay in History).\n\nEsc: cancel", all, one, ButtonType.CANCEL);
        a.initOwner(owner);
        a.setHeaderText("Close Tabs?");
        var armedAt = System.currentTimeMillis() + 1000;
        a.getDialogPane().addEventFilter(javafx.scene.input.KeyEvent.KEY_PRESSED, e -> {
            if (System.currentTimeMillis() < armedAt) { e.consume(); return; }
            if (e.getCode() == KeyCode.F2) { a.setResult(all); a.close(); e.consume(); }
            else if (e.getCode() == KeyCode.F3) { a.setResult(one); a.close(); e.consume(); }
            else if (e.getCode() == KeyCode.ENTER || e.getCode() == KeyCode.SPACE) e.consume();
        });
        var r = a.showAndWait().orElse(ButtonType.CANCEL);
        return r == all ? 0 : r == one ? 1 : -1;
    }

    /** Closing a group: 0 remove only the label (its tabs stay), 1 close the group and its tabs, -1 cancel.
     *  F2 keep the tabs, F3 close them too, Esc cancel; keys are ignored for the first moments. */
    static int closeGroup(Stage owner, String label, int tabs) {
        var keep = new ButtonType("Remove the label, keep the " + tabs + " tabs (F2)", ButtonBar.ButtonData.OK_DONE);
        var all = new ButtonType("Close the group and its " + tabs + " tabs (F3)", ButtonBar.ButtonData.OTHER);
        var a = new Alert(Alert.AlertType.CONFIRMATION, "Keep the tabs: only the label goes; its tabs stay where they are."
                + "\nClose them too: the tabs end (they stay in History).\n\nEsc: cancel", keep, all, ButtonType.CANCEL);
        a.initOwner(owner);
        a.setHeaderText("Close the group \"" + label + "\"?");
        var armedAt = System.currentTimeMillis() + 1000;
        a.getDialogPane().addEventFilter(javafx.scene.input.KeyEvent.KEY_PRESSED, e -> {
            if (System.currentTimeMillis() < armedAt) { e.consume(); return; }
            if (e.getCode() == KeyCode.F2) { a.setResult(keep); a.close(); e.consume(); }
            else if (e.getCode() == KeyCode.F3) { a.setResult(all); a.close(); e.consume(); }
            else if (e.getCode() == KeyCode.ENTER || e.getCode() == KeyCode.SPACE) e.consume();
        });
        var r = a.showAndWait().orElse(ButtonType.CANCEL);
        return r == keep ? 0 : r == all ? 1 : -1;
    }

    /** Closing a tab that has something running in it: 0 close it, 1 hibernate it instead (it stays in the list, asleep,
     *  and wakes as it was), -1 cancel. F2 close, F3 hibernate, Esc cancel; keys are ignored for the first moments, so
     *  a keystroke meant for the program cannot answer it. */
    static int closeTab(Stage owner, String label, String running) {
        var close = new ButtonType("Close (F2)", ButtonBar.ButtonData.OK_DONE);
        var sleep = new ButtonType("Hibernate (F3)", ButtonBar.ButtonData.OTHER);
        var a = new Alert(Alert.AlertType.CONFIRMATION, running + "\n\nClose: the program ends (the tab stays in History)."
                + "\nHibernate: the tab goes to sleep and wakes where it was.\n\nEsc: cancel", close, sleep, ButtonType.CANCEL);
        a.initOwner(owner);
        a.setHeaderText("Close \"" + label + "\"?");
        var armedAt = System.currentTimeMillis() + 800;
        a.getDialogPane().addEventFilter(javafx.scene.input.KeyEvent.KEY_PRESSED, e -> {
            if (System.currentTimeMillis() < armedAt) { e.consume(); return; }
            if (e.getCode() == KeyCode.F2) { a.setResult(close); a.close(); e.consume(); }
            else if (e.getCode() == KeyCode.F3) { a.setResult(sleep); a.close(); e.consume(); }
            else if (e.getCode() == KeyCode.ENTER || e.getCode() == KeyCode.SPACE) e.consume();
        });
        var r = a.showAndWait().orElse(ButtonType.CANCEL);
        return r == close ? 0 : r == sleep ? 1 : -1;
    }

    private Ui() { }
}
