package fxterm.tabs;

import module java.base;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.input.*;
import javafx.scene.layout.*;

/** A tab's row: its number (Alt+number selects it) with a moon under it when asleep or its RAM when live; its name,
 *  and under that its folder; a
 *  close button on hover; the full path on hover too. Re-rendered by ListView.refresh() when anything changes. */
public final class TabCell extends ListCell<TerminalTab> {

    final Sidebar side;
    /** The corner under the number: a sleeping tab's moon, or a live tab's RAM. Drawn at twice its shown size. */
    static final javafx.scene.image.Image SLEEPING = new javafx.scene.image.Image(TabCell.class.getResourceAsStream("asleep.png"));
    final Label number = new Label(), name = new Label(), sub = new Label(), state = new Label();
    final Button close = new Button("×");
    final HBox row;
    final Tooltip tip = new Tooltip();

    TabCell(Sidebar side) {
        this.side = side;
        var t = side.theme;
        number.setStyle("-fx-text-fill: " + t.foreground + "; -fx-opacity: 0.55; -fx-font-size: 13; -fx-font-weight: bold;");
        name.setStyle("-fx-text-fill: " + t.foreground + "; -fx-font-size: 15; -fx-font-weight: bold;");
        sub.setStyle("-fx-text-fill: " + t.foreground + "; -fx-opacity: 0.6; -fx-font-size: 12;");
        close.setStyle("-fx-background-color: transparent; -fx-text-fill: " + t.foreground + "; -fx-font-size: 14; -fx-padding: 0 4 0 4; -fx-cursor: hand;");
        ramStyle = "-fx-text-fill: " + t.colors.get(2) + "; -fx-font-size: 10.5; -fx-font-weight: bold; -fx-opacity: 0.85;";
        close.setFocusTraversable(false);
        close.visibleProperty().bind(hoverProperty());
        close.setOnAction(e -> { if (getItem() != null) side.app.close(getItem()); });
        var text = new VBox(2, name, sub);
        text.setMinWidth(0);
        HBox.setHgrow(text, Priority.ALWAYS);
        // number on top, sleep mark or RAM under it, in a narrow column
        moon.setFitWidth(20);
        moon.setFitHeight(20);
        moon.setSmooth(true);
        var corner = new VBox(1, number, state);
        corner.setMinWidth(30);
        corner.setPrefWidth(30);
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
        setOnDragOver(e -> {
            if (e.getGestureSource() instanceof TabCell src && src != this && src.side == side) e.acceptTransferModes(TransferMode.MOVE);
            e.consume();
        });
        setOnDragDropped(e -> {
            if (e.getGestureSource() instanceof TabCell src && src.getItem() != null) {
                side.app.move(src.getItem(), isEmpty() || getItem() == null ? Integer.MAX_VALUE : getIndex());
                e.setDropCompleted(true);
            }
            e.consume();
        });
        name.setMaxWidth(Double.MAX_VALUE);
        sub.setMaxWidth(Double.MAX_VALUE);
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
            return;
        }
        number.setText(String.valueOf(getIndex() + 1));
        name.setText(t.shown());
        var where = t.where();
        var folder = Path.of(where).getFileName();
        sub.setText(folder == null ? where : folder.toString());
        if (t.live()) {
            state.setGraphic(null);
            state.setText(t.ramBytes > 0 ? shortMb(t.ramBytes) : "");
            state.setStyle(ramStyle);
        } else {
            state.setText(null);
            state.setGraphic(moon);
        }
        var ram = t.live() ? Mem.mb(t.ramBytes) : "asleep";
        tip.setText(t.shown() + "\n" + where + "\n" + (t.live() ? "RAM: " + ram + " (shell and the program it runs)" : "Asleep: no process; Enter restores it"));
        setTooltip(tip);
        setContextMenu(side.menu(t));
        setGraphic(row);
        setStyle(Ui.cell(side.theme, isSelected(), t.color.get()));
    }

    final String ramStyle;
    final javafx.scene.image.ImageView moon = new javafx.scene.image.ImageView(SLEEPING);

    /** 214M, 1.2G: fits under the number. */
    static String shortMb(long bytes) {
        var mb = bytes / (1024.0 * 1024);
        return mb >= 1000 ? "%.1fG".formatted(mb / 1024) : "%.0fM".formatted(mb);
    }

    @Override
    public void updateSelected(boolean selected) {
        super.updateSelected(selected);
        setStyle(Ui.cell(side.theme, selected && !isEmpty(), isEmpty() || getItem() == null ? null : getItem().color.get()));
    }
}
