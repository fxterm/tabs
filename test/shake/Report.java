import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.List;

/** A page that shows the shake tests. Each test has a "before" and an "after" set of frames: a bar for each step
 *  from one frame to the next (how many pixels outside the animated mark's own slot changed), and the places that
 *  moved, in red, on a frame. Usage: java Report.java out.html { title beforeLabel beforeDir afterLabel afterDir
 *  side titleBar gx0 gx1 gy0 gy1 } ... */
public class Report {
    record Run(String label, int[] outside, BufferedImage overlay) {}

    public static void main(String[] a) throws Exception {
        var h = new StringBuilder();
        h.append("""
                <!doctype html><html lang="en"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1">
                <title>Tab shake tests</title><style>
                :root{--bg:#f6f6f4;--fg:#1d1d1b;--mute:#6b6b66;--card:#fff;--line:#dcdcd6;--bad:#d64545;--good:#2e9e5b}
                @media (prefers-color-scheme:dark){:root{--bg:#161616;--fg:#ececea;--mute:#9a9a94;--card:#202020;--line:#343432;--bad:#ff6b6b;--good:#4cc38a}}
                body{margin:0;background:var(--bg);color:var(--fg);font:15px/1.5 system-ui,Segoe UI,sans-serif}
                main{max-width:1100px;margin:0 auto;padding:28px 16px 60px}
                h1{font-size:22px;margin:0 0 4px}h2{font-size:17px;margin:34px 0 10px}p.sub{color:var(--mute);margin:0 0 6px}
                .sum{display:flex;flex-wrap:wrap;gap:8px;margin:16px 0 0}.pill{border:1px solid var(--line);background:var(--card);border-radius:99px;padding:3px 12px;font-size:13px}
                .pill b.good{color:var(--good)}.pill b.bad{color:var(--bad)}
                .cols{display:grid;grid-template-columns:1fr 1fr;gap:16px}@media(max-width:760px){.cols{grid-template-columns:1fr}}
                .card{background:var(--card);border:1px solid var(--line);border-radius:10px;padding:16px}
                .verdict{font-size:24px;font-weight:700;margin:2px 0}.bad{color:var(--bad)}.good{color:var(--good)}
                .meta{color:var(--mute);font-size:13px;margin-bottom:10px}
                .bars{display:flex;align-items:flex-end;gap:3px;height:90px;border-bottom:1px solid var(--line);margin:8px 0 4px}
                .bars div{flex:1;min-width:3px;background:var(--bad);border-radius:2px 2px 0 0}.bars div.z{background:var(--good);height:3px!important}
                .cap{color:var(--mute);font-size:12px}
                img{width:100%;border-radius:6px;border:1px solid var(--line);margin-top:12px;display:block}
                .note{margin-top:26px;color:var(--mute);font-size:13px}
                </style></head><body><main>
                <h1>Does the tab bar shake while a program animates its title?</h1>
                <p class="sub">Frames of the window, taken while a tab's title animates the way programs do it. Each bar is one step from a frame to the next: how many pixels changed anywhere except inside the animated mark's own slot. A steady tab bar has none. Red on the picture marks every place that moved at least once.</p>
                """);
        var body = new StringBuilder();
        var pills = new StringBuilder();
        for (int i = 1; i + 11 < a.length; i += 12) {
            String title = a[i];
            int inset = Integer.parseInt(a[i + 11]); int side = Integer.parseInt(a[i + 5]), tb = Integer.parseInt(a[i + 6]), gx0 = Integer.parseInt(a[i + 7]), gx1 = Integer.parseInt(a[i + 8]),
                    gy0 = Integer.parseInt(a[i + 9]), gy1 = Integer.parseInt(a[i + 10]);
            var runs = List.of(run(a[i + 1], a[i + 2], side, tb, gx0, gx1, gy0, gy1, inset), run(a[i + 3], a[i + 4], side, tb, gx0, gx1, gy0, gy1, inset));
            int peak = 1;
            for (var r : runs) for (int v : r.outside) peak = Math.max(peak, v);
            body.append("<h2>").append(esc(title)).append("</h2><div class=\"cols\">");
            boolean[] ok = new boolean[2];
            for (int k = 0; k < 2; k++) {
                var r = runs.get(k);
                int bad = 0, total = 0;
                for (int v : r.outside) { if (v > 0) bad++; total += v; }
                ok[k] = bad == 0;
                body.append("<div class=\"card\"><div class=\"meta\">").append(esc(r.label)).append("</div><div class=\"verdict ")
                        .append(ok[k] ? "good" : "bad").append("\">").append(ok[k] ? "Steady" : "Shakes").append("</div><div class=\"meta\">")
                        .append(bad).append(" of ").append(r.outside.length).append(" steps moved something &middot; ").append(total)
                        .append(" pixels in all</div><div class=\"bars\">");
                for (int v : r.outside)
                    body.append(v == 0 ? "<div class=\"z\" title=\"0\"></div>"
                            : "<div style=\"height:" + Math.max(3, (int) Math.round(v * 90.0 / peak)) + "px\" title=\"" + v + " px\"></div>");
                body.append("</div><div class=\"cap\">one bar per frame step (tallest = ").append(peak).append(" px)</div>");
                if (!ok[k]) body.append("<img alt=\"where it moved\" src=\"data:image/png;base64,").append(png(r.overlay)).append("\">");
                body.append("</div>");
            }
            body.append("</div>");
            pills.append("<span class=\"pill\">").append(esc(title.length() > 38 ? title.substring(0, 36) + "…" : title)).append(": <b class=\"")
                    .append(ok[0] ? "good" : "bad").append("\">").append(ok[0] ? "steady" : "shakes").append("</b> &rarr; <b class=\"")
                    .append(ok[1] ? "good" : "bad").append("\">").append(ok[1] ? "steady" : "shakes").append("</b></span>");
        }
        h.append("<div class=\"sum\">").append(pills).append("</div>").append(body);
        h.append("<p class=\"note\">Pictures are shown only for runs that moved. The animated mark itself is excluded from the count: in the new build it is a drawn ring that turns while the program works and a quiet dot when it stops, so it draws every frame but never changes size. ")
                .append("The on-screen test also covers the window title bar, where the old build put the animated symbol.</p></main></body></html>");
        Files.writeString(Path.of(a[0]), h.toString());
        System.out.println("wrote " + a[0]);
    }

