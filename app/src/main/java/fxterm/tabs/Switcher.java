package fxterm.tabs;

import module java.base;
import fxterm.tabs.SwitcherRow.Kind;
import javafx.animation.PauseTransition;
import javafx.application.Platform;
import javafx.geometry.*;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.input.*;
import javafx.scene.layout.*;
import javafx.stage.*;
import javafx.util.Duration;

/** Ctrl+Tab: a popup that goes to any tab of any window. Left, a list that scrolls (the awake tabs under their group under
 *  their window, then the hibernated ones the same way); right, what the highlighted tab shows now (its saved text when
 *  it sleeps). The box on top filters as you type: a tab's number, part of its name or folder, its window's or group's
 *  name, several words at once. Up and Down move, Enter goes there, Backspace takes back what was typed (Ctrl+Backspace
 *  clears it), Esc closes. */
final class Switcher {

    final TabsApp app;
    final PauseTransition previewSoon = new PauseTransition(Duration.millis(70));
    Stage stage;
    TabsWindow from;
    TextField filter;
    ListView<SwitcherRow> list;
    TextArea preview;
    Label previewHead;
    boolean filling;

    Switcher(TabsApp app) {
        this.app = app;
    }

    /** Opens over `window` with its shown tab chosen, then moved by `step` (Ctrl+Tab: the next, Ctrl+Shift+Tab: the one
     *  above, 0: where it is). Pressed again while open, it moves on. */
    void open(TabsWindow window, int step) {
        if (stage != null && stage.isShowing()) {
            move(step == 0 ? 1 : step);
            return;
        }
        from = window;
        var theme = app.theme;
        var fg = theme.foreground;
        filter = new TextField();
        filter.setPromptText("Type a number or part of a name…");
        filter.setStyle("-fx-font-size: 15; -fx-padding: 8 10 8 10;");
        list = new ListView<>();
        list.setFocusTraversable(false);
        list.setCellFactory(v -> new SwitcherLine(theme, this::confirm, () -> Platform.runLater(filter::requestFocus)));
        list.setStyle("-fx-background-color: transparent; -fx-control-inner-background: " + Ui.shade(theme.background, -4) + ";");
        previewHead = new Label();
        previewHead.setStyle("-fx-text-fill: " + fg + "; -fx-font-size: 13; -fx-font-weight: bold; -fx-padding: 2 4 6 4;");
        preview = new TextArea();
        preview.setEditable(false);
        preview.setFocusTraversable(false);
        preview.setStyle("-fx-control-inner-background: " + theme.background + "; -fx-text-fill: " + fg + "; -fx-font-family: '" + app.options.family
                + "'; -fx-font-size: 11.5; -fx-background-radius: 6; -fx-focus-color: transparent; -fx-faint-focus-color: transparent;");
        VBox.setVgrow(preview, Priority.ALWAYS);
        var right = new VBox(0, previewHead, preview);
        right.setPadding(new Insets(0, 0, 0, 10));
        var left = new VBox(8, filter, list);
        VBox.setVgrow(list, Priority.ALWAYS);
        var body = new SplitPane(left, right);
        body.setDividerPositions(0.46);
        body.setStyle("-fx-background-color: transparent; -fx-padding: 0;");
        var hint = new Label(Os.keys("Up / Down move     Enter go     Backspace edit     Esc close     Ctrl+Tab again: next"));
        hint.setStyle("-fx-text-fill: " + Ui.mix(fg, theme.background, 0.45) + "; -fx-font-size: 11;");
        var root = new BorderPane(body);
        BorderPane.setMargin(body, new Insets(0, 0, 8, 0));
        root.setBottom(hint);
        root.setPadding(new Insets(14));
        root.setStyle("-fx-background-color: " + Ui.shade(theme.background, -4) + "; -fx-border-color: " + Ui.shade(theme.background, 40)
                + "; -fx-border-width: 1; -fx-background-radius: 8; -fx-border-radius: 8;");
        var owner = window.stage;
        var width = Math.min(1040, owner.getWidth() * 0.92);
        var height = Math.min(600, owner.getHeight() * 0.88);
        var scene = new Scene(root, width, height);
        scene.getStylesheets().add(Ui.stylesheet(theme));
        scene.getPreferences().setColorScheme(theme.dark() ? javafx.application.ColorScheme.DARK : javafx.application.ColorScheme.LIGHT);
        scene.addEventFilter(KeyEvent.KEY_PRESSED, this::keys);
        scene.addEventFilter(KeyEvent.ANY, e -> IdleTrim.activity());
        stage = new Stage(StageStyle.UNDECORATED);
        stage.initOwner(owner);
        stage.setScene(scene);
        stage.setX(owner.getX() + (owner.getWidth() - width) / 2);
        stage.setY(owner.getY() + (owner.getHeight() - height) / 2);
        previewSoon.setOnFinished(e -> showPreview());
        list.getSelectionModel().selectedItemProperty().addListener((ov, was, now) -> chosen(was, now));
        filter.textProperty().addListener((ov, was, now) -> fill(now, now.isBlank() ? window.selected() : null));
        fill("", window.selected());
        if (step != 0) move(step);
        stage.show();
        stage.focusedProperty().addListener((ov, was, now) -> { if (!now) close(false); });
        Platform.runLater(filter::requestFocus);
    }

