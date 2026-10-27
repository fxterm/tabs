package tabbyfx;

import module java.base;
import javafx.scene.control.Label;
import javafx.scene.layout.*;

/** The status line: the selected tab's RAM and folder on the left; all tabs together, tabbyfx itself and how many
 *  tabs are live or asleep on the right. */
public final class Footer extends HBox {

    final Label left = new Label(), right = new Label();

    Footer(TabbyTheme theme) {
        var gap = new Region();
        HBox.setHgrow(gap, Priority.ALWAYS);
        getChildren().addAll(left, gap, right);
        setStyle(Ui.footer(theme));
        left.setStyle(Ui.footerText(theme));
        right.setStyle(Ui.footerText(theme));
    }

    void update(TerminalTab selected, List<TerminalTab> tabs, long selfBytes) {
        left.setText(selected == null ? "" : (selected.live() ? Mem.mb(selected.ramBytes) : "asleep") + "   " + selected.where());
        var live = tabs.stream().filter(TerminalTab::live).count();
        var total = tabs.stream().mapToLong(t -> t.live() ? t.ramBytes : 0).sum();
        right.setText("all tabs " + Mem.mb(total) + "   ·   tabbyfx " + Mem.mb(selfBytes) + "   ·   " + live + " live, "
                + (tabs.size() - live) + " asleep");
    }
}
