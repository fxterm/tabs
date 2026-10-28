package fxterm.tabs;

import module java.base;
import javafx.geometry.*;
import javafx.scene.paint.Color;
import javafx.scene.control.*;
import javafx.scene.input.*;
import javafx.scene.layout.*;

/** A tab's row. Left: its number (Alt/Ctrl+number selects it) with a moon under it when asleep or its RAM when live
 *  (hovering the RAM turns it into a Zz button that hibernates the tab). Then three lines: the title, the folder's
 *  name, the full path in small type. A close button on hover. Re-rendered by ListView.refresh() when anything changes. */
public final class TabCell extends ListCell<TerminalTab> {

    final Sidebar side;
    /** The corner under the number: a sleeping tab's moon, or a live tab's RAM. Drawn at twice its shown size. */
    static final javafx.scene.image.Image SLEEPING = new javafx.scene.image.Image(TabCell.class.getResourceAsStream("asleep.png"));
    final Label number = new Label(), name = new Label(), sub = new Label(), path = new Label(), state = new Label();
    final Button sleepNow = new Button("Zz");
    final Button close = new Button("×");
    final HBox row;
    final Tooltip tip = new Tooltip();

    TabCell(Sidebar side) {
        this.side = side;
        var t = side.theme;
        textOn(false, null);
        close.setStyle("-fx-background-color: transparent; -fx-text-fill: " + t.foreground + "; -fx-font-size: 19; -fx-font-weight: bold;"
                + " -fx-padding: 0 6 0 6; -fx-cursor: hand;");
        ramStyle = "-fx-text-fill: #ffffff; -fx-font-size: 10.5; -fx-font-weight: bold;";
        close.setFocusTraversable(false);
        close.visibleProperty().bind(hoverProperty());
        close.setOnAction(e -> { if (getItem() != null) side.app.close(getItem()); });
        var text = new VBox(-1, name, sub, path);
        text.setMinWidth(0);
        HBox.setHgrow(text, Priority.ALWAYS);
        // number on top, sleep mark or RAM under it, in a narrow column
        moon.setFitWidth(20);
        moon.setFitHeight(20);
        moon.setSmooth(true);
        // the moon or the RAM sits centred in the space left under the number
        // hovering the RAM swaps it for a Zz button: one click hibernates the tab
        sleepNow.setFocusTraversable(false);
        sleepNow.setVisible(false);
        sleepNow.setStyle("-fx-background-color: " + t.colors.get(6) + "; -fx-text-fill: #ffffff; -fx-font-size: 10.5;"
                + " -fx-font-weight: bold; -fx-padding: 0 5 0 5; -fx-background-radius: 3; -fx-cursor: hand;");
        sleepNow.setTooltip(new Tooltip("Hibernate this tab"));
        sleepNow.setOnAction(e -> { if (getItem() != null && getItem().live()) side.app.hibernate(getItem()); });
        var mark = new StackPane(state, sleepNow);
        mark.setAlignment(Pos.CENTER);
        mark.setOnMouseEntered(e -> { var live = getItem() != null && getItem().live(); sleepNow.setVisible(live); state.setVisible(!live); });
        mark.setOnMouseExited(e -> { sleepNow.setVisible(false); state.setVisible(true); });
        VBox.setVgrow(mark, Priority.ALWAYS);
        var corner = new VBox(0, number, mark);
        corner.setFillWidth(true);
        corner.setMinWidth(40);
        corner.setPrefWidth(40);
        state.setMinWidth(Region.USE_PREF_SIZE);
        corner.setAlignment(Pos.TOP_LEFT);
        row = new HBox(4, corner, text, close);
        row.setAlignment(Pos.CENTER_LEFT);
        tip.setShowDelay(javafx.util.Duration.millis(400));
        setPrefWidth(0);
        // drag a tab onto another to put it there
        setOnDragDetected(e -> {
            if (getItem() == null) return;
            var db = startDragAndDrop(TransferMode.MOVE);
            db.setDragView(snapshot(null, null));
            var c = new ClipboardContent();
            c.putString("tab " + getIndex());
            db.setContent(c);
            e.consume();
        });
        // the drop goes before this row (pointer in its top half) or after it (bottom half); a line shows which
        setOnDragOver(e -> {
            if (e.getGestureSource() instanceof TabCell src && src != this) {
                e.acceptTransferModes(TransferMode.MOVE);
                var after = e.getY() > getHeight() / 2;
                var line = side.theme.colors.get(4);
                setBorder(new Border(new BorderStroke(after ? Color.TRANSPARENT : Color.web(line), Color.TRANSPARENT,
                        after ? Color.web(line) : Color.TRANSPARENT, Color.TRANSPARENT, BorderStrokeStyle.SOLID, BorderStrokeStyle.NONE,
                        BorderStrokeStyle.SOLID, BorderStrokeStyle.NONE, CornerRadii.EMPTY, new BorderWidths(3, 0, 3, 0), Insets.EMPTY)));
            }
            e.consume();
        });
        setOnDragExited(e -> setBorder(null));
        // the right-click menu is built when asked for, not on every redraw (the list redraws as RAM figures change)
        setOnContextMenuRequested(e -> {
            var tab = getItem();
            if (tab == null) return;
            if (openMenu != null) openMenu.hide();
            openMenu = tab.separator ? side.separatorMenu(tab) : side.menu(tab);
            openMenu.show(this, e.getScreenX(), e.getScreenY());
            e.consume();
        });
        // a press on a separator only starts a drag: it must not try to select the separator, whose "cannot be
        // chosen" correction redraws the list under the drag
        addEventFilter(javafx.scene.input.MouseEvent.MOUSE_PRESSED, e -> { if (getItem() != null && getItem().separator && e.isPrimaryButtonDown()) e.consume(); });
        setOnDragDropped(e -> {
            if (e.getGestureSource() instanceof TabCell src && src.getItem() != null) {
                setBorder(null);
                var after = e.getY() > getHeight() / 2;
                var at = isEmpty() || getItem() == null ? Integer.MAX_VALUE : getIndex() + (after ? 1 : 0);
                // within this window it is a reorder (taking the tab out first shifts the rows below it up by one);
                // from another window the tab moves here, still running
                if (src.side == side) side.app.drop(src.getItem(), getIndex(), after);
                else side.app.app.moveTo(src.getItem(), side.app, at);
                e.setDropCompleted(true);
            }
            e.consume();
        });
        // dropped where nothing took it: outside every window it opens in a new window there, as a browser does
        setOnDragDone(e -> {
            var tab = getItem();
            if (e.getTransferMode() != null || tab == null || tab.owner == null || tab.separator) return;
            var at = TitleBar.cursor();
            if (at == null) return;
            var st = tab.owner.stage;
            var outside = at[0] < st.getX() || at[0] > st.getX() + st.getWidth() || at[1] < st.getY() || at[1] > st.getY() + st.getHeight();
            if (outside) side.app.app.newWindow(tab, at[0], at[1], tab.owner);
        });
        name.setMaxWidth(Double.MAX_VALUE);
        sub.setMaxWidth(Double.MAX_VALUE);
        path.setMaxWidth(Double.MAX_VALUE);
        sepLabel.setStyle("-fx-text-fill: " + t.colors.get(4) + "; -fx-font-size: 11.5; -fx-font-weight: bold;");
        var line = new Region();
        line.setStyle("-fx-background-color: " + Ui.shade(t.background, 40) + "; -fx-min-height: 1; -fx-max-height: 1; -fx-pref-height: 1;");
        HBox.setHgrow(line, Priority.ALWAYS);
        sepRow = new HBox(8, sepLabel, line);
        sepRow.setAlignment(Pos.CENTER_LEFT);
    }

