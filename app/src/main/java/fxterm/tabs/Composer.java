package fxterm.tabs;

import module java.base;
import javafx.geometry.*;
import javafx.scene.control.*;
import javafx.scene.input.*;
import javafx.scene.layout.*;
import prosemirrorfx.view.embed.MarkdownEditor;
import prosemirrorfx.writer.Writer;

/** The editor under the terminal: prosemirrorfx's Markdown editor in the terminal's own colours, with its formatting
 *  toolbar and writer bar, so a reply can be typed, formatted, dictated (F3) and spell-checked before it goes to the
 *  shown tab as one paste. (Reading aloud is for the terminal's selection, in the selection bar, not here.)
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

    /** The editor's colours taken from the terminal's, so the box is the terminal's own dark (or light) and not the
     *  editor's stock white: page, toolbar, hairlines, text and the accents. */
    static prosemirrorfx.view.Theme editorTheme(TabbyTheme t) {
        var bg = t.background;
        var muted = Ui.mix(t.foreground, bg, 0.45);
        var tokens = new LinkedHashMap<String, String>();
        tokens.put("bg", bg);
        tokens.put("panel", Ui.shade(bg, 6));
        tokens.put("text", t.foreground);
        tokens.put("muted", muted);
        tokens.put("interim", muted);
        tokens.put("code", t.colors.get(5));
        tokens.put("link", t.colors.get(4));
        tokens.put("line", Ui.shade(bg, 22));
        tokens.put("border", Ui.shade(bg, 34));
        tokens.put("active-line", Ui.shade(bg, 5));
        tokens.put("hover", Ui.shade(bg, 22));
        tokens.put("handle", muted);
        tokens.put("select", Ui.shade(bg, 30));
        tokens.put("select-focus", t.colors.get(4));
        tokens.put("caret", t.foreground);
        tokens.put("number", muted);
        tokens.put("code-bg", Ui.shade(bg, 8));
        tokens.put("code-border", Ui.shade(bg, 28));
        var theme = prosemirrorfx.view.Theme.DARK;
        for (var e : tokens.entrySet()) theme = theme.with(e.getKey(), e.getValue());
        return theme;
    }

    /** What the editor's toolbar and the writer's bar need, as JavaFX's own buttons would not look right on the
     *  terminal's colours: flat, in the theme's text, a shade lighter under the mouse. */
    String barCss() {
        var fg = theme.foreground;
        var css = ".pm-toolbar, .wr-bar { -fx-background-color: " + Ui.shade(theme.background, 6) + "; -fx-text-base-color: " + fg + "; }\n"
                + ".pm-toolbar .button, .pm-toolbar .toggle-button, .wr-bar .button { -fx-background-color: transparent; -fx-text-fill: " + fg
                + "; -fx-font-size: 11; -fx-padding: 2 6 2 6; -fx-background-radius: 4; -fx-cursor: hand; }\n"
                + ".pm-toolbar .button:hover, .pm-toolbar .toggle-button:hover, .wr-bar .button:hover { -fx-background-color: " + Ui.shade(theme.background, 26) + "; }\n"
                + ".pm-toolbar .toggle-button:selected { -fx-background-color: " + Ui.shade(theme.background, 38) + "; }\n"
                + ".pm-toolbar .button:disabled, .wr-bar .button:disabled { -fx-opacity: 0.35; }\n"
                + ".pm-toolbar .pm-icon, .wr-bar .pm-icon { -fx-fill: " + fg + "; }\n"
                + ".pm-toolbar .label, .wr-bar .label { -fx-text-fill: " + fg + "; }\n"
                + ".pm-toolbar .separator .line, .wr-bar .separator .line { -fx-border-color: " + Ui.shade(theme.background, 34) + "; }\n"
                + ".pm-toolbar .split-menu-button, .pm-toolbar .menu-button { -fx-background-color: transparent; -fx-padding: 0; }\n"
                + ".pm-toolbar .split-menu-button > .label, .pm-toolbar .menu-button > .label { -fx-text-fill: " + fg + "; }\n"
                + ".pm-toolbar .split-menu-button > .action-button, .pm-toolbar .split-menu-button > .arrow-button, .pm-toolbar .menu-button > .arrow-button"
                + " { -fx-background-color: transparent; -fx-background-radius: 4; }\n"
                + ".pm-toolbar .split-menu-button > .action-button:hover, .pm-toolbar .split-menu-button > .arrow-button:hover, .pm-toolbar .menu-button:hover"
                + " { -fx-background-color: " + Ui.shade(theme.background, 26) + "; }\n"
                + ".pm-toolbar .split-menu-button > .arrow-button > .arrow, .pm-toolbar .menu-button > .arrow-button > .arrow, .pm-toolbar .combo-box .arrow"
                + " { -fx-background-color: " + fg + "; }\n"
                + ".pm-toolbar .combo-box, .pm-toolbar .choice-box { -fx-background-color: " + Ui.shade(theme.background, 14) + "; }\n"
                + ".pm-toolbar .combo-box .list-cell, .pm-toolbar .choice-box .label { -fx-text-fill: " + fg + "; }\n";
        return "data:text/css;base64," + Base64.getEncoder().encodeToString(css.getBytes(StandardCharsets.UTF_8));
    }

    /** Builds the editor on first use. */
    void ensure() {
        if (md != null) return;
        md = new MarkdownEditor();
        md.view().setTheme(editorTheme(theme));
        writer = new Writer(md);
        var editor = md.node();
        if (editor instanceof Region r) {
            r.setPrefHeight(170);
            r.setMinHeight(90);
        }
        // bold, italic, headings, lists and the rest of an editor's tools; the writer's own bar carries dictation and
        // spelling. Reading aloud is not here: it belongs to the terminal's selection, not to what is being typed.
        var tools = md.toolbar();
        var writerBar = (Pane) writer.bar();
        var separators = new ArrayList<Integer>();
        for (var i = 0; i < writerBar.getChildren().size(); i++) if (writerBar.getChildren().get(i) instanceof Separator) separators.add(i);
        if (separators.size() >= 2) writerBar.getChildren().subList(separators.get(0), separators.get(1)).clear();
        getStylesheets().add(barCss());
        var send = Footer.small(Os.keys("Send + Enter  Ctrl+Enter"), "Type it into the tab and press Enter", () -> send(true));
        Footer.tint(send, theme.colors.get(2));
        var type = Footer.small("Type only", "Type it into the tab without pressing Enter", () -> send(false));
        var close = Footer.small("Close  Esc", "Close the editor (the text stays for next time)", app::toggleCompose);
        var outside = Footer.small("Open in notepadfx", "Write this to a reply file (beside the tab's PRP, or a temp file) and open it in notepadfx",
                () -> { var t = app.selected(); if (t != null) editOutside(t, getText()); });
        hint.setStyle("-fx-text-fill: " + theme.foreground + "; -fx-opacity: 0.7; -fx-font-size: 11;");
        var gap = new Region();
        HBox.setHgrow(gap, Priority.ALWAYS);
        var bar = new HBox(6, hint, gap, outside, type, send, close);
        bar.setAlignment(Pos.CENTER_RIGHT);
        getChildren().setAll(tools, writerBar, editor, bar);
        // the writer's keys (as its bar shows them) and the box's own
        addEventFilter(KeyEvent.KEY_PRESSED, e -> {
            var plain = !e.isControlDown() && !e.isMetaDown() && !e.isAltDown() && !e.isShiftDown();
            if (e.getCode() == KeyCode.ENTER && (e.isControlDown() || e.isMetaDown())) send(true);
            else if (e.getCode() == KeyCode.F3 && plain) writer.dictate();
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
            hint.setText(Os.keys("Opened " + file.getFileName() + ". Save it there, then Ctrl+Enter sends @path to the tab."));
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
