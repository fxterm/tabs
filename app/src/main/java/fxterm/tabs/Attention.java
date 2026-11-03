package fxterm.tabs;

import module java.base;
import javafx.application.Platform;

/** A tab asking for the user (its program ran `ctl attention on`, or its title starts with a bell): the moment it starts
 *  to ask, Tabs rings once and, unless the user is already looking at that tab, asks whether to switch to it
 *  (AttentionPopup: Yes, No or Esc, and after 20 seconds it switches by itself). One popup at a time; the others wait. */
final class Attention {


    final TabsApp app;
    final Deque<TerminalTab> waiting = new ArrayDeque<>();
    AttentionPopup shown;

    Attention(TabsApp app) {
        this.app = app;
    }

    /** Called when a tab's title or asking flag may have changed: acts only when it started or stopped asking. */
    void update(TerminalTab t) {
        var now = t.attention();
        if (now == t.wasAttention) return;
        t.wasAttention = now;
        if (!now) {
            dismiss(t);
            return;
        }
        var looking = t.owner != null && t.owner.stage.isFocused() && t.owner.selected() == t;
        if (looking) return;
        ring();
        waiting.add(t);
        next();
    }

    void dismiss(TerminalTab t) {
        waiting.remove(t);
        if (shown != null && shown.tab == t) shown.close();
    }

    /** The popup for the next waiting tab, once none is showing. */
    void next() {
        if (shown != null) return;
        var t = waiting.poll();
        while (t != null && (!t.attention() || t.owner == null)) t = waiting.poll();
        if (t == null) return;
        shown = new AttentionPopup(this, t);
        shown.show();
    }

    void closed(AttentionPopup p) {
        if (shown == p) shown = null;
        Platform.runLater(this::next);
    }

    /** Once, quietly (see BellSound). */
    static void ring() {
        BellSound.play();
    }
}
