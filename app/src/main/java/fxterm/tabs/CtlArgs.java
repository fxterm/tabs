package fxterm.tabs;

import module java.base;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.json.JsonWriteFeature;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.databind.node.*;

/** The words of `fxterm-tabs ctl ...` turned into the JSON request. Three forms, the last two for text a command line mangles
 *  (non-ASCII characters on Windows, quotes removed by PowerShell 5.1):
 *  `ctl rename id=t1 title=text` · `ctl --json '{"cmd":"rename",...}'` · `ctl -` (the request as JSON on stdin, UTF-8).
 *  A word without `=` that follows a `name=value` word is the rest of that value (`title=My tab` unquoted keeps "My tab"). */
final class CtlArgs {

    /** The --state=<file> of a test run, or null. */
    static String state(List<String> args) {
        return args.stream().filter(a -> a.startsWith("--state=")).map(a -> a.substring(8)).findFirst().orElse(null);
    }

    /** The request, or null when help is wanted. */
    static ObjectNode request(List<String> args) throws IOException {
        var rest = args.stream().filter(a -> !a.startsWith("--state=")).toList();
        if (!rest.isEmpty() && rest.getFirst().equals("-")) return object(new String(System.in.readAllBytes(), StandardCharsets.UTF_8));
        if (!rest.isEmpty() && rest.getFirst().startsWith("--json")) {
            var inline = rest.getFirst().startsWith("--json=");
            if (!inline && rest.size() < 2) throw new IllegalArgumentException("--json needs the request: ctl --json '{\"cmd\":\"list\"}'");
            return object(inline ? rest.getFirst().substring(7) : String.join(" ", rest.subList(1, rest.size())));
        }
        var req = Control.JSON.createObjectNode();
        var words = new ArrayList<String>();
        String last = null;
        for (var a : rest) {
            var eq = a.indexOf('=');
            if (eq > 0) { last = a.substring(0, eq); req.set(last, Control.value(a.substring(eq + 1))); }
            else if (last != null && req.get(last).isTextual()) req.put(last, req.get(last).asText() + " " + a);
            else words.add(a);
        }
        if (words.isEmpty() || words.getFirst().equals("help")) return null;
        req.put("cmd", words.getFirst());
        if (words.getFirst().equals("attention") && words.size() > 1) req.put("on", !words.get(1).equals("off"));
        return req;
    }

    static final ObjectMapper ASCII = JsonMapper.builder().enable(JsonWriteFeature.ESCAPE_NON_ASCII).build();

    /** The reply with every non-ASCII character written as a JSON escape: still the same JSON, and it survives any console code page (🔔 would print as ??). */
    static String ascii(String reply) {
        try {
            return reply == null ? "null" : ASCII.writeValueAsString(ASCII.readTree(reply));
        } catch (IOException e) {
            return reply;
        }
    }

    static ObjectNode object(String json) throws IOException {
        JsonNode n;
        try {
            n = Control.JSON.readTree(json.strip());
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("the request is not valid JSON (PowerShell 5.1 removes the quotes of an argument: pipe the request into `ctl -` instead)");
        }
        if (n instanceof ObjectNode o && o.hasNonNull("cmd")) return o;
        throw new IllegalArgumentException("the request must be a JSON object with a \"cmd\"");
    }

    private CtlArgs() { }
}
