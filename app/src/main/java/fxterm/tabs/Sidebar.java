package fxterm.tabs;

import module java.base;
import javafx.beans.binding.Bindings;
import javafx.collections.ObservableList;
import javafx.scene.control.*;
import javafx.scene.layout.*;

/** The tab list on the left. The list is only as tall as its tabs, so the + button sits right under the last one;
 *  past the window's height the list scrolls. Right-click a tab: rename, colour, close. */
public final class Sidebar {

    static final double ROW = 52;
    static final Map<String, Integer> COLOURS = new LinkedHashMap<>();

    static {
        COLOURS.put("Red", 1);
        COLOURS.put("Green", 2);
        COLOURS.put("Yellow", 3);
        COLOURS.put("Blue", 4);
        COLOURS.put("Magenta", 5);
        COLOURS.put("Cyan", 6);
    }

    final TabsApp app;
    final TabbyTheme theme;
    final ListView<TerminalTab> list;
    final VBox node;

    Sidebar(TabsApp app, ObservableList<TerminalTab> tabs, TabbyTheme theme) {
        this.app = app;
        this.theme = theme;
        list = new ListView<>(tabs);
        list.setCellFactory(v -> new TabCell(this));
        list.setFocusTraversable(false);
        list.setFixedCellSize(ROW);
        list.setStyle(Ui.sidebar(theme));
        list.prefHeightProperty().bind(Bindings.size(tabs).multiply(ROW).add(6));
        list.setMinHeight(ROW);
        list.setMaxHeight(Region.USE_PREF_SIZE);
        var plus = new Button("+");
        plus.setFocusTraversable(false);
        plus.setStyle(Ui.plus(theme));
        plus.setTooltip(new Tooltip("New tab (Ctrl+Shift+T)"));
        plus.setOnAction(e -> app.add());
        node = new VBox(list, plus);
        node.setStyle(Ui.sidebar(theme));
        node.setMinWidth(120);
    }

    ContextMenu menu(TerminalTab t) {
        var rename = new MenuItem("Rename…");
        rename.setOnAction(e -> rename(t));
        var colour = new Menu("Colour");
        var none = new MenuItem("None");
        none.setOnAction(e -> recolour(t, null));
        colour.getItems().add(none);
        COLOURS.forEach((name, index) -> {
            var swatch = new Region();
            swatch.setMinSize(12, 12);
            swatch.setStyle("-fx-background-color: " + theme.colors.get(index) + "; -fx-background-radius: 2;");
            var item = new MenuItem(name, swatch);
            item.setOnAction(e -> recolour(t, theme.colors.get(index)));
            colour.getItems().add(item);
        });
        var close = new MenuItem("Close tab");
        close.setOnAction(e -> app.close(t));
        return new ContextMenu(rename, colour, new SeparatorMenuItem(), close);
    }

    void rename(TerminalTab t) {
        var d = new TextInputDialog(t.custom.get() == null ? t.title.get() : t.custom.get());
        d.initOwner(app.stage);
        d.setTitle("Rename tab");
        d.setHeaderText("Name for this tab (empty: use the title the program sets)");
        d.showAndWait().ifPresent(name -> {
            t.custom.set(name.isBlank() ? null : name.strip());
            app.refresh();
        });
    }

    void recolour(TerminalTab t, String hex) {
        t.color.set(hex);
        app.refresh();
    }
}
