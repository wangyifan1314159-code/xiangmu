import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Minimal connectivity check for the IoT backend API. Requires Java 11+. */
public final class ApiConnectivityTest {
    private static final Pattern TOKEN = Pattern.compile("\\\"token\\\"\\s*:\\s*\\\"([^\\\"]+)\\\"");

    private final HttpClient client = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .build();
    private final String baseUrl;
    private int failures;

    private ApiConnectivityTest(String baseUrl) {
        this.baseUrl = baseUrl.replaceAll("/+$", "");
    }

    public static void main(String[] args) {
        String baseUrl = value(args, "--base-url", "http://localhost:8080");
        String username = value(args, "--username", null);
        String password = value(args, "--password", null);

        ApiConnectivityTest test = new ApiConnectivityTest(baseUrl);
        test.check("health", "GET", "/api/health", null, null);
        test.check("readiness", "GET", "/api/health/ready", null, null);

        if (username == null || password == null) {
            System.out.println("[SKIP] login/devices (provide --username and --password to test authenticated APIs)");
        } else {
            String token = test.login(username, password);
            if (token != null) {
                test.check("devices", "GET", "/api/devices", null, token);
            }
        }

        System.out.printf("Result: %s (%d failure(s))%n", test.failures == 0 ? "PASS" : "FAIL", test.failures);
        if (test.failures > 0) System.exit(1);
    }

    private String login(String username, String password) {
        String body = "{\"username\":\"" + json(username) + "\",\"password\":\"" + json(password) + "\"}";
        HttpResponse<String> response = send("POST", "/api/auth/login", body, null);
        if (!ok("login", response)) return null;

        Matcher matcher = TOKEN.matcher(response.body());
        if (!matcher.find()) {
            failures++;
            System.out.println("[FAIL] login: HTTP response does not contain a token");
            return null;
        }
        System.out.println("[PASS] login: token received");
        return matcher.group(1);
    }

    private void check(String name, String method, String path, String body, String token) {
        HttpResponse<String> response = send(method, path, body, token);
        ok(name, response);
    }

    private HttpResponse<String> send(String method, String path, String body, String token) {
        try {
            HttpRequest.Builder request = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + path))
                    .timeout(Duration.ofSeconds(10))
                    .header("Accept", "application/json");
            if (token != null) request.header("Authorization", "Bearer " + token);
            if (body == null) request.method(method, HttpRequest.BodyPublishers.noBody());
            else request.header("Content-Type", "application/json")
                    .method(method, HttpRequest.BodyPublishers.ofString(body));
            return client.send(request.build(), HttpResponse.BodyHandlers.ofString());
        } catch (IOException | InterruptedException | IllegalArgumentException e) {
            if (e instanceof InterruptedException) Thread.currentThread().interrupt();
            System.out.printf("[FAIL] %s %s: %s%n", method, path, e.getMessage());
            return null;
        }
    }

    private boolean ok(String name, HttpResponse<String> response) {
        if (response != null && response.statusCode() >= 200 && response.statusCode() < 300) {
            System.out.printf("[PASS] %s: HTTP %d%n", name, response.statusCode());
            return true;
        }
        failures++;
        System.out.printf("[FAIL] %s: %s%n", name, response == null ? "no response" : "HTTP " + response.statusCode());
        return false;
    }

    private static String value(String[] args, String key, String fallback) {
        for (int i = 0; i < args.length - 1; i++) {
            if (key.equals(args[i])) return args[i + 1];
        }
        return fallback;
    }

    private static String json(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
