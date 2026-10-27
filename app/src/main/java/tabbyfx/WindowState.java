package tabbyfx;

import module java.base;
import com.fasterxml.jackson.databind.*;
import javafx.stage.Stage;

/** Window position, size and the tab list's width, kept across runs in ~/tabbyfx/tabbyfx/window.json. */
public final class WindowState {

    static final Path FILE = Path.of(System.getProperty("user.home"), "tabbyfx", "tabbyfx", "window.json");
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
            System.err.println("tabbyfx: window state not saved: " + e);
        }
    }
}