    @Override
    protected void updateItem(TerminalTab t, boolean empty) {
        super.updateItem(t, empty);
        setText(null);
        if (empty || t == null) {
            setGraphic(null);
            setTooltip(null);
            setContextMenu(null);
            setStyle(Ui.cell(side.theme, false, null));
            setPrefHeight(Sidebar.ROW);
            return;
        }
        if (t.separator) {
            // a label with a line after it; nothing to open, nothing to measure
            sepLabel.setText(t.shown().toUpperCase(Locale.ROOT));
            setTooltip(null);
            setContextMenu(null);
            setGraphic(sepRow);
            setPrefHeight(Sidebar.SEPARATOR_ROW);
            setMinHeight(Sidebar.SEPARATOR_ROW);
            setMaxHeight(Sidebar.SEPARATOR_ROW);
            setStyle("-fx-padding: 0 6 0 8; -fx-background-color: " + Ui.shade(side.theme.background, -10) + ";");
            return;
        }
        setPrefHeight(Sidebar.ROW);
        setMinHeight(Sidebar.ROW);
        setMaxHeight(Sidebar.ROW);
        // numbered among real tabs only, as Ctrl/Alt+number counts them
        var n = 1;
        for (var i = 0; i < getIndex() && i < getListView().getItems().size(); i++) if (!getListView().getItems().get(i).separator) n++;
        number.setText(String.valueOf(n));
        name.setText(t.shown());
        var where = t.where();
        var folder = Path.of(where).getFileName();
        sub.setText(folder == null ? where : folder.toString());
        path.setText(where);
        if (t.live()) {
            state.setGraphic(null);
            state.setText(t.ramBytes > 0 ? shortMb(t.ramBytes) : "");
            state.setStyle(t.ramBytes > 0 ? ramBadge(t) : "");
        } else {
            state.setText(null);
            // the badge's fill goes too, or a reused row shows it behind the moon
            state.setStyle("");
            state.setGraphic(moon);
        }
        var ram = t.live() ? Mem.mb(t.ramBytes) : "asleep";
        tip.setText(t.shown() + "\n" + where + "\n" + (t.live() ? "RAM: " + ram + " (shell and the program it runs)" : "Asleep: no process; Enter restores it"));
        setTooltip(tip);
        setContextMenu(null);
        setGraphic(row);
        paint(isSelected(), t.color.get());
    }

