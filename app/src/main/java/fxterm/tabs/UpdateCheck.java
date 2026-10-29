package fxterm.tabs;

import module java.base;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.http.*;
import javafx.application.Platform;

/** Is a newer Tabs released? Asked when Tabs starts and every 24 hours after, and on request (the footer's check
 *  button). It reads the same update file the release exe's own -Xjr:update uses, so it needs neither the exe nor a
 *  process: the answer is only shown (in the footer), nothing is downloaded or replaced behind the user's back.
 *  The last answer is kept in ~/fxterm/tabs/update.json so the footer has something to show at once. */
public final class UpdateCheck {

    static final Path FILE = Path.of(System.getProperty("user.home"), "fxterm", "tabs", "update.json");
    static final URI FEED = URI.create("https://github.com/fxterm/tabs/releases/latest/download/fxterm-tabs.update.json");
    static final String PAGE = "https://github.com/fxterm/tabs/releases/latest";
    static final Duration EVERY = Duration.ofHours(24);
    static final ObjectMapper JSON = new ObjectMapper();

    /** The version this Tabs was built as, from the build (a resource filled by Maven); "dev" when run from an IDE. */
    final String current = version();
    volatile String latest, error;
    volatile Instant checked;
    volatile boolean checking;
    final Runnable changed;
    private boolean started;

    UpdateCheck(Runnable changed) {
        this.changed = changed;
        try {
            var n = JSON.readTree(FILE.toFile());
            latest = n.path("latest").asText(null);
            var at = n.path("checked").asText(null);
            if (at != null) checked = Instant.parse(at);
        } catch (Exception ignored) {
            // nothing kept yet
        }
    }

    static String version() {
        try (var in = UpdateCheck.class.getResourceAsStream("version.txt")) {
            var v = in == null ? "" : new String(in.readAllBytes(), StandardCharsets.UTF_8).strip();
            return v.isEmpty() || v.startsWith("$") ? "dev" : v;
        } catch (IOException e) {
            return "dev";
        }
    }

    /** Checks a few seconds after the start, then every 24 hours for as long as Tabs runs. */
    synchronized void start() {
        if (started) return;
        started = true;
        Thread.ofPlatform().daemon().name("update-check").start(() -> {
            try {
                Thread.sleep(6_000);
                while (true) {
                    check();
                    Thread.sleep(EVERY.toMillis());
                }
            } catch (InterruptedException e) {
                // Tabs is closing
            }
        });
    }

    /** The manual "check now": asks at once, off the UI thread. */
    void recheck() {
        if (checking) return;
        Thread.ofPlatform().daemon().name("update-check-now").start(this::check);
    }

    void check() {
        synchronized (this) {
            if (checking) return;
            checking = true;
        }
        tell();
        try {
            var client = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.ALWAYS).connectTimeout(java.time.Duration.ofSeconds(15)).build();
            var res = client.send(HttpRequest.newBuilder(FEED).timeout(java.time.Duration.ofSeconds(20)).header("User-Agent", "fxterm-tabs/" + current).build(),
                    HttpResponse.BodyHandlers.ofString());
            if (res.statusCode() != 200) throw new IOException("HTTP " + res.statusCode());
            var stable = JSON.readTree(res.body()).path("channels").path("stable").asText(null);
            if (stable == null) throw new IOException("no stable version in the update file");
            latest = stable;
            error = null;
            checked = Instant.now();
            save();
        } catch (Exception e) {
            error = e.getMessage() == null ? e.toString() : e.getMessage();
        } finally {
            checking = false;
            tell();
        }
    }

    void tell() {
        Platform.runLater(changed);
    }

    void save() {
        try {
            Files.createDirectories(FILE.getParent());
            Files.writeString(FILE, "{\"checked\": \"" + checked + "\", \"latest\": \"" + latest + "\"}\n");
        } catch (IOException e) {
            System.err.println("fxterm-tabs: could not keep the update answer: " + e);
        }
    }

    boolean available() {
        return latest != null && !"dev".equals(current) && newer(latest, current);
    }

    /** True when version a is higher than b, comparing the numbers part by part (0.1.10 is above 0.1.9). */
    static boolean newer(String a, String b) {
        var x = a.split("[.-]");
        var y = b.split("[.-]");
        for (var i = 0; i < Math.max(x.length, y.length); i++) {
            var p = i < x.length ? number(x[i]) : 0;
            var q = i < y.length ? number(y[i]) : 0;
            if (p != q) return p > q;
        }
        return false;
    }

    static int number(String s) {
        try {
            return Integer.parseInt(s);
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    /** The short line for the footer. */
    String status() {
        if (checking) return "Checking for updates…";
        if (available()) return "Update " + latest + " available";
        if (error != null && latest == null) return "Update check failed";
        if (latest == null) return "v" + current;
        return "v" + current + " is the latest";
    }

    String detail() {
        var when = checked == null ? "not yet" : java.time.LocalDateTime.ofInstant(checked, java.time.ZoneId.systemDefault())
                .format(java.time.format.DateTimeFormatter.ofPattern("d MMM HH:mm"));
        var line = "This is Tabs " + current + ". Last checked: " + when + (error != null ? " (the last try failed: " + error + ")" : "") + ".\n"
                + "Checked at start and every 24 hours. ";
        return line + (available() ? "Click to open the release page. A release exe also updates itself: fxterm-tabs.exe -Xjr:update."
                : "The button on the right checks now.");
    }
}
