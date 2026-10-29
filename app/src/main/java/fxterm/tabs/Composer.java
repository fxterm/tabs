package fxterm.tabs;

import javafx.geometry.*;
import javafx.scene.control.*;
import javafx.scene.input.*;
import javafx.scene.layout.*;
import prosemirrorfx.view.embed.MarkdownEditor;
import prosemirrorfx.writer.Writer;

/** The editor under the terminal: prosemirrorfx's Markdown editor with its writer bar, so a reply can be typed,
 *  dictated (F3) or read aloud (F9, the selection or from the caret) before it goes to the shown tab as one paste.
 *  Ctrl+Enter sends and presses Enter, "Type only" sends without Enter, Esc stops reading or dictation, else closes.
 *  The editor is made the first time the box opens, so a Tabs that never uses it does not pay for it. */
public final class Composer extends VBox {

    final TabsWindow app;
    final TabbyTheme theme;
    MarkdownEditor md;
    Writer writer;
    final Label hint = new Label();

    Composer(TabsWindow app, TabbyTheme theme) {
        this.app = app;
        this.theme = theme;
        setSpacing(4);
        setPadding(new Insets(6, 8, 6, 8));
        setStyle("-fx-background-color: " + Ui.shade(theme.background, -10) + ";");
    }

    /** Builds the editor on first use. */
    void ensure() {
        if (md != null) return;
        md = new MarkdownEditor();
        writer = new Writer(md);
        var editor = md.node();
        if (editor instanceof Region r) {
            r.setPrefHeight(170);
            r.setMinHeight(90);
        }
        var send = Footer.button("Send + Enter  Ctrl+Enter", theme.colors.get(2), "Type it into the tab and press Enter", () -> send(true));
        var type = Footer.button("Type only", "#4a4a4a", "Type it into the tab without pressing Enter", () -> send(false));
        var close = Footer.button("Close  Esc", "#4a4a4a", "Close the editor (the text stays for next time)", app::toggleCompose);
        var outside = Footer.button("Open in notepadfx", "#4a4a4a", "Write this to a reply file (beside the tab's PRP, or a temp file) and open it in notepadfx",
                () -> { var t = app.selected(); if (t != null) editOutside(t, getText()); });
        hint.setStyle("-fx-text-fill: " + theme.foreground + "; -fx-opacity: 0.7; -fx-font-size: 11.5;");
        var gap = new Region();
        HBox.setHgrow(gap, Priority.ALWAYS);
        var bar = new HBox(8, hint, gap, outside, type, send, close);
        bar.setAlignment(Pos.CENTER_RIGHT);
        getChildren().setAll(writer.bar(), editor, bar);
        // the writer's keys (as its bar shows them) and the box's own
        addEventFilter(KeyEvent.KEY_PRESSED, e -> {
            var plain = !e.isControlDown() && !e.isAltDown() && !e.isShiftDown();
            if (e.getCode() == KeyCode.ENTER && e.isControlDown()) send(true);
            else if (e.getCode() == KeyCode.F3 && plain) writer.dictate();
            else if (e.getCode() == KeyCode.F9 && plain) writer.read();
            else if (e.getCode() == KeyCode.F7 && e.isShiftDown()) writer.nextIssue();
            else if (e.getCode() == KeyCode.ESCAPE) { if (!writer.stop()) app.toggleCompose(); }
            else return;
            e.consume();
        });
    }

    String getText() {
        ensure();
        return md.getMarkdown();
    }

    void setText(String text) {
        ensure();
        md.setMarkdown(text);
        md.setCaretSourceOffset(text.length());
    }

    /** Puts markdown in at the caret (a quote from the terminal), the caret after it. */
    void insert(String text) {
        ensure();
        var all = md.getMarkdown();
        var at = Math.clamp(md.caretSourceOffset(), 0, all.length());
        md.setMarkdown(all.substring(0, at) + text + all.substring(at));
        md.setCaretSourceOffset(at + text.length());
    }

    void focusEditor() {
        ensure();
        md.focus();
    }

    /** Writes the text to a reply file and opens it in notepadfx; the box then holds `@<path>`, so once the reply is
     *  written and saved there, Ctrl+Enter hands the file to claude (that send is the confirmation). */
    void editOutside(TerminalTab t, String body) {
        try {
            var file = Quote.writeAndEdit(t, body);
            if (!app.bottom.getChildren().contains(this)) app.toggleCompose();
            setText("@" + file + " ");
            hint.setText("Opened " + file.getFileName() + ". Save it there, then Ctrl+Enter sends @path to the tab.");
        } catch (java.io.IOException e) {
            hint.setText("Could not write the reply file: " + e.getMessage());
        }
    }

    /** Pastes the text into the shown tab (whole, even across lines), optionally presses Enter, clears, closes. */
    void send(boolean enter) {
        var t = app.selected();
        var text = getText().strip();
        if (t == null || !t.live() || text.isEmpty()) return;
        writer.stop();
        var panel = t.widget.getTerminalPanel();
        panel.pasteText(text);
        // the same queue as the paste, so Enter cannot overtake the text
        if (enter) panel.sendText("\r");
        md.setMarkdown("");
        hint.setText("");
        app.toggleCompose();
    }
}
