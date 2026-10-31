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
    /** The corner under the number: a sleeping tab's moon (the icon in grey), or a live tab's RAM. */
    static final javafx.scene.image.Image SLEEPING = new javafx.scene.image.Image(TabCell.class.getResourceAsStream("asleep.png"));
    final javafx.scene.image.ImageView moon = new javafx.scene.image.ImageView(SLEEPING);
    final Label number = new Label(), name = new Label(), sub = new Label(), path = new Label(), state = new Label();
    final Button sleepNow = new Button("Zz");
    /** On a sleeping tab, hovering its grey moon shows this yellow sun: one click wakes the tab. */
    final Button wakeNow = new Button("☀");
    HBox topBox;
    VBox textBox;
    final Button close = new Button("×");
    final HBox row;
    final Tooltip tip = new Tooltip();
    final javafx.scene.image.ImageView project = new javafx.scene.image.ImageView();
    /** The tab being dragged, from any window (one drag at a time in the process). */
    static TerminalTab dragged;

    TabCell(Sidebar side) {
        this.side = side;
        var t = side.theme;
        textOn(false, null);
        close.setStyle("-fx-background-color: transparent; -fx-text-fill: " + t.foreground + "; -fx-font-size: 19; -fx-font-weight: bold;"
                + " -fx-padding: 0 6 0 6; -fx-cursor: hand;");
        ramStyle = "-fx-text-fill: #ffffff; -fx-font-size: 10.5; -fx-font-weight: bold;";
        close.setFocusTraversable(false);
        close.visibleProperty().bind(hoverProperty());
        close.setOnAction(e -> { if (getItem() != null) side.app.requestClose(getItem()); });
        // the animated symbol programs put at the front of their title (claude's stars, braille dots, | / - \) is not
        // shown as text: its frames differ in width and baseline and the name beside it twitched. A drawn mark of
        // one fixed size stands in for it (see ActivityMark).
        HBox.setHgrow(name, Priority.ALWAYS);
        var nameRow = new HBox(2, activity, name);
        nameRow.setAlignment(Pos.CENTER_LEFT);
        var text = new VBox(-1, nameRow, sub, path);
        textBox = text;
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
        wakeNow.setFocusTraversable(false);
        wakeNow.setVisible(false);
        wakeNow.setStyle("-fx-background-color: transparent; -fx-text-fill: #f2c94c; -fx-font-size: 18; -fx-font-family: 'Segoe UI Symbol';"
                + " -fx-padding: 0 4 0 4; -fx-cursor: hand; -fx-effect: dropshadow(gaussian, rgba(242,201,76,0.55), 8, 0.3, 0, 0);");
        wakeNow.setTooltip(new Tooltip("Wake this tab"));
        wakeNow.setOnAction(e -> { if (getItem() != null && !getItem().live()) getItem().wake.run(); });
        var mark = new StackPane(state, sleepNow, wakeNow);
        mark.setAlignment(Pos.CENTER);
        // a live tab's RAM becomes Zz on hover, a sleeping tab's grey moon becomes a yellow sun
        mark.setOnMouseEntered(e -> {
            var tab = getItem();
            if (tab == null || tab.separator) return;
            sleepNow.setVisible(tab.live() && !tab.hibernating);
            wakeNow.setVisible(!tab.live());
            state.setVisible(false);
        });
        mark.setOnMouseExited(e -> { sleepNow.setVisible(false); wakeNow.setVisible(false); state.setVisible(true); });
        VBox.setVgrow(mark, Priority.ALWAYS);
        // the project's icon (from proj's prp/proj.json) beside the number
        // larger than the number, and centred in the room left beside it rather than pressed against it
        project.setFitWidth(22);
        project.setFitHeight(22);
        project.setSmooth(true);
        var iconBox = new StackPane(project);
        iconBox.setAlignment(Pos.CENTER);
        iconBox.setMinHeight(26);
        HBox.setHgrow(iconBox, Priority.ALWAYS);
        // the number sits right-aligned in room for two digits, so 1..9 line up with 10 and up
        number.setMinWidth(18);
        number.setPrefWidth(18);
        number.setAlignment(Pos.CENTER_RIGHT);
        var top = new HBox(0, number, iconBox);
        topBox = top;
        // the number on the icon's middle line, not hanging at its top
        top.setAlignment(Pos.CENTER_LEFT);
        top.setMinHeight(26);
        var corner = new VBox(0, top, mark);
        corner.setFillWidth(true);
        corner.setMinWidth(48);
        corner.setPrefWidth(48);
        state.setMinWidth(Region.USE_PREF_SIZE);
        corner.setAlignment(Pos.TOP_LEFT);
        row = new HBox(4, corner, text, close);
        row.setAlignment(Pos.CENTER_LEFT);
        tip.setShowDelay(javafx.util.Duration.millis(400));
        setPrefWidth(0);
        // drag a tab onto another to put it there
        // The dragged tab is remembered when the drag starts. The list redraws during a drag (RAM figures, titles) and
        // a row may then show another tab, so asking the source row at the drop moved the wrong tab.
        setOnDragDetected(e -> {
            if (getItem() == null) return;
            dragged = getItem();
            Events.log("drag-start", "tab", Events.tab(dragged), "row", getIndex());
            var db = startDragAndDrop(TransferMode.MOVE);
            db.setDragView(snapshot(null, null));
            var c = new ClipboardContent();
            c.putString("tab " + dragged.id);
            db.setContent(c);
            e.consume();
        });
        // the drop goes before this row (pointer in its top half) or after it (bottom half); a line shows which
        setOnDragOver(e -> {
            if (dragged != null && e.getGestureSource() instanceof TabCell && dragged != getItem()) {
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
        addEventFilter(javafx.scene.input.MouseEvent.MOUSE_PRESSED, e -> {
            // logged so a drag can be checked against the row the mouse actually went down on
            if (getItem() != null && e.isPrimaryButtonDown()) Events.log("press", "tab", Events.tab(getItem()), "row", getIndex(), "y", Math.round(e.getY()));
            if (getItem() != null && getItem().separator && e.isPrimaryButtonDown()) e.consume();
        });
        setOnDragDropped(e -> {
            var tab = dragged;
            if (tab != null && e.getGestureSource() instanceof TabCell) {
                dragged = null;
                setBorder(null);
                var after = e.getY() > getHeight() / 2;
                var at = isEmpty() || getItem() == null ? Integer.MAX_VALUE : getIndex() + (after ? 1 : 0);
                Events.log("drop", "tab", Events.tab(tab), "onRow", getIndex(), "onto", Events.tab(getItem()), "after", after);
                // within this window it is a reorder (taking the tab out first shifts the rows below it up by one);
                // from another window the tab moves here, still running
                if (tab.owner == side.app) side.app.drop(tab, getIndex(), after);
                else if (tab.separator) {
                    // a group goes to the other window whole, in its order
                    var block = tab.owner.group(tab);
                    for (var i = 0; i < block.size(); i++) side.app.app.moveTo(block.get(i), side.app, at == Integer.MAX_VALUE ? at : at + i);
                }
                else side.app.app.moveTo(tab, side.app, at);
                Events.log("dropped", "tab", Events.tab(tab));
                e.setDropCompleted(true);
            }
            e.consume();
        });
        // dropped where nothing took it: outside every window it opens in a new window there, as a browser does
        setOnDragDone(e -> {
            var tab = dragged;
            dragged = null;
            // every row of every window drops its line, wherever the drag ended
            side.app.app.windows.forEach(w -> w.list.lookupAll(".list-cell").forEach(c -> { if (c instanceof Region r) r.setBorder(null); }));
            if (e.getTransferMode() != null || tab == null || tab.owner == null || tab.separator) return;
            Events.log("drag-out", "tab", Events.tab(tab));
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
        sepLine.setStyle("-fx-background-color: " + Ui.shade(t.background, 40) + "; -fx-min-height: 1; -fx-max-height: 1; -fx-pref-height: 1;");
        HBox.setHgrow(sepLine, Priority.ALWAYS);
        arrow.setStyle("-fx-text-fill: " + t.colors.get(4) + "; -fx-font-size: 12; -fx-min-width: 12;");
        sepRow = new HBox(6, arrow, sepLabel, sepLine);
        sepRow.setAlignment(Pos.CENTER_LEFT);
        // a click on a label (not the end of a drag) folds or unfolds its group
        setOnMouseClicked(e -> {
            var item = getItem();
            if (item != null && item.separator && e.getButton() == javafx.scene.input.MouseButton.PRIMARY && e.isStillSincePress()) {
                side.app.toggleGroup(item);
                e.consume();
            }
        });
    }

    @Override
    protected void updateItem(TerminalTab t, boolean empty) {
        super.updateItem(t, empty);
        setText(null);
        // a row that no longer shows a tab (empty, or a group label) lets go of the last tab's title
        if (empty || t == null || t.separator) {
            name.textProperty().unbind();
            activity.show(null, Color.WHITE, Color.WHITE);
        }
        // the drop line belongs to a drag in progress; a row reused for another tab must not carry it along
        // (only when there is one: setting it, even to the same null, replaces the row's CSS side bar for a moment)
        if (dragged == null && getBorder() != null && getBorder().getStrokes().stream().anyMatch(s -> s.getWidths().getTop() == 3)) setBorder(null);
        if (empty || t == null) {
            setGraphic(null);
            setTooltip(null);
            setContextMenu(null);
            setStyle(Ui.cell(side.theme, false, null));
            setPrefHeight(Sidebar.ROW);
            return;
        }
        // a tab in a folded group takes no room (it keeps its place in the list, so indexes stay what they were)
        if (side.app.folded(t)) {
            setGraphic(null);
            setTooltip(null);
            setContextMenu(null);
            setStyle("-fx-padding: 0; -fx-background-color: transparent; -fx-border-width: 0;");
            setMinHeight(0);
            setPrefHeight(0);
            setMaxHeight(0);
            return;
        }
        if (t.separator) {
            // an arrow, the label (with how many tabs it holds while folded), a line after it
            var inside = side.app.group(t).size() - 1;
            // the label, its arrow and its line take the group's colour, which the tabs under it wear as their side bar
            var group = t.color.get() != null ? t.color.get() : side.theme.colors.get(4);
            sepLabel.setStyle("-fx-text-fill: " + group + "; -fx-font-size: 11.5; -fx-font-weight: bold;");
            arrow.setStyle("-fx-text-fill: " + group + "; -fx-font-size: 12; -fx-min-width: 12;");
            sepLine.setStyle("-fx-background-color: " + (t.color.get() != null ? group : Ui.shade(side.theme.background, 40))
                    + "; -fx-min-height: 1; -fx-max-height: 1; -fx-pref-height: 1;");
            arrow.setText(t.collapsed ? "▸" : "▾");
            sepLabel.setText(t.shown().toUpperCase(Locale.ROOT) + (t.collapsed ? "  (" + inside + ")" : ""));
            setTooltip(sepTip);
            sepTip.setText((t.collapsed ? "Click to show its " + inside + " tabs" : "Click to fold this group") + "; drag to move the group");
            setTooltip(null);
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
        // numbered among real tabs only, as Ctrl/Alt+number counts them, and straight on from the windows before this one
        var n = side.app.firstNumber();
        for (var i = 0; i < getIndex() && i < getListView().getItems().size(); i++) if (!getListView().getItems().get(i).separator) n++;
        number.setText(String.valueOf(n));
        var icon = ProjectIcon.of(t.where());
        project.setImage(icon);
        project.setVisible(icon != null);
        project.setManaged(icon != null);
        // the name follows the tab's title by itself: a title a program rewrites ten times a second changes this one
        // label and nothing else, with no redraw of the list
        name.textProperty().unbind();
        name.textProperty().bind(javafx.beans.binding.Bindings.createStringBinding(() -> plain(t.shown()), t.title, t.custom));
        var hasGlyph = javafx.beans.binding.Bindings.createBooleanBinding(() -> glyphOf(t.shown()) == 1, t.title, t.custom);
        activity.visibleProperty().unbind();
        activity.managedProperty().unbind();
        activity.visibleProperty().bind(hasGlyph);
        activity.managedProperty().bind(hasGlyph);
        var where = t.where();
        var folder = Path.of(where).getFileName();
        sub.setText(folder == null ? where : folder.toString());
        path.setText(where);
        if (t.hibernating) {
            state.setGraphic(null);
            state.setText("Zz…");
            state.setStyle("-fx-background-color: " + side.theme.colors.get(6) + "; " + ramStyle + " -fx-padding: 0 3 0 3; -fx-background-radius: 3;");
        } else if (t.live()) {
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
        tip.setText(plain(t.shown()) + "\n" + where + "\n" + (t.live() ? "RAM: " + ram + " (shell and the program it runs)" : "Asleep: no process; Enter restores it"));
        setTooltip(tip);
        setContextMenu(null);
        setGraphic(row);
        paint(isSelected(), side.app.colourOf(t));
    }

    /** The row's fill, and text that stays readable on it. */
    void paint(boolean selected, String colour) {
        setStyle(Ui.cell(side.theme, selected, colour));
        textOn(selected, colour);
        // a tab asleep is dulled (its text, number and icon) so the live ones stand out; the active one a little less
        var asleep = getItem() != null && !getItem().separator && !getItem().live();
        var dull = asleep ? (selected ? 0.75 : 0.45) : 1.0;
        if (textBox != null) textBox.setOpacity(dull);
        if (topBox != null) topBox.setOpacity(dull);
    }

    /** On the active tab the text is white or black, whichever stands out more from its fill, and the folder and
     *  path lines are dimmed less; elsewhere it is the theme's text colour. */
    void textOn(boolean selected, String colour) {
        var t = side.theme;
        var fg = selected ? Ui.textOn(Ui.frame(t, colour)) : colour != null && Ui.fullColour ? Ui.shade(t.foreground, 20) : t.foreground;
        number.setStyle("-fx-text-fill: " + (selected ? fg : "#ffffff") + "; -fx-font-size: 13; -fx-font-weight: bold;");
        name.setStyle("-fx-text-fill: " + fg + "; -fx-font-size: 15; -fx-font-weight: bold;");
        activity.show(getItem(), Color.web(fg), Color.web(side.theme.colors.get(4)));
        sub.setStyle("-fx-text-fill: " + fg + "; -fx-opacity: " + (selected ? 0.9 : 0.75) + "; -fx-font-size: 12;");
        path.setStyle("-fx-text-fill: " + fg + "; -fx-opacity: " + (selected ? 0.75 : 0.55) + "; -fx-font-size: 10.5;");
    }

    final String ramStyle;
    final ActivityMark activity = new ActivityMark();

    /** Whether the title starts with a program's symbol (1) or not (0): the only thing about it the row shows. */
    static int glyphOf(String title) {
        return GLYPH.matcher(title).matches() ? 1 : 0;
    }

    /** The title without the symbol claude puts first. */
    static String plain(String title) {
        var m = GLYPH.matcher(title);
        return m.matches() ? m.group(2) : title;
    }
    /** A symbol, then the name: the spinner and status marks programs put first. The known families: claude's
     *  (· ✢ ✳ ✶ ✻ ✽), braille dots (npm, pip, ora, cargo: ⠋⠙⠹⠸⠼⠴⠦⠧⠇⠏ and ⣾⣽⣻⢿⡿⣟⣯⣷), the quarter circles and
     *  circle halves (◐◓◑◒ ◴◷◶◵ ◜◠◝◞◡◟), the arrows (←↖↑↗→↘↓↙), the block bars (▖▘▝▗ ▁▃▄▅▆▇), the star and dingbat
     *  runs, and the plain ASCII wheel | / - \ (and *). */
    static final Pattern GLYPH = Pattern.compile("^([\\p{So}\\p{Sm}\\u00B7\\u2190-\\u21FF\\u2580-\\u259F\\u25A0-\\u25FF\\u2800-\\u28FF\\u2700-\\u27BF*|/\\\\-])\\s+(.+)$", Pattern.DOTALL);

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
    final Label sepLabel = new Label(), arrow = new Label();
    final Region sepLine = new Region();
    final Tooltip sepTip = new Tooltip();
    final HBox sepRow;

    @Override
    public void updateSelected(boolean selected) {
        super.updateSelected(selected);
        if (getItem() != null && getItem().separator) return;
        paint(selected && !isEmpty(), isEmpty() || getItem() == null ? null : side.app.colourOf(getItem()));
    }
}
