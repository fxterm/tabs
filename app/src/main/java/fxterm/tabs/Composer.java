package fxterm.tabs;

import javafx.geometry.*;
import javafx.scene.control.*;
import javafx.scene.input.*;
import javafx.scene.layout.*;

/** A small editor under the terminal for writing what a terminal makes awkward (select, cut, copy, paste, Ctrl+F,
 *  Ctrl+H, moving around), then sending it to the shown tab as one paste. Ctrl+Enter sends and presses Enter,
 *  the second button sends without Enter, Esc closes. */
public final class Composer extends VBox {

    final TextArea text = new TextArea();
    final TabsApp app;

    Composer(TabsApp app, TabbyTheme theme) {
        this.app = app;
        text.setPrefRowCount(6);
        text.setWrapText(true);
        text.setPromptText("Write here, then Ctrl+Enter to send it to the tab (Esc closes)");
        text.setStyle("-fx-control-inner-background: " + Ui.shade(theme.background, 8) + "; -fx-text-fill: " + theme.foreground + ";"
                + " -fx-font-family: 'Consolas'; -fx-font-size: 14; -fx-focus-color: transparent; -fx-faint-focus-color: transparent;");
        var send = Footer.button("Send + Enter  Ctrl+Enter", theme.colors.get(2), "Type it into the tab and press Enter", () -> send(true));
        var type = Footer.button("Type only", "#4a4a4a", "Type it into the tab without pressing Enter", () -> send(false));
        var close = Footer.button("Close  Esc", "#4a4a4a", "Close the editor (the text stays for next time)", app::toggleCompose);
        var gap = new Region();
        HBox.setHgrow(gap, Priority.ALWAYS);
        var bar = new HBox(8, new Label(), gap, type, send, close);
        bar.setAlignment(Pos.CENTER_RIGHT);
        setSpacing(6);
        setPadding(new Insets(6, 8, 6, 8));
        setStyle("-fx-background-color: " + Ui.shade(theme.background, -10) + ";");
        getChildren().addAll(text, bar);
        text.addEventFilter(KeyEvent.KEY_PRESSED, e -> {
            if (e.getCode() == KeyCode.ENTER && e.isControlDown()) { send(true); e.consume(); }
            else if (e.getCode() == KeyCode.ESCAPE) { app.toggleCompose(); e.consume(); }
        });
    }

    /** Pastes the text into the shown tab (whole, even across lines), optionally presses Enter, clears, closes. */
    void send(boolean enter) {
        var t = app.selected();
        if (t == null || !t.live() || text.getText().isEmpty()) return;
        var panel = t.widget.getTerminalPanel();
        panel.pasteText(text.getText());
        // the same queue as the paste, so Enter cannot overtake the text
        if (enter) panel.sendText("\r");
        text.clear();
        app.toggleCompose();
    }
}
