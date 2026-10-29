package fxterm.tabs;

import module java.base;
import javafx.beans.binding.Bindings;
import javafx.collections.ObservableList;
import javafx.scene.control.*;
import javafx.scene.input.TransferMode;
import javafx.scene.layout.*;

/** The tab list on the left. The list is only as tall as its tabs, so the + button sits right under the last one;
 *  past the window's height the list scrolls. Right-click a tab: rename, colour, close. */
public final class Sidebar {

    static final double ROW = 64, SEPARATOR_ROW = 30;
    static final Map<String, Integer> COLOURS = new LinkedHashMap<>();

    static {
        COLOURS.put("Red", 1);
        COLOURS.put("Green", 2);
        COLOURS.put("Yellow", 3);
        COLOURS.put("Blue", 4);
        COLOURS.put("Magenta", 5);
        COLOURS.put("Cyan", 6);
    }

    final TabsWindow app;
    final TabbyTheme theme;
    final ListView<TerminalTab> list;
    final VBox node;
    final HBox addButtons;

    Sidebar(TabsWindow app, ObservableList<TerminalTab> tabs, TabbyTheme theme) {
        this.app = app;
        this.theme = theme;
        list = new ListView<>(tabs);
        list.setCellFactory(v -> new TabCell(this));
        list.setFocusTraversable(false);
        list.setStyle(Ui.sidebar(theme));
        // tabs and separators have their own heights; the list is as tall as its rows, so + sits under the last
        list.prefHeightProperty().bind(Bindings.createDoubleBinding(
                () -> tabs.stream().mapToDouble(t -> t.separator ? SEPARATOR_ROW : ROW).sum() + 6, tabs));
        list.setMinHeight(ROW);
        list.setMaxHeight(Region.USE_PREF_SIZE);
        var plus = new Button("+");
        plus.setFocusTraversable(false);
        plus.setStyle(Ui.plus(theme));
        plus.setTooltip(new Tooltip("New tab (Ctrl+Shift+T)"));
        plus.setOnAction(e -> app.add());
        // beside +: a separator with a label, added at the end; drag it to where the group should start
        var separatorButton = new Button("⎯ label");
        separatorButton.setFocusTraversable(false);
        separatorButton.setStyle(Ui.plus(theme) + " -fx-font-size: 12.5; -fx-opacity: 0.8;");
        separatorButton.setTooltip(new Tooltip("New separator: a label that groups the tabs under it (drag it into place)"));
        separatorButton.setOnAction(e -> app.insertSeparator(null));
        addButtons = new HBox(0, plus, separatorButton);
        addButtons.setAlignment(javafx.geometry.Pos.CENTER_LEFT);
        // a tab dropped on the list but below the last tab goes to the end; from another window too
        list.setOnDragOver(e -> {
            if (e.getGestureSource() instanceof TabCell) e.acceptTransferModes(TransferMode.MOVE);
            e.consume();
        });
        list.setOnDragDropped(e -> {
            if (e.getGestureSource() instanceof TabCell src && src.getItem() != null) {
                app.app.moveTo(src.getItem(), app, Integer.MAX_VALUE);
                e.setDropCompleted(true);
            }
            e.consume();
        });
        node = new VBox(list, addButtons);
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
        // browser-style: copy, this tab, closing
        var copyDir = new MenuItem("Copy folder path");
        copyDir.setOnAction(e -> app.copy(t.where()));
        var copyCmd = new MenuItem(t.live() ? "Copy command (to resume elsewhere)" : "Copy restore command");
        copyCmd.setOnAction(e -> app.copyCommand(t));
        var duplicate = new MenuItem("Duplicate (new tab in this folder)");
        duplicate.setOnAction(e -> app.duplicate(t));
        var sleep = new MenuItem(t.live() ? "Hibernate" : "Restore");
        sleep.setOnAction(e -> { if (t.live()) app.hibernate(t); else t.wake.run(); });
        var close = new MenuItem("Close tab");
        close.setOnAction(e -> app.closeTabs(List.of(t)));
        var i = app.tabs.indexOf(t);
        var above = new MenuItem("Close tabs above");
        above.setOnAction(e -> app.closeTabs(List.copyOf(app.tabs.subList(0, app.tabs.indexOf(t)))));
        above.setDisable(i <= 0);
        var below = new MenuItem("Close tabs below");
        below.setOnAction(e -> app.closeTabs(List.copyOf(app.tabs.subList(app.tabs.indexOf(t) + 1, app.tabs.size()))));
        below.setDisable(i >= app.tabs.size() - 1);
        var others = new MenuItem("Close other tabs");
        others.setOnAction(e -> app.closeTabs(app.tabs.stream().filter(x -> x != t).toList()));
        others.setDisable(app.tabs.size() < 2);
        // send the tab to another window of this process, or to a new one; it keeps running
        var moveTo = new Menu("Move to window");
        var others2 = app.app.windows.stream().filter(w -> w != app).toList();
        for (var w : others2) {
            var shown = w.selected();
            var item = new MenuItem("Window " + (app.app.windows.indexOf(w) + 1) + " (" + w.tabs.size() + " tabs"
                    + (shown != null ? ", showing " + shown.shown() : "") + ")");
            item.setOnAction(e -> app.app.moveTo(t, w, Integer.MAX_VALUE));
            moveTo.getItems().add(item);
        }
        if (!others2.isEmpty()) moveTo.getItems().add(new SeparatorMenuItem());
        var fresh = new MenuItem("New window");
        fresh.setOnAction(e -> app.app.newWindow(t, Double.NaN, Double.NaN, app));
        moveTo.getItems().add(fresh);
        icon(moveTo, "↗");
        // small grey symbols, one per item, all in one tone so the menu stays calm
        icon(copyDir, "🗀");
        icon(copyCmd, "⧉");
        icon(rename, "✎");
        icon(colour, "◐");
        icon(duplicate, "❐");
        icon(sleep, t.live() ? "☾" : "↻");
        icon(close, "✕");
        icon(above, "↥");
        icon(below, "↧");
        icon(others, "⊠");
        var group = new MenuItem("Insert separator above…");
        group.setOnAction(e -> app.insertSeparator(t));
        icon(group, "\u2014");
        return new ContextMenu(copyDir, copyCmd, new SeparatorMenuItem(), rename, colour, duplicate, sleep, moveTo, group,
                new SeparatorMenuItem(), close, above, below, others);
    }

    /** A separator's own menu: its label, another separator, removing it. */
    ContextMenu separatorMenu(TerminalTab t) {
        var rename = new MenuItem("Rename…");
        rename.setOnAction(e -> rename(t));
        icon(rename, "\u270E");
        var group = new MenuItem("Insert separator above…");
        group.setOnAction(e -> app.insertSeparator(t));
        icon(group, "\u2014");
        var remove = new MenuItem("Close group (choose: keep its tabs or close them)");
        remove.setOnAction(e -> app.closeGroup(t));
        icon(remove, "\u2715");
        var fold = new MenuItem(t.collapsed ? "Show the group's tabs" : "Fold the group");
        fold.setOnAction(e -> app.toggleGroup(t));
        icon(fold, t.collapsed ? "▸" : "▾");
        return new ContextMenu(fold, rename, group, new SeparatorMenuItem(), remove);
    }

    static void icon(MenuItem item, String glyph) {
        var l = new Label(glyph);
        l.setStyle("-fx-text-fill: #a0a0a0; -fx-font-family: 'Segoe UI Symbol'; -fx-font-size: 13; -fx-min-width: 18;"
                + " -fx-alignment: center;");
        item.setGraphic(l);
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
