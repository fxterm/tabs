package fxterm.tabs;

import module java.base;

/** Colours and font size, read from Tabby's own config so both terminals look the same. Reads only the active
 *  `terminal.colorScheme` block and `terminal.fontSize`; anything missing keeps the built-in dark palette. */
public final class TabbyTheme {

    static final Path CONFIG = Os.tabbyConfig();

    String name = "fxterm-tabs dark", foreground = "#e8eaf2", background = "#0f1324", cursor = "#e8eaf2";
    List<String> colors = new ArrayList<>(List.of(
            "#000000", "#cd3131", "#0dbc79", "#e5e510", "#2472c8", "#bc3fbc", "#11a8cd", "#e5e5e5",
            "#666666", "#f14c4c", "#23d18b", "#f5f543", "#3b8eea", "#d670d6", "#29b8db", "#ffffff"));
    double fontSize = 13.5;

    /** VS Code's own terminal in its Dark Modern theme (what a new VS Code install shows): the panel colour behind the
     *  text, #cccccc text, and the standard ANSI palette (the built-in colours here are that same palette). */
    static TabbyTheme vscode() {
        var t = new TabbyTheme();
        t.name = "VS Code Dark Modern";
        t.foreground = "#cccccc";
        t.background = "#181818";
        t.cursor = "#ffffff";
        t.fontSize = 12;
        return t;
    }

    static TabbyTheme load(String which) {
        if ("vscode".equals(which)) return vscode();
        var t = new TabbyTheme();
        if (!"tabby".equals(which) || !Files.isReadable(CONFIG)) return t;
        try {
            t.read(Files.readAllLines(CONFIG));
        } catch (IOException | RuntimeException e) {
            System.err.println("fxterm-tabs: could not read Tabby's theme, using the built-in one: " + e);
        }
        return t;
    }

    /** Line by line rather than a YAML library: the block is small, flat and always written by Tabby itself. */
    void read(List<String> lines) {
        var inTerminal = false;
        var inScheme = false;
        var inColors = false;
        var scheme = new ArrayList<String>();
        for (var raw : lines) {
            var indent = raw.length() - raw.stripLeading().length();
            var line = raw.strip();
            if (indent == 0) inTerminal = line.equals("terminal:");
            if (!inTerminal || line.isEmpty()) continue;
            if (indent == 2) {
                inScheme = line.equals("colorScheme:");
                inColors = false;
                if (line.startsWith("fontSize:")) fontSize = Double.parseDouble(value(line));
                continue;
            }
            if (!inScheme) continue;
            if (indent == 4) {
                inColors = line.equals("colors:");
                switch (line.substring(0, Math.max(0, line.indexOf(':')))) {
                    case "name" -> name = value(line);
                    case "foreground" -> foreground = value(line);
                    case "background" -> background = value(line);
                    case "cursor" -> cursor = value(line);
                    default -> { }
                }
            } else if (inColors && line.startsWith("- ")) {
                scheme.add(unquote(line.substring(2).strip()));
            }
        }
        if (scheme.size() == 16) colors = scheme;
    }

    static String value(String line) {
        return unquote(line.substring(line.indexOf(':') + 1).strip());
    }

    static String unquote(String s) {
        return s.replace("'", "").replace("\"", "");
    }

    /** A dark background (by perceived brightness), so the window chrome should be dark too. */
    boolean dark() {
        var c = rgb(background);
        return 0.299 * ((c >> 16) & 0xff) + 0.587 * ((c >> 8) & 0xff) + 0.114 * (c & 0xff) < 128;
    }

    static int rgb(String hex) {
        return Integer.parseInt(hex.replace("#", "").substring(0, 6), 16);
    }
}
