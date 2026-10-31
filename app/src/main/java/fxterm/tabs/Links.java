package fxterm.tabs;

import module java.base;
import com.techsenger.jeditermfx.core.model.hyperlinks.*;
import com.techsenger.jeditermfx.ui.hyperlinks.LinkInfoEx;
import javafx.geometry.Rectangle2D;
import javafx.scene.canvas.Canvas;

/** Finds what can be clicked in a line of terminal text: web links, and Windows paths (C:\..., \\server\share\...).
 *  A click opens a link in the browser, a file with its program, a folder in Explorer. Nothing is checked on disk
 *  while finding, since this runs for every line printed; a path that does not exist simply opens nothing. */
public final class Links implements HyperlinkFilter {

    static final Pattern URL = Pattern.compile("\\b(?:https?|ftp|file)://[-A-Za-z0-9+$&@#/%?=~_|!:,.;()\\[\\]*']*[-A-Za-z0-9+$&@#/%=~_|)]");
    /** A drive or UNC path up to a space, quote or bracket; claude prints paths with spaces in backticks, which end
     *  it too, so a path with spaces is found up to its first space only. */
    static final Pattern PATH = Pattern.compile("(?<![\\w/\\\\])(?:[A-Za-z]:\\\\|\\\\\\\\[\\w.$-]+\\\\)[^\\s\"'`<>|*?]*");

    final boolean hover;

    /** @param hover whether resting the mouse on a link offers to copy it */
    Links(boolean hover) {
        this.hover = hover;
    }

    Links() {
        this(false);
    }

    /** The link: a click opens it, and when hover is on, resting the mouse on it offers Copy. */
    LinkInfo info(String target) {
        if (!hover) return new LinkInfo(() -> open(target));
        return new LinkInfoEx.Builder().setNavigateCallback(() -> open(target)).setHoverConsumer(new LinkInfoEx.HoverConsumer() {
            @Override
            public void onMouseEntered(Canvas host, Rectangle2D bounds) {
                LinkPopup.entered(target, host, bounds);
            }

            @Override
            public void onMouseExited() {
                LinkPopup.exited();
            }
        }).build();
    }

    @Override
    public LinkResult apply(String line) {
        var items = new ArrayList<LinkResultItem>();
        var m = URL.matcher(line);
        while (m.find()) {
            var url = trimEnd(m.group());
            items.add(new LinkResultItem(m.start(), m.start() + url.length(), info(url)));
        }
        if (line.contains(":\\") || line.contains("\\\\")) {
            var p = PATH.matcher(line);
            while (p.find()) {
                var path = trimEnd(p.group());
                if (path.length() < 4 || overlaps(items, p.start())) continue;
                items.add(new LinkResultItem(p.start(), p.start() + path.length(), info(path)));
            }
        }
        return items.isEmpty() ? null : new LinkResult(items);
    }

    static boolean overlaps(List<LinkResultItem> items, int at) {
        for (var i : items) if (at >= i.getStartOffset() && at < i.getEndOffset()) return true;
        return false;
    }

    /** Punctuation that ends a sentence is not part of the link; a closing bracket only if it has no opening one. */
    static String trimEnd(String s) {
        while (!s.isEmpty()) {
            var c = s.charAt(s.length() - 1);
            if (".,;:!?'".indexOf(c) >= 0) s = s.substring(0, s.length() - 1);
            else if (c == ')' && s.chars().filter(ch -> ch == '(').count() < s.chars().filter(ch -> ch == ')').count()) s = s.substring(0, s.length() - 1);
            else break;
        }
        return s;
    }

    /** Opens it the way Explorer would. The target goes as an argument, never through a shell, so characters such as
     *  & in a URL cannot start anything else. */
    static void open(String target) {
        try {
            var dir = !target.contains("://") && Files.isDirectory(Path.of(target));
            var argv = !Os.WIN ? List.of(Os.MAC ? "open" : "xdg-open", target)
                    : dir ? List.of("explorer.exe", target) : List.of("rundll32.exe", "url.dll,FileProtocolHandler", target);
            new ProcessBuilder(argv).start();
        } catch (Exception e) {
            System.err.println("fxterm-tabs: could not open " + target + ": " + e);
        }
    }
}
