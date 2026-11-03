package fxterm.tabs;

import module java.base;
import javafx.geometry.*;
import javafx.scene.control.*;
import javafx.scene.layout.*;

/** The row above the tab list: History, the window's colour, the window's name, Restore all (the sun) and Settings
 *  (the gear). Flat icons; the colour comes in when the mouse is over one. Beside them the window's name, in the
 *  window's colour, and a bar of that colour along the top of the row, so a window can be told from another. */
final class Toolbar extends HBox {

    static final double ICON = 17;

    final TabsWindow win;
    final TabbyTheme theme;
    final Button history, colour, rename, restoreAll, settings;
    final javafx.scene.layout.Region colourIcon;
    final Label name = new Label();

    Toolbar(TabsWindow win, TabbyTheme theme) {
        this.win = win;
        this.theme = theme;
        var dim = Ui.mix(theme.foreground, theme.background, 0.38);
        var bright = theme.foreground;
        history = Icons.button(Icons.HISTORY, ICON, dim, theme.colors.get(4), Os.keys("History: every saved session (Ctrl+Shift+H)"), win::toggleHistory);
        rename = Icons.button(Icons.EDIT, ICON, dim, bright, "Name this window", () -> WindowMenus.rename(win));
        restoreAll = Icons.button(Icons.SUN, ICON, dim, "#f2c94c", "Restore all: wake every sleeping tab in this window", win::wakeAll);
        settings = Icons.button(Icons.GEAR, ICON, dim, bright, "Settings", () -> { });
        settings.setOnAction(e -> WindowMenus.settings(win, settings));
        // the colour button is painted in this window's own colour, so it shows which one this is
        colourIcon = Icons.icon(Icons.PALETTE, ICON, dim);
        colour = new Button();
        colour.setGraphic(colourIcon);
        colour.setFocusTraversable(false);
        colour.getStyleClass().add("icon-button");
        colour.setTooltip(new Tooltip("Change this window's colour (its title bar, border and taskbar icon)"));
        colour.setOnAction(e -> WindowMenus.colours(win).show(colour, Side.BOTTOM, 0, 0));
        colour.hoverProperty().addListener((ov, was, now) -> colourIcon.setStyle("-fx-background-color: " + (now ? bright : own()) + ";"));
        name.setMinWidth(0);
        name.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(name, Priority.ALWAYS);
        setAlignment(Pos.CENTER_LEFT);
        setSpacing(1);
        getChildren().addAll(history, colour, rename, restoreAll, settings, name);
        look();
    }

    private String own() {
        return win.colour == null ? Ui.mix(theme.foreground, theme.background, 0.38) : win.colour;
    }

    /** The window's colour and name, after either changed. */
    void look() {
        var c = own();
        colourIcon.setStyle("-fx-background-color: " + c + ";");
        name.setText(win.windowLabel());
        name.setStyle("-fx-text-fill: " + c + "; -fx-font-size: 11.5; -fx-font-weight: bold; -fx-padding: 0 0 0 8; -fx-opacity: 0.95;");
        setStyle("-fx-background-color: " + Ui.shade(theme.background, -10) + "; -fx-padding: 3 6 3 4; -fx-border-color: " + c
                + " transparent transparent transparent; -fx-border-width: 3 0 0 0;");
    }

    /** Restore all is for this window's sleeping tabs: dimmed when there are none. */
    void update(List<TerminalTab> tabs) {
        var real = tabs.stream().filter(t -> !t.separator).toList();
        var asleep = real.stream().filter(t -> !t.live()).count();
        restoreAll.setDisable(asleep == 0);
        restoreAll.getTooltip().setText(asleep == 0 ? "Restore all: every tab in this window is awake" : "Restore all: wake the " + asleep + " sleeping tab" + (asleep == 1 ? "" : "s") + " in this window");
    }
}