    static Run run(String label, String dir, int side, int tb, int gx0, int gx1, int gy0, int gy1, int inset) throws Exception {
        var files = new ArrayList<Path>();
        try (var s = Files.list(Path.of(dir))) { s.filter(p -> p.getFileName().toString().matches("f\\d+\\.png")).forEach(files::add); }
        files.sort(Comparator.comparingInt(p -> Integer.parseInt(p.getFileName().toString().replaceAll("\\D", ""))));
        var frames = new ArrayList<BufferedImage>();
        for (var f : files) frames.add(ImageIO.read(f.toFile()));
        int n = frames.size() - 1;
        int[] outside = new int[n];
        var first = frames.getFirst();
        var overlay = new BufferedImage(first.getWidth(), first.getHeight(), BufferedImage.TYPE_INT_RGB);
        var g = overlay.createGraphics();
        g.drawImage(first, 0, 0, null);
        g.setColor(new Color(0, 0, 0, 90));
        g.fillRect(0, 0, overlay.getWidth(), overlay.getHeight());
        g.dispose();
        for (int f = 0; f < n; f++) {
            var p = frames.get(f); var q = frames.get(f + 1);
            for (int y = 0; y < Math.min(p.getHeight(), q.getHeight()); y++)
                for (int x = inset; x < (y < tb ? Math.min(p.getWidth(), q.getWidth()) - inset : Math.min(side, p.getWidth())); x++)
                    if (p.getRGB(x, y) != q.getRGB(x, y)) {
                        if (x >= gx0 && x < gx1 && y >= gy0 && y < gy1) continue;
                        outside[f]++;
                        for (int dx = -1; dx <= 1; dx++) for (int dy = -1; dy <= 1; dy++) {
                            int ox = x + dx, oy = y + dy;
                            if (ox >= 0 && oy >= 0 && ox < overlay.getWidth() && oy < overlay.getHeight()) overlay.setRGB(ox, oy, 0xFF3B3B);
                        }
                    }
        }
        var g2 = overlay.createGraphics();
        g2.setColor(new Color(0x2f6fd6)); g2.setStroke(new BasicStroke(2));
        g2.drawRect(gx0, gy0, gx1 - gx0, gy1 - gy0);
        g2.dispose();
        int cw = Math.min(overlay.getWidth(), Math.max(side + 40, 700)), ch = Math.min(overlay.getHeight(), 520);
        return new Run(label, outside, overlay.getSubimage(0, 0, cw, ch));
    }

    static String png(BufferedImage im) throws IOException {
        var out = new ByteArrayOutputStream();
        ImageIO.write(im, "png", out);
        return Base64.getEncoder().encodeToString(out.toByteArray());
    }

    static String esc(String s) { return s.replace("&", "&amp;").replace("<", "&lt;"); }
}
