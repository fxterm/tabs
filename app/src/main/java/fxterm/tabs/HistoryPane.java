package fxterm.tabs;

import module java.base;
import javafx.application.Platform;
import javafx.geometry.*;
import javafx.scene.control.*;
import javafx.scene.input.*;
import javafx.scene.layout.*;

/** The History view, shown in the window's main area in place of a terminal: every saved session, newest first,
 *  searchable across titles, folders and all saved text, filtered by date and kind, with a preview of the text.
 *  Enter resumes the chosen entry in a new tab; Esc goes back to the tab. */
public final class HistoryPane extends VBox {

    static final DateTimeFormatter WHEN = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm").withZone(ZoneId.systemDefault());

    final TabsWindow win;
    final History history;
    final TextField search = new TextField();
    final ChoiceBox<String> range = new ChoiceBox<>(javafx.collections.FXCollections.observableArrayList(
            "Today", "Last 7 days", "Last 30 days", "All", "From - to"));
    final DatePicker from = new DatePicker(), to = new DatePicker();
    final ChoiceBox<String> kind = new ChoiceBox<>(javafx.collections.FXCollections.observableArrayList("All kinds", "Claude", "Shells"));
    final ListView<History.Entry> results = new ListView<>();
    final TextArea preview = new TextArea();
    final Label count = new Label();
    final Button resume, copyText, copyCommand, clear;
    final Map<String, String> textCache = new HashMap<>();
    List<History.Entry> all = List.of();
    long searchGeneration;

    HistoryPane(TabsWindow win, History history) {
        this.win = win;
        this.history = history;
        var theme = win.theme;
        var fg = "-fx-text-fill: " + theme.foreground + ";";
        setStyle("-fx-background-color: " + theme.background + ";");
        setSpacing(8);
        setPadding(new Insets(10, 12, 10, 12));

        var heading = new Label("History");
        heading.setStyle(fg + " -fx-font-size: 16; -fx-font-weight: bold;");
        search.setPromptText("Search titles, folders and all saved text");
        HBox.setHgrow(search, Priority.ALWAYS);
        range.getSelectionModel().select("All");
        kind.getSelectionModel().select("All kinds");
        from.setPrefWidth(130);
        to.setPrefWidth(130);
        from.setPromptText("from");
        to.setPromptText("to");
        var dates = new HBox(4, from, to);
        dates.visibleProperty().bind(range.valueProperty().isEqualTo("From - to"));
        dates.managedProperty().bind(dates.visibleProperty());
        clear = Footer.button("Clear history", "#8a3a3a", "Delete saved history (asks first)", this::clear);
        var back = Footer.button("Back  Esc", "#4a4a4a", "Back to the tab", win::hideHistory);
        count.setStyle(fg + " -fx-opacity: 0.7; -fx-font-size: 11.5;");
        var top = new HBox(8, heading, search, range, dates, kind, clear, back);
        top.setAlignment(Pos.CENTER_LEFT);

        results.setCellFactory(v -> new EntryCell(theme));
        results.setStyle("-fx-background-color: " + Ui.shade(theme.background, -10) + "; -fx-control-inner-background: "
                + Ui.shade(theme.background, -10) + ";");
        results.setPrefWidth(420);
        results.getSelectionModel().selectedItemProperty().addListener((ov, was, now) -> showPreview(now));
        preview.setEditable(false);
        preview.setWrapText(false);
        preview.setStyle("-fx-control-inner-background: " + Ui.shade(theme.background, 4) + "; -fx-text-fill: " + theme.foreground + ";"
                + " -fx-font-family: '" + win.options.family + "'; -fx-font-size: 13; -fx-focus-color: transparent; -fx-faint-focus-color: transparent;");
        resume = Footer.button("Resume  Enter", theme.colors.get(4), "Open it again in a new tab: claude --resume with its flags, or its shell, in its folder", this::resume);
        copyText = Footer.button("Copy text", "#4a4a4a", "Copy the saved text", () -> { var e = chosen(); if (e != null) win.copy(text(e)); });
        copyCommand = Footer.button("Copy command", "#4a4a4a", "Copy the line that resumes it in any cmd window",
                () -> { var e = chosen(); if (e != null) win.copy(e.asSaved().resumeLine()); });
        var actions = new HBox(8, count, new Region(), copyText, copyCommand, resume);
        HBox.setHgrow(actions.getChildren().get(1), Priority.ALWAYS);
        actions.setAlignment(Pos.CENTER_LEFT);
        var right = new VBox(6, preview, actions);
        VBox.setVgrow(preview, Priority.ALWAYS);
        var split = new SplitPane(results, right);
        split.setDividerPositions(0.38);
        VBox.setVgrow(split, Priority.ALWAYS);
        getChildren().addAll(top, split);

        search.textProperty().addListener((ov, was, now) -> refilterSoon());
        range.valueProperty().addListener((ov, was, now) -> refilterSoon());
        kind.valueProperty().addListener((ov, was, now) -> refilterSoon());
        from.valueProperty().addListener((ov, was, now) -> refilterSoon());
        to.valueProperty().addListener((ov, was, now) -> refilterSoon());
        addEventFilter(KeyEvent.KEY_PRESSED, e -> {
            if (e.getCode() == KeyCode.ESCAPE) { win.hideHistory(); e.consume(); }
            else if (e.getCode() == KeyCode.ENTER && !(e.getTarget() instanceof TextArea)) { resume(); e.consume(); }
            else if (e.getCode() == KeyCode.DOWN && e.getTarget() == search) { results.requestFocus(); results.getSelectionModel().selectFirst(); e.consume(); }
        });
    }

