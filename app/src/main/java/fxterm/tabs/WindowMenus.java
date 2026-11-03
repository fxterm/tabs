package fxterm.tabs;

import module java.base;
import javafx.geometry.Side;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.layout.Region;

/** What a window's name button, colour button and gear open. */
final class WindowMenus {

    static void rename(TabsWindow w) {
        var d = new TextInputDialog(w.name == null ? "" : w.name);
        d.initOwner(w.stage);
        d.setTitle("Name this window");
        d.setHeaderText("Name for this window, shown in its title bar and in the tab switcher (empty: Window N)");
        d.showAndWait().ifPresent(n -> {
            w.name = n.isBlank() ? null : n.strip();
            w.look();
        });
        w.focusSoon();
    }

    /** The palette, each colour with a swatch: the one in use ticked, those worn by other windows named. */
    static ContextMenu colours(TabsWindow w) {
        var menu = new ContextMenu();
        for (var i = 0; i < WindowLook.PALETTE.size(); i++) {
            var hex = WindowLook.PALETTE.get(i);
            var other = w.app.windows.stream().filter(x -> x != w && hex.equals(x.colour)).findFirst().orElse(null);
            var swatch = new Region();
            swatch.setMinSize(14, 14);
            swatch.setStyle("-fx-background-color: " + hex + "; -fx-background-radius: 7;");
            var label = WindowLook.NAMES.get(i) + (hex.equals(w.colour) ? "   ✓" : other != null ? "   (" + other.windowLabel() + ")" : "");
            var item = new MenuItem(label, swatch);
            item.setOnAction(e -> { w.colour = hex; w.look(); w.focusSoon(); });
            menu.getItems().add(item);
        }
        return menu;
    }

    static void settings(TabsWindow w, Node anchor) {
        var app = w.app;
        var colour = new Menu("Window colour");
        colour.getItems().setAll(colours(w).getItems());
        var menu = new ContextMenu(item("Name this window…", () -> rename(w)), colour, new SeparatorMenuItem(),
                item(Os.keys("Larger text   Ctrl+="), () -> app.fontSize(FontSize.get() + FontSize.STEP)),
                item(Os.keys("Smaller text   Ctrl+-"), () -> app.fontSize(FontSize.get() - FontSize.STEP)),
                item(Os.keys("Normal size   Ctrl+0"), () -> app.fontSize(FontSize.base)), new SeparatorMenuItem(),
                item(Os.keys("Go to a tab in any window…   Ctrl+Tab"), () -> app.switcher.open(w, 0)),
                item(Os.keys("New window   Ctrl+Shift+N"), () -> app.newWindow(null, Double.NaN, Double.NaN, w)), new SeparatorMenuItem(),
                item("Check for a newer version now", () -> { if (app.updates != null) app.updates.recheck(); }));
        menu.show(anchor, Side.BOTTOM, 0, 0);
    }

    private static MenuItem item(String text, Runnable action) {
        var i = new MenuItem(text);
        i.setOnAction(e -> action.run());
        return i;
    }

    private WindowMenus() { }
}
