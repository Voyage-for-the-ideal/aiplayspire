import com.badlogic.gdx.Gdx;
import com.google.gson.Gson;
import com.megacrit.cardcrawl.headless.GameKernel;
import com.megacrit.cardcrawl.headless.HeadlessObservation;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/** CommunicationMod-compatible out-of-run state only. No action dispatcher. */
public final class ReadonlyStateServer {
    private static final Gson JSON = new Gson();

    static Map<String, Object> compatibleState(GameKernel kernel) {
        NoRunGuard.verify();
        Map<String, Object> nativeState = HeadlessObservation.build(kernel);
        if (!"BOOT".equals(nativeState.get("phase"))) {
            throw new IllegalStateException("Expected native BOOT observation");
        }
        // Match CommunicationMod's out-of-run fields; do not invent in-run values.
        Map<String, Object> state = new LinkedHashMap<>();
        state.put("screen_type", "MAIN_MENU");
        // Gson 2.8.8 must not reflect into JDK 21's private Collections$EmptyList.
        state.put("choice_list", new ArrayList<>());
        state.put("can_proceed", false);
        state.put("can_cancel", false);
        Map<String, Object> headless = new LinkedHashMap<>();
        headless.put("schema", "headless-readonly-state-v1");
        headless.put("engine_phase", nativeState.get("phase"));
        headless.put("read_only", true);
        headless.put("run_started", false);
        headless.put("collection_enabled", false);
        headless.put("player_initialized", false);
        headless.put("map_initialized", false);
        headless.put("room_initialized", false);
        headless.put("seed_initialized", false);
        headless.put("rng_streams_initialized", false);
        headless.put("floor", nativeState.get("floor"));
        headless.put("act", nativeState.get("act"));
        headless.put("graphics_type", String.valueOf(Gdx.graphics.getType()));
        headless.put("gl_present", false);
        state.put("headless", headless);
        NoRunGuard.verify();
        return state;
    }

    static String compatibleJson(GameKernel kernel) {
        return JSON.toJson(compatibleState(kernel));
    }

    static int statusFor(String path, String query, String method) {
        if (!"/state".equals(path)) return 404;
        if (!"GET".equals(method)) return 405;
        if (query != null) return 400;
        return 200;
    }

    private static void reply(HttpExchange exchange, int status, Object value) throws IOException {
        byte[] bytes = JSON.toJson(value).getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
        exchange.getResponseHeaders().set("Cache-Control", "no-store");
        exchange.getResponseHeaders().set("X-Headless-Read-Only", "true");
        exchange.getResponseHeaders().set("Connection", "close");
        if (status == 405) exchange.getResponseHeaders().set("Allow", "GET");
        boolean head = "HEAD".equals(exchange.getRequestMethod());
        exchange.sendResponseHeaders(status, head ? -1 : bytes.length);
        if (!head) {
            try (var output = exchange.getResponseBody()) {
                output.write(bytes);
            }
        }
    }

    public static void main(String[] args) throws Exception {
        if (args.length != 3) throw new IllegalArgumentException("dataRoot saveRoot port");
        int port = Integer.parseInt(args[2]);
        if (port < 1 || port > 65535) throw new IllegalArgumentException("Invalid port");
        GameKernel.boot(args[0], args[1]);
        GameKernel kernel = NoRunGuard.kernel();
        compatibleJson(kernel); // Check native state AND wire serialization before listening.
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", port), 8);
        ThreadPoolExecutor executor = new ThreadPoolExecutor(1, 1, 0, TimeUnit.SECONDS,
                new ArrayBlockingQueue<>(16), new ThreadPoolExecutor.AbortPolicy());
        server.setExecutor(executor);
        server.createContext("/", exchange -> {
            try {
                int status = statusFor(exchange.getRequestURI().getRawPath(),
                        exchange.getRequestURI().getRawQuery(), exchange.getRequestMethod());
                if (status != 200) {
                    reply(exchange, status, Collections.singletonMap("error", "Read-only GET /state endpoint"));
                    return;
                }
                Map<String, Object> state;
                try {
                    state = compatibleState(kernel);
                } catch (RuntimeException error) {
                    System.err.println("Read-only state guard failed: " + error.getClass().getSimpleName());
                    reply(exchange, 503, Collections.singletonMap("error", "Pre-run state invariant failed"));
                    return;
                }
                reply(exchange, 200, state);
            } catch (Exception error) {
                error.printStackTrace(System.err);
            } finally {
                exchange.close();
            }
        });
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            server.stop(1);
            executor.shutdownNow();
        }, "readonly-state-shutdown"));
        server.start();
        System.out.println("READONLY_STATE_READY http://127.0.0.1:" + port + "/state");
    }
}
