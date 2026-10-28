package fxterm.tabs;

import module java.base;
import com.fasterxml.jackson.databind.*;
import javafx.stage.Stage;

/** Window position, size and the tab list's width, kept across runs in ~/fxterm/tabs/window.json. */
public final class WindowState {

    static final Path FILE = Path.of(System.getProperty("user.home"), "fxterm", "tabs", "window.json");
    static final ObjectMapper JSON = new ObjectMapper().configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    public double x = Double.NaN, y = Double.NaN, width = 1200, height = 720, sidebar = 240;
    public boolean maximized;

    static WindowState load() {
        try {
            return Files.isReadable(FILE) ? JSON.readValue(FILE.toFile(), WindowState.class) : new WindowState();
        } catch (IOException e) {
            return new WindowState();
        }
    }

    void apply(Stage stage) {
        if (!Double.isNaN(x)) {
            stage.setX(x);
            stage.setY(y);
        }
        stage.setMaximized(maximized);
    }

    static final Path ALL = FILE.resolveSibling("windows.json");

    /** Every window's place, in order; the old single-window file for the first if there is no list yet. */
    static List<WindowState> loadAll() {
        try {
            if (Files.isReadable(ALL)) return List.of(JSON.readValue(ALL.toFile(), WindowState[].class));
        } catch (IOException e) {
            System.err.println("fxterm-tabs: windows.json unreadable: " + e);
        }
        return List.of(load());
    }

    static void saveAll(List<TabsWindow> windows) {
        var all = windows.stream().map(w -> of(w.stage, w.sidebarWidth())).toList();
        try {
            Files.createDirectories(FILE.getParent());
            JSON.writeValue(ALL.toFile(), all);
            JSON.writeValue(FILE.toFile(), all.getFirst());
        } catch (IOException e) {
            System.err.println("fxterm-tabs: window state not saved: " + e);
        }
    }

    static WindowState of(Stage stage, double sidebar) {
        var s = new WindowState();
        s.maximized = stage.isMaximized();
        s.x = stage.getX();
        s.y = stage.getY();
        s.width = stage.getWidth();
        s.height = stage.getHeight();
        s.sidebar = sidebar;
        return s;
    }

    static void save(Stage stage, double sidebar) {
        var s = new WindowState();
        s.maximized = stage.isMaximized();
        s.x = stage.getX();
        s.y = stage.getY();
        s.width = stage.getWidth();
        s.height = stage.getHeight();
        s.sidebar = sidebar;
        try {
            Files.createDirectories(FILE.getParent());
            JSON.writeValue(FILE.toFile(), s);
        } catch (IOException e) {
            System.err.println("fxterm-tabs: window state not saved: " + e);
        }
    }
}
