package fxterm.tabs;

import module java.base;
import fxterm.tabs.SwitcherRow.Kind;

/** The switcher's list: the awake tabs by window and group, then the hibernated ones the same way; only what fits every typed word. */
final class SwitcherRows {

    static List<SwitcherRow> build(TabsApp app, List<String> tokens) {
        var numbers = new HashMap<TerminalTab, Integer>();
        var n = 1;
        for (var w : app.windows) for (var t : w.realTabs()) numbers.put(t, n++);
        var out = new ArrayList<SwitcherRow>();
        for (var live : new boolean[] {true, false}) {
            var section = new ArrayList<SwitcherRow>();
            for (var wi = 0; wi < app.windows.size(); wi++) {
                var w = app.windows.get(wi);
                var inWindow = window(w, live, tokens, numbers);
                if (inWindow.isEmpty()) continue;
                section.add(new SwitcherRow(Kind.WINDOW, w.windowLabel(), w.colour, null, wi + 1));
                section.addAll(inWindow);
            }
            if (section.isEmpty()) continue;
            out.add(new SwitcherRow(Kind.SECTION, live ? "ACTIVE TABS" : "HIBERNATED TABS", null, null, 0));
            out.addAll(section);
        }
        return out;
    }

    private static List<SwitcherRow> window(TabsWindow w, boolean live, List<String> tokens, Map<TerminalTab, Integer> numbers) {
        var rows = new ArrayList<SwitcherRow>();
        var head = (SwitcherRow) null;
        var group = new ArrayList<SwitcherRow>();
        var headName = "";
        for (var t : w.tabs) {
            if (t.separator) {
                flush(rows, head, group);
                headName = t.shown();
                head = new SwitcherRow(Kind.GROUP, t.shown(), t.color.get(), null, 0);
                group = new ArrayList<>();
            } else if (t.live() == live && numbers.containsKey(t) && matches(tokens, numbers.get(t), t, headName, w)) {
                group.add(new SwitcherRow(Kind.TAB, TabCell.plain(t.shown()), w.colourOf(t), t, numbers.get(t)));
            }
        }
        flush(rows, head, group);
        return rows;
    }

    private static void flush(List<SwitcherRow> into, SwitcherRow head, List<SwitcherRow> group) {
        if (group.isEmpty()) return;
        if (head != null) into.add(head);
        into.addAll(group);
    }

    /** A number fits a tab whose number starts with it; any other word fits if it is in the tab's name or folder, or in the
     *  name of its group or window. */
    static boolean matches(List<String> tokens, int number, TerminalTab t, String group, TabsWindow w) {
        var hay = (TabCell.plain(t.shown()) + " " + t.where() + " " + group + " " + w.windowLabel()).toLowerCase(Locale.ROOT);
        return tokens.stream().allMatch(token -> hay.contains(token) || isNumber(token) && String.valueOf(number).startsWith(token));
    }

    static boolean isNumber(String s) {
        return s.chars().allMatch(Character::isDigit);
    }

    private SwitcherRows() { }
}
