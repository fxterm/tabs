package fxterm.tabs;

import module java.base;
import com.fasterxml.jackson.databind.ObjectMapper;

/** One window per user. The first launch listens on a loopback port and writes the port and a random token to
 *  ~/fxterm/tabs/run/instance.json; a later launch sends its Request there and exits. The token matters: the
 *  port opens tabs that run commands, so only a process that can read the user's own files may use it. */
public final class Instance {

    static final Path FILE = System.getenv("FXTERM_TABS_INSTANCE") != null ? Path.of(System.getenv("FXTERM_TABS_INSTANCE"))
            : Path.of(System.getProperty("user.home"), "fxterm", "tabs", "run", "instance.json");
    static final ObjectMapper JSON = new ObjectMapper();

    /** True if a running window took the request. */
    static boolean forward(Request r) {
        if (!Files.isReadable(FILE)) return false;
        try {
            var n = JSON.readTree(FILE.toFile());
            try (var s = new Socket()) {
                s.connect(new InetSocketAddress(InetAddress.getLoopbackAddress(), n.path("port").asInt()), 1500);
                var msg = JSON.createObjectNode();
                msg.put("token", n.path("token").asText());
                msg.set("request", JSON.valueToTree(r));
                s.getOutputStream().write((JSON.writeValueAsString(msg) + "\n").getBytes(StandardCharsets.UTF_8));
                s.getOutputStream().flush();
                return "ok".equals(new BufferedReader(new InputStreamReader(s.getInputStream(), StandardCharsets.UTF_8)).readLine());
            }
        } catch (IOException e) {
            return false;
        }
    }

    /** Becomes the instance; each accepted request is handed to the consumer (on a background thread). */
    static void serve(Consumer<Request> handler) {
        try {
            var server = new ServerSocket(0, 8, InetAddress.getLoopbackAddress());
            var token = HexFormat.of().formatHex(SecureRandom.getInstanceStrong().generateSeed(24));
            Files.createDirectories(FILE.getParent());
            var info = JSON.createObjectNode().put("port", server.getLocalPort()).put("token", token).put("pid", ProcessHandle.current().pid());
            Files.writeString(FILE, JSON.writeValueAsString(info));
            Thread.ofPlatform().daemon().name("instance").start(() -> {
                while (true) {
                    try (var s = server.accept()) {
                        s.setSoTimeout(3000);
                        var line = new BufferedReader(new InputStreamReader(s.getInputStream(), StandardCharsets.UTF_8)).readLine();
                        var n = JSON.readTree(line);
                        var ok = MessageDigest.isEqual(token.getBytes(), n.path("token").asText().getBytes());
                        if (ok) handler.accept(JSON.treeToValue(n.path("request"), Request.class));
                        s.getOutputStream().write((ok ? "ok\n" : "denied\n").getBytes(StandardCharsets.UTF_8));
                    } catch (IOException | RuntimeException e) {
                        System.err.println("fxterm-tabs: instance request failed: " + e);
                    }
                }
            });
        } catch (IOException | GeneralSecurityException e) {
            System.err.println("fxterm-tabs: cannot listen for other launches: " + e);
        }
    }

    static void release() {
        try {
            var n = JSON.readTree(FILE.toFile());
            if (n.path("pid").asLong() == ProcessHandle.current().pid()) Files.deleteIfExists(FILE);
        } catch (IOException ignored) { }
    }
}