    void keys(KeyEvent e) {
        switch (e.getCode()) {
            case ESCAPE -> close(true);
            case ENTER -> confirm();
            case DOWN -> move(1);
            case UP -> move(-1);
            case PAGE_DOWN -> move(8);
            case PAGE_UP -> move(-8);
            case TAB -> move(e.isShiftDown() ? -1 : 1);
            case BACK_SPACE -> { if (!e.isControlDown() && !e.isMetaDown()) return; filter.clear(); }
            default -> { return; }
        }
        e.consume();
    }

    /** A heading cannot be chosen: the choice goes back to the tab that had it. */
    void chosen(SwitcherRow was, SwitcherRow now) {
        if (filling) return;
        if (now != null && now.kind != Kind.TAB) Platform.runLater(() -> { if (was != null && list.getItems().contains(was)) list.getSelectionModel().select(was); });
        else previewSoon.playFromStart();
    }

    /** The rows for what is typed. With nothing typed the shown tab stays chosen; with a number typed the tab with exactly
     *  that number is chosen, else the first tab that fits. */
    void fill(String query, TerminalTab keep) {
        var tokens = Arrays.stream(query.toLowerCase(Locale.ROOT).split("\\s+")).filter(s -> !s.isBlank()).toList();
        var rows = SwitcherRows.build(app, tokens);
        filling = true;
        try {
            list.getItems().setAll(rows);
        } finally {
            filling = false;
        }
        var exact = rows.stream().filter(r -> r.kind == Kind.TAB && (keep != null ? r.tab == keep : tokens.contains(String.valueOf(r.number)))).findFirst();
        var pick = exact.or(() -> rows.stream().filter(r -> r.kind == Kind.TAB).findFirst()).map(rows::indexOf).orElse(-1);
        if (pick < 0) {
            list.getSelectionModel().clearSelection();
            previewHead.setText(tokens.isEmpty() ? "" : "Nothing matches “" + String.join(" ", tokens) + "”");
            preview.clear();
            return;
        }
        list.getSelectionModel().select(pick);
        Platform.runLater(() -> list.scrollTo(Math.max(0, pick - 4)));
    }

    /** Moves the choice over tabs only, round the ends for one step; a page stops at them. */
    void move(int by) {
        var tabs = IntStream.range(0, list.getItems().size()).filter(i -> list.getItems().get(i).kind == Kind.TAB).boxed().toList();
        if (tabs.isEmpty()) return;
        var at = tabs.indexOf(list.getSelectionModel().getSelectedIndex());
        var to = at < 0 ? (by > 0 ? 0 : tabs.size() - 1) : Math.abs(by) == 1 ? Math.floorMod(at + by, tabs.size()) : Math.clamp(at + by, 0, tabs.size() - 1);
        list.getSelectionModel().select(tabs.get(to));
        list.scrollTo(Math.max(0, tabs.get(to) - 3));
    }

    void showPreview() {
        var row = list.getSelectionModel().getSelectedItem();
        if (row == null || row.tab == null) return;
        var t = row.tab;
        previewHead.setText(row.number + "   " + TabCell.plain(t.shown()) + (t.live() ? "" : "   (asleep)") + "\n" + t.where());
        var text = t.live() ? t.screenText() : t.sleeping != null ? t.sleeping.screen : null;
        var lines = (text == null || text.isBlank() ? (t.live() ? "" : "Nothing saved for this tab.") : text).split("\n", -1);
        preview.setText(String.join("\n", Arrays.copyOfRange(lines, Math.max(0, lines.length - 90), lines.length)));
        Platform.runLater(() -> preview.setScrollTop(Double.MAX_VALUE));
    }

    void confirm() {
        var row = list.getSelectionModel().getSelectedItem();
        if (row == null || row.tab == null) return;
        close(false);
        // after the popup has gone: closing it hands the focus back to its owner, and the chosen window must win
        Platform.runLater(() -> go(row.tab));
    }

    void close(boolean refocus) {
        if (stage == null) return;
        var s = stage;
        stage = null;
        previewSoon.stop();
        s.close();
        if (refocus && from != null) from.focusSoon();
    }

    /** Brings the tab's window forward and shows the tab there (a hibernated tab shows its saved text and waits for Enter). */
    void go(TerminalTab t) {
        var w = t.owner;
        if (w == null) return;
        if (w.stage.isIconified()) w.stage.setIconified(false);
        w.stage.toFront();
        w.stage.requestFocus();
        app.lastFocused = w;
        if (w.selected() == t) w.show(t);
        else w.list.getSelectionModel().select(t);
        w.focusSoon();
    }
}
