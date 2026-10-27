package fxterm.tabs;

import module java.base;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.*;

/** The open tabs, saved on close and read on start: ~/fxterm/tabs/session.json (another file with --state). */
public final class SessionStore {

    static final ObjectMapper JSON = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT)
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    final Path file;

    SessionStore(Options o) {
        file = o.state != null ? Path.of(o.state) : Path.of(System.getProperty("user.home"), "fxterm", "tabs", "session.json");
    }

    List<SavedTab> load() {
        if (!Files.isReadable(file)) return List.of();
        try {
            return JSON.readValue(file.toFile(), new TypeReference<List<SavedTab>>() { });
        } catch (IOException e) {
            System.err.println("fxterm-tabs: session file unreadable, starting empty: " + e);
            return List.of();
        }
    }

    void save(List<SavedTab> tabs) {
        try {
            Files.createDirectories(file.getParent());
            var tmp = file.resolveSibling(file.getFileName() + ".tmp");
            JSON.writeValue(tmp.toFile(), tabs);
            Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException e) {
            System.err.println("fxterm-tabs: could not save the session: " + e);
        }
    }
}
