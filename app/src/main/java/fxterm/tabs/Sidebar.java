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
        // a thin strip above the tabs: everything's RAM together, and waking every sleeping tab at once
        total.setStyle("-fx-text-fill: " + theme.foreground + "; -fx-font-size: 12; -fx-font-weight: bold;");
        total.setTooltip(new Tooltip("RAM of Tabs itself plus every live tab's shell and the program it runs (claude)"));
        restoreAll.setFocusTraversable(false);
        restoreAll.setStyle("-fx-background-color: " + theme.colors.get(4) + "; -fx-text-fill: #ffffff; -fx-font-size: 11.5;"
                + " -fx-padding: 2 8 2 8; -fx-background-radius: 4; -fx-cursor: hand;");
        restoreAll.setOnAction(e -> app.wakeAll());
        var gap = new Region();
        HBox.setHgrow(gap, Priority.ALWAYS);
        var strip = new HBox(6, total, gap, restoreAll);
        strip.setAlignment(javafx.geometry.Pos.CENTER_LEFT);
        strip.setStyle("-fx-padding: 4 6 6 8;");
        node = new VBox(strip, list, plus);
        node.setStyle(Ui.sidebar(theme));
        node.setMinWidth(120);
    }

    final Label total = new Label();
    final Button restoreAll = new Button();

    /** The strip's figures; the button shows only while some tab is asleep. */
    void update(List<TerminalTab> tabs, long selfBytes) {
        var asleep = tabs.stream().filter(t -> !t.live()).count();
        total.setText("RAM " + Mem.mb(selfBytes + tabs.stream().mapToLong(t -> t.live() ? t.ramBytes : 0).sum()));
        restoreAll.setText("Restore all (" + asleep + ")");
        restoreAll.setVisible(asleep > 0);
        restoreAll.setManaged(asleep > 0);
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
        return new ContextMenu(copyDir, copyCmd, new SeparatorMenuItem(), rename, colour, duplicate, sleep,
                new SeparatorMenuItem(), close, above, below, others);
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