    /** The row's fill, and text that stays readable on it. */
    void paint(boolean selected, String colour) {
        setStyle(Ui.cell(side.theme, selected, colour));
        textOn(selected, colour);
    }

    /** On the active tab the text is white or black, whichever stands out more from its fill, and the folder and
     *  path lines are dimmed less; elsewhere it is the theme's text colour. */
    void textOn(boolean selected, String colour) {
        var t = side.theme;
        var fg = selected ? Ui.textOn(Ui.frame(t, colour)) : colour != null ? Ui.shade(t.foreground, 20) : t.foreground;
        number.setStyle("-fx-text-fill: " + (selected ? fg : "#ffffff") + "; -fx-font-size: 13; -fx-font-weight: bold;");
        name.setStyle("-fx-text-fill: " + fg + "; -fx-font-size: 15; -fx-font-weight: bold;");
        sub.setStyle("-fx-text-fill: " + fg + "; -fx-opacity: " + (selected ? 0.9 : 0.75) + "; -fx-font-size: 12;");
        path.setStyle("-fx-text-fill: " + fg + "; -fx-opacity: " + (selected ? 0.75 : 0.55) + "; -fx-font-size: 10.5;");
    }

    final String ramStyle;
    final javafx.scene.image.ImageView moon = new javafx.scene.image.ImageView(SLEEPING);

    /** The RAM on a coloured pill: blue under 100 MB, yellow from 100, orange from 200; the tab using the most, once
     *  past 100 MB, red with a glow, so the heavy one stands out. */
    String ramBadge(TerminalTab t) {
        var mb = t.ramBytes / (1024.0 * 1024);
        var top = side.app.tabs.stream().filter(TerminalTab::live).mapToLong(x -> x.ramBytes).max().orElse(0);
        String bg, fg = "#ffffff", glow = "";
        if (t.ramBytes == top && mb >= 100) {
            bg = "#d64545";
            glow = " -fx-effect: dropshadow(gaussian, #ff5a5a, 8, 0.45, 0, 0);";
        } else if (mb >= 200) {
            bg = "#e07b24";
        } else if (mb >= 100) {
            bg = "#d9b23a";
            fg = "#1a1a1a";
        } else {
            bg = "#2f6fd6";
        }
        return "-fx-background-color: " + bg + "; -fx-text-fill: " + fg + "; -fx-font-size: 10.5; -fx-font-weight: bold;"
                + " -fx-padding: 0 3 0 3; -fx-background-radius: 3;" + glow;
    }

    /** 214M, 1.2G: fits under the number. */
    static String shortMb(long bytes) {
        var mb = bytes / (1024.0 * 1024);
        return mb >= 1000 ? "%.1fG".formatted(mb / 1024) : "%.0fM".formatted(mb);
    }

    ContextMenu openMenu;
    final Label sepLabel = new Label();
    final HBox sepRow;

    @Override
    public void updateSelected(boolean selected) {
        super.updateSelected(selected);
        if (getItem() != null && getItem().separator) return;
        paint(selected && !isEmpty(), isEmpty() || getItem() == null ? null : getItem().color.get());
    }
}
