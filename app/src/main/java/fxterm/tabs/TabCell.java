package fxterm.tabs;

import module java.base;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.input.*;
import javafx.scene.layout.*;

/** A tab's row: its number (Alt+number selects it), its name, and under that the folder and the RAM it uses; a
 *  close button on hover; the full path on hover too. Re-rendered by ListView.refresh() when anything changes. */
public final class TabCell extends ListCell<TerminalTab> {

    final Sidebar side;
    final Label number = new Label(), name = new Label(), sub = new Label();
    final Button close = new Button("×");
    final HBox row;
    final Tooltip tip = new Tooltip();

    TabCell(Sidebar side) {
        this.side = side;
        var t = side.theme;
        number.setStyle("-fx-text-fill: " + t.foreground + "; -fx-opacity: 0.45; -fx-font-size: 12; -fx-min-width: 18;");
        name.setStyle("-fx-text-fill: " + t.foreground + "; -fx-font-size: 13;");
        sub.setStyle("-fx-text-fill: " + t.foreground + "; -fx-opacity: 0.55; -fx-font-size: 11;");
        close.setStyle("-fx-background-color: transparent; -fx-text-fill: " + t.foreground + "; -fx-font-size: 14; -fx-padding: 0 4 0 4; -fx-cursor: hand;");
        close.setFocusTraversable(false);
        close.visibleProperty().bind(hoverProperty());
        close.setOnAction(e -> { if (getItem() != null) side.app.close(getItem()); });
        var text = new VBox(2, name, sub);
        text.setMinWidth(0);
        HBox.setHgrow(text, Priority.ALWAYS);
        row = new HBox(6, number, text, close);
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
        name.setText((t.asleep.get() ? "zz  " : "") + t.shown());
        var where = t.where();
        var folder = Path.of(where).getFileName();
        var ram = t.live() ? Mem.mb(t.ramBytes) : "asleep";
        sub.setText((folder == null ? where : folder.toString()) + "  ·  " + ram);
        tip.setText(t.shown() + "\n" + where + "\nRAM: " + ram + (t.live() ? " (shell and the program it runs)" : ", no process"));
        setTooltip(tip);
        setContextMenu(side.menu(t));
        setGraphic(row);
        setStyle(Ui.cell(side.theme, isSelected(), t.color.get()));
    }

    @Override
    public void updateSelected(boolean selected) {
        super.updateSelected(selected);
        setStyle(Ui.cell(side.theme, selected && !isEmpty(), isEmpty() || getItem() == null ? null : getItem().color.get()));
    }
}
