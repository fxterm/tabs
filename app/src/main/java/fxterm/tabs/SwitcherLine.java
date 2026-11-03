package fxterm.tabs;

import module java.base;
import javafx.geometry.*;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;

/** One line of the switcher's list, drawn by its kind; only tab lines can be clicked (twice to go there). */
final class SwitcherLine extends ListCell<SwitcherRow> {

    final TabbyTheme theme;
    final Runnable go, refocus;

    SwitcherLine(TabbyTheme theme, Runnable go, Runnable refocus) {
        this.theme = theme;
        this.go = go;
        this.refocus = refocus;
    }

    @Override
    protected void updateItem(SwitcherRow r, boolean empty) {
        super.updateItem(r, empty);
        setText(null);
        setOnMouseClicked(null);
        if (empty || r == null) {
            setGraphic(null);
            setMouseTransparent(false);
            return;
        }
        setMouseTransparent(r.kind != SwitcherRow.Kind.TAB);
        setGraphic(switch (r.kind) {
            case SECTION -> label(r.text, "-fx-text-fill: " + dim() + "; -fx-font-size: 11; -fx-font-weight: bold; -fx-padding: 12 0 2 6;");
            case WINDOW -> window(r);
            case GROUP -> label(r.text.toUpperCase(Locale.ROOT), "-fx-text-fill: " + (r.colour != null ? r.colour : theme.colors.get(4))
                    + "; -fx-font-size: 11; -fx-font-weight: bold; -fx-padding: 2 0 0 24;");
            case TAB -> tab(r);
        });
        restyle();
    }

    @Override
    public void updateSelected(boolean selected) {
        super.updateSelected(selected);
        restyle();
    }

    /** An inline background would hide the selection's, so the chosen tab line gets its own here. */
    private void restyle() {
        var r = getItem();
        if (r == null) return;
        var bar = r.kind == SwitcherRow.Kind.TAB && r.colour != null;
        var fill = isSelected() && r.kind == SwitcherRow.Kind.TAB ? Ui.shade(theme.background, 26) : "transparent";
        setStyle("-fx-padding: 3 6 3 " + (bar ? 3 : 6) + "; -fx-background-color: " + fill + "; -fx-background-radius: 5;"
                + (bar ? " -fx-border-color: " + r.colour + "; -fx-border-width: 0 0 0 3;" : ""));
    }

    private String dim() {
        return Ui.mix(theme.foreground, theme.background, 0.45);
    }

    private static Label label(String text, String style) {
        var l = new Label(text);
        l.setStyle(style);
        return l;
    }

    private HBox window(SwitcherRow r) {
        var dot = new Circle(5, Color.web(r.colour == null ? theme.colors.get(4) : r.colour));
        var box = new HBox(8, dot, label(r.text, "-fx-text-fill: " + theme.foreground + "; -fx-font-size: 13.5; -fx-font-weight: bold;"));
        box.setAlignment(Pos.CENTER_LEFT);
        box.setPadding(new Insets(6, 0, 1, 6));
        return box;
    }

    private HBox tab(SwitcherRow r) {
        var t = r.tab;
        var number = label(String.valueOf(r.number), "-fx-text-fill: " + dim() + "; -fx-font-size: 13; -fx-font-weight: bold;");
        number.setMinWidth(30);
        number.setAlignment(Pos.CENTER_RIGHT);
        var asks = t.attention();
        var name = label((asks && !TabCell.attention(t.shown()) ? "D83DDD14 " : "") + r.text, "-fx-text-fill: " + (asks ? Ui.ASKING : theme.foreground) + "; -fx-font-size: 14;" + (asks ? " -fx-font-weight: bold;" : ""));
        name.setMinWidth(0);
        var folder = Path.of(t.where()).getFileName();
        var where = label(folder == null ? t.where() : folder.toString(), "-fx-text-fill: " + dim() + "; -fx-font-size: 11.5;");
        where.setMinWidth(0);
        var spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        var box = new HBox(8, number, name, spacer, where);
        box.setAlignment(Pos.CENTER_LEFT);
        box.setOpacity(t.live() ? 1 : 0.75);
        setOnMouseClicked(e -> { if (e.getClickCount() == 2) go.run(); else refocus.run(); });
        return box;
    }
}
