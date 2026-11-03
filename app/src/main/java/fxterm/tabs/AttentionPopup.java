package fxterm.tabs;

import module java.base;
import javafx.animation.*;
import javafx.application.Platform;
import javafx.geometry.*;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.input.*;
import javafx.scene.layout.*;
import javafx.stage.*;
import javafx.util.Duration;

/** "A tab needs you": which tab, and Yes (F2) or No (Esc). With no answer in 20 seconds it switches to that tab by
 *  itself. Keys and buttons are ignored for the first moments, so what the user was typing cannot answer it. */
final class AttentionPopup {

    static final int SECONDS = 20;
    static final long ARM_MS = 1500;

    final Attention attention;
    final TerminalTab tab;
    final Label countdown = new Label();
    final Button yes = new Button("Yes   F2"), no = new Button("No   Esc");
    Stage stage;
    Timeline tick;
    boolean armed;
    int left = SECONDS;

    AttentionPopup(Attention attention, TerminalTab tab) {
        this.attention = attention;
        this.tab = tab;
    }

    void show() {
        var app = attention.app;
        var theme = app.theme;
        var fg = theme.foreground;
        var dim = Ui.mix(fg, theme.background, 0.4);
        var where = tab.owner;
        var title = new Label(TabCell.rowName(tab.shown()));
        title.setStyle("-fx-text-fill: " + fg + "; -fx-font-size: 15; -fx-font-weight: bold;");
        var sub = new Label(where.windowLabel() + "   ·   " + tab.where());
        sub.setStyle("-fx-text-fill: " + dim + "; -fx-font-size: 11.5;");
        title.setMaxWidth(520);
        sub.setMaxWidth(520);
        var heading = new Label("needs you");
        heading.setStyle("-fx-text-fill: " + BellMark.AMBER + "; -fx-font-size: 12; -fx-font-weight: bold;");
        var bell = new BellMark();
        bell.show(true);
        var head = new HBox(12, bell, new VBox(1, heading, title, sub));
        head.setAlignment(Pos.CENTER_LEFT);
        countdown.setStyle("-fx-text-fill: " + dim + "; -fx-font-size: 11.5;");
        for (var b : List.of(yes, no)) {
            b.setFocusTraversable(false);
            b.setDisable(true);
            b.getStyleClass().add("tabs-button");
        }
        yes.setOnAction(e -> answer(true));
        no.setOnAction(e -> answer(false));
        var spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        var buttons = new HBox(8, countdown, spacer, yes, no);
        buttons.setAlignment(Pos.CENTER_LEFT);
        var root = new VBox(14, head, new Label("Go to that tab?"), buttons);
        root.getChildren().get(1).setStyle("-fx-text-fill: " + fg + "; -fx-font-size: 13;");
        root.setPadding(new Insets(16, 18, 14, 18));
        root.setMinWidth(420);
        root.setStyle("-fx-background-color: " + Ui.shade(theme.background, -4) + "; -fx-border-color: " + BellMark.AMBER
                + "; -fx-border-width: 1.5; -fx-background-radius: 8; -fx-border-radius: 8;");
        var scene = new Scene(root);
        scene.getStylesheets().add(Ui.stylesheet(theme));
        scene.getPreferences().setColorScheme(theme.dark() ? javafx.application.ColorScheme.DARK : javafx.application.ColorScheme.LIGHT);
        scene.addEventFilter(KeyEvent.KEY_PRESSED, this::keys);
        stage = new Stage(StageStyle.UNDECORATED);
        if (!where.stage.isIconified()) stage.initOwner(where.stage);
        stage.setAlwaysOnTop(true);
        stage.setScene(scene);
        stage.setOnShown(e -> place());
        stage.show();
        stage.toFront();
        var arming = new PauseTransition(Duration.millis(ARM_MS));
        arming.setOnFinished(e -> { armed = true; yes.setDisable(false); no.setDisable(false); });
        arming.play();
        tick = new Timeline(new KeyFrame(Duration.seconds(1), e -> second()));
        tick.setCycleCount(SECONDS);
        tick.play();
        text();
    }

    /** Top centre of the window the tab is in (or of the screen, when that window is minimised). */
    void place() {
        var o = tab.owner.stage;
        var screen = Screen.getPrimary().getVisualBounds();
        var inWindow = !o.isIconified();
        stage.setX(inWindow ? o.getX() + (o.getWidth() - stage.getWidth()) / 2 : screen.getMinX() + (screen.getWidth() - stage.getWidth()) / 2);
        stage.setY(inWindow ? o.getY() + 70 : screen.getMinY() + 90);
    }

    void keys(KeyEvent e) {
        if (!armed) { e.consume(); return; }
        if (e.getCode() == KeyCode.F2) answer(true);
        else if (e.getCode() == KeyCode.ESCAPE) answer(false);
        e.consume();
    }

    void second() {
        left--;
        text();
        if (left <= 0) answer(true);
    }

    void text() {
        countdown.setText("Switching by itself in " + left + " s");
    }

    /** Yes, or the 20 seconds ran out: that window comes forward showing that tab. No: nothing moves. */
    void answer(boolean go) {
        close();
        if (go && tab.attention()) Platform.runLater(() -> attention.app.switcher.go(tab));
    }

    void close() {
        if (tick != null) tick.stop();
        if (stage != null) {
            var s = stage;
            stage = null;
            s.close();
            attention.closed(this);
        }
    }
}
