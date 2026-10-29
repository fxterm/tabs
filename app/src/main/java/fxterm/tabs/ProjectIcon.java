package fxterm.tabs;

import module java.base;
import com.fasterxml.jackson.databind.*;
import javafx.scene.image.Image;

/** The icon of the project a tab is in, shown beside its number. The proj tool keeps `prp/proj.json` (uuid, name,
 *  longName, icon) and resolves the icon itself (proj.json, then the repo's, then the org's), writing it to
 *  ~/littlejlib/proj/icons/<uuid>.png; Tabs only reads local files, and never fetches anything. No proj.json or no
 *  icon file: no icon. */
public final class ProjectIcon {

    static final Path CACHE = Path.of(System.getProperty("user.home"), "littlejlib", "proj", "icons");
    static final ObjectMapper JSON = new ObjectMapper();
    /** Looked up again after this long, so an icon proj writes later appears without a restart. */
    static final long FRESH_MS = 60_000;

    record Found(Image image, String name, long at) { }

    static final Map<String, Found> byFolder = new ConcurrentHashMap<>();

    /** The icon for a tab's folder, or null. Cached per folder; the list redraws often. */
    static Image of(String cwd) {
        var f = find(cwd);
        return f == null ? null : f.image;
    }

    /** The project's short name from proj.json, or null. */
    static String name(String cwd) {
        var f = find(cwd);
        return f == null ? null : f.name;
    }

    static Found find(String cwd) {
        if (cwd == null) return null;
        var now = System.currentTimeMillis();
        var f = byFolder.get(cwd);
        if (f != null && now - f.at < FRESH_MS) return f;
        f = load(cwd, now);
        byFolder.put(cwd, f);
        return f;
    }

    static Found load(String cwd, long now) {
        var none = new Found(null, null, now);
        var prp = Quote.prpDir(cwd);
        if (prp == null) return none;
        var file = prp.resolve("proj.json");
        if (!Files.isReadable(file)) return none;
        try {
            var n = JSON.readTree(file.toFile());
            var name = n.path("name").asText(null);
            Path icon = null;
            var own = n.path("icon").asText(null);
            // an icon kept in the repo is read where it is; anything else proj has already cached by uuid
            if (own != null && !own.isBlank() && !own.contains("://")) {
                var p = Path.of(own);
                p = p.isAbsolute() ? p : prp.getParent().resolve(p);
                if (Files.isReadable(p)) icon = p;
            }
            var uuid = n.path("uuid").asText(null);
            if (icon == null && uuid != null && Files.isReadable(CACHE.resolve(uuid.toLowerCase(Locale.ROOT) + ".png")))
                icon = CACHE.resolve(uuid.toLowerCase(Locale.ROOT) + ".png");
            // loaded at twice the drawn size, so it stays sharp on a scaled display
            var image = icon == null ? null : new Image(icon.toUri().toString(), 32, 32, true, true);
            return new Found(image == null || image.isError() ? null : image, name, now);
        } catch (IOException | RuntimeException e) {
            System.err.println("fxterm-tabs: " + file + " unreadable: " + e);
            return none;
        }
    }

    private ProjectIcon() { }
}
