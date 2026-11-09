package fxterm.tabs;

import module java.base;

/** Where the tab of a launch goes (PRP 24). A group is a label and the tabs under it. The tab goes to the END of its group, so
 *  tabs launched one after another stay in launch order. Which group: the one named, else the one of the tab the launch came from;
 *  with neither (or an id no tab has any more, or a tab in no group) the end of the window, as before. A group that is not there
 *  is made as a label at the end of the window. A folded group opens, so the new tab is in sight. */
final class Placement {

    /** Adds `t` and returns the window it landed in; the caller selects it. `fallback` is where it goes when nothing says otherwise.
     *  `pinned`: the window was named by the caller, so only it is looked at. */
    static TabsWindow add(TabsApp app, TerminalTab t, String group, String from, TabsWindow fallback, boolean pinned) {
        var starter = from == null || pinned ? null : app.allTabs().stream().filter(x -> !x.separator && x.id.equals(from)).findFirst().orElse(null);
        var label = group == null || group.isBlank() ? null : group.strip();
        var w = fallback;
        var head = (TerminalTab) null;
        if (label != null) {
            var l = label;
            w = pinned ? fallback
                : starter != null && head(starter.owner, l) != null ? starter.owner
                : app.windows.stream().filter(x -> head(x, l) != null).findFirst().orElse(starter != null ? starter.owner : fallback);
            head = head(w, label);
            if (head == null) {
                head = TerminalTab.separator(app.options, app.theme, label);
                head.owner = w;
                w.tabs.add(head);
            }
        } else if (starter != null && starter.owner.headOf(starter) != null) {
            w = starter.owner;
            head = w.headOf(starter);
        }
        w.tabs.add(head == null ? w.tabs.size() : w.tabs.indexOf(head) + w.group(head).size(), t);
        t.owner = w;
        Events.log("launch-into", "tab", Events.tab(t), "group", head == null ? null : head.shown(), "from", from);
        if (head != null && head.collapsed) w.toggleGroup(head);
        w.refresh();
        return w;
    }

    /** The label row of that name in the window, or null. */
    static TerminalTab head(TabsWindow w, String label) {
        return w.tabs.stream().filter(x -> x.separator && x.shown().equals(label)).findFirst().orElse(null);
    }

    private Placement() { }
}
