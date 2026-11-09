package fxterm.tabs;

import module java.base;
import javafx.geometry.Side;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.DirectoryChooser;

/** What a window's name button, colour button and gear open. */
final class WindowMenus {

    static void rename(TabsWindow w) {
        var d = new TextInputDialog(w.name == null ? "" : w.name);
        d.initOwner(w.stage);
        d.setTitle("Name this window");
        d.setHeaderText("Name for this window, shown in its title bar and in the tab switcher (empty: the default, Window N)");
        d.showAndWait().ifPresent(n -> {
            w.name = n.isBlank() ? null : n.strip();
            w.look();
        });
        w.focusSoon();
    }

    /** No colour first, then the palette, each colour with a swatch: the one in use ticked, those worn by other windows named. */
    static ContextMenu colours(TabsWindow w) {
        var menu = new ContextMenu();
        var none = new Region();
        none.setMinSize(14, 14);
        none.setStyle("-fx-background-color: transparent; -fx-border-color: #888888; -fx-border-radius: 7; -fx-background-radius: 7;");
        var plain = new MenuItem("No colour" + (w.colour == null ? "   ✓" : ""), none);
        plain.setOnAction(e -> { w.colour = null; w.look(); w.focusSoon(); });
        menu.getItems().add(plain);
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

    /** The folder where new tabs open: typed or pasted, or chosen; empty goes back to the home folder. */
    static void defaultFolder(TabsWindow w) {
        var field = new TextField(DefaultFolder.saved() == null ? "" : DefaultFolder.saved());
        field.setPromptText("empty: your home folder");
        field.setPrefColumnCount(42);
        var choose = new Button("Choose…");
        choose.setOnAction(e -> {
            var c = new DirectoryChooser();
            c.setTitle("Folder where new tabs open");
            var start = field.getText().isBlank() ? null : new java.io.File(field.getText().strip());
            if (start != null && start.isDirectory()) c.setInitialDirectory(start);
            var dir = c.showDialog(w.stage);
            if (dir != null) field.setText(dir.getPath());
        });
        var d = new Dialog<ButtonType>();
        d.initOwner(w.stage);
        d.setTitle("Default folder");
        d.setHeaderText("Folder where a new tab opens (empty: your home folder). One setting for every window.");
        d.getDialogPane().setContent(new HBox(6, field, choose));
        d.getDialogPane().getButtonTypes().setAll(ButtonType.OK, ButtonType.CANCEL);
        d.showAndWait().filter(b -> b == ButtonType.OK).ifPresent(b -> {
            var text = field.getText();
            if (!text.isBlank() && !DefaultFolder.exists(text.strip())) w.app.message("Not a folder: " + text.strip());
            else DefaultFolder.set(text);
        });
        w.focusSoon();
    }

    static void settings(TabsWindow w, Node anchor) {
        var app = w.app;
        var colour = new Menu("Window colour");
        colour.getItems().setAll(colours(w).getItems());
        var menu = new ContextMenu(item("Name this window…", () -> rename(w)),
                item("Default name (Window N)" + (w.name == null ? "   ✓" : ""), () -> { w.name = null; w.look(); w.focusSoon(); }),
                colour, new SeparatorMenuItem(),
                item("Default folder for new tabs…", () -> defaultFolder(w)), new SeparatorMenuItem(),
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