    /** Reads the history again (it grows while the app runs) and puts the cursor in the search box. */
    void open() {
        textCache.clear();
        all = history.load();
        refilterSoon();
        Platform.runLater(search::requestFocus);
    }

    History.Entry chosen() {
        return results.getSelectionModel().getSelectedItem();
    }

    String text(History.Entry e) {
        return textCache.computeIfAbsent(e.id, id -> history.text(e));
    }

    /** Filters off the FX thread (the search reads saved text), keeping only the newest request's answer. */
    void refilterSoon() {
        var generation = ++searchGeneration;
        var query = search.getText().strip().toLowerCase(Locale.ROOT);
        var cutoffFrom = rangeStart();
        var cutoffTo = rangeEnd();
        var which = kind.getValue();
        var source = all;
        Thread.ofPlatform().daemon().start(() -> {
            var found = source.stream()
                    .filter(e -> e.at >= cutoffFrom && e.at < cutoffTo)
                    .filter(e -> "Claude".equals(which) ? e.claude() : !"Shells".equals(which) || !e.claude())
                    .filter(e -> query.isEmpty() || matches(e, query))
                    .toList();
            Platform.runLater(() -> {
                if (generation != searchGeneration) return;
                results.getItems().setAll(found);
                count.setText(found.size() + " of " + source.size() + " saved sessions   ·   " + Mem.mb(history.bytes()) + " on disk");
                clear.setText("Clear history (" + Mem.mb(history.bytes()) + ")");
                if (!found.isEmpty()) results.getSelectionModel().selectFirst();
                else preview.clear();
            });
        });
    }

    boolean matches(History.Entry e, String query) {
        return contains(e.shown(), query) || contains(e.cwd, query) || contains(e.claudeSessionId, query) || contains(text(e), query);
    }

    static boolean contains(String s, String query) {
        return s != null && s.toLowerCase(Locale.ROOT).contains(query);
    }

    long rangeStart() {
        var today = LocalDate.now().atStartOfDay(ZoneId.systemDefault());
        return switch (range.getValue()) {
            case "Today" -> today.toInstant().toEpochMilli();
            case "Last 7 days" -> today.minusDays(6).toInstant().toEpochMilli();
            case "Last 30 days" -> today.minusDays(29).toInstant().toEpochMilli();
            case "From - to" -> from.getValue() == null ? 0 : from.getValue().atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli();
            default -> 0;
        };
    }

    long rangeEnd() {
        if ("From - to".equals(range.getValue()) && to.getValue() != null)
            return to.getValue().plusDays(1).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli();
        return Long.MAX_VALUE;
    }

    /** The saved text, scrolled to the first match of the search (or to its end, which is what the screen showed). */
    void showPreview(History.Entry e) {
        if (e == null) {
            preview.clear();
            return;
        }
        var text = text(e);
        preview.setText(text);
        var query = search.getText().strip().toLowerCase(Locale.ROOT);
        var at = query.isEmpty() ? -1 : text.toLowerCase(Locale.ROOT).indexOf(query);
        if (at >= 0) {
            preview.selectRange(at, at + query.length());
        } else {
            preview.positionCaret(text.length());
            Platform.runLater(() -> preview.setScrollTop(Double.MAX_VALUE));
        }
    }

    void resume() {
        var e = chosen();
        if (e == null) return;
        win.resumeFromHistory(e.asSaved());
    }

    void clear() {
        var choice = new ChoiceDialog<>("Older than 30 days", "Older than 30 days", "Everything");
        choice.initOwner(win.stage);
        choice.setTitle("Clear history");
        choice.setHeaderText("Delete saved history (" + Mem.mb(history.bytes()) + " on disk)?");
        choice.showAndWait().ifPresent(c -> {
            var cutoff = c.equals("Everything") ? 0 : System.currentTimeMillis() - 30L * 24 * 3600 * 1000;
            history.clear(cutoff);
            open();
        });
    }

    /** One entry: when, its name, its folder, claude or shell. */
    static final class EntryCell extends ListCell<History.Entry> {
        final Label name = new Label(), sub = new Label();
        final VBox box = new VBox(1, name, sub);

        EntryCell(TabbyTheme theme) {
            name.setStyle("-fx-text-fill: " + theme.foreground + "; -fx-font-size: 13.5; -fx-font-weight: bold;");
            sub.setStyle("-fx-text-fill: " + theme.foreground + "; -fx-opacity: 0.6; -fx-font-size: 11;");
            setPrefWidth(0);
        }

        @Override
        protected void updateItem(History.Entry e, boolean empty) {
            super.updateItem(e, empty);
            setText(null);
            if (empty || e == null) {
                setGraphic(null);
                return;
            }
            name.setText(e.shown());
            sub.setText(WHEN.format(Instant.ofEpochMilli(e.at)) + "  ·  " + (e.claude() ? "claude" : e.shell != null ? e.shell : "shell")
                    + "  ·  " + e.event + "  ·  " + e.lines + " lines  ·  " + (e.cwd == null ? "" : e.cwd));
            setGraphic(box);
        }
    }
}
