package fxterm.tabs;

/** One line of the switcher's list. `number` is a tab's number (the key that reaches it) or, on a window line, the window's. */
final class SwitcherRow {

    enum Kind { SECTION, WINDOW, GROUP, TAB }

    final Kind kind;
    final String text, colour;
    final TerminalTab tab;
    final int number;

    SwitcherRow(Kind kind, String text, String colour, TerminalTab tab, int number) {
        this.kind = kind;
        this.text = text;
        this.colour = colour;
        this.tab = tab;
        this.number = number;
    }
}
