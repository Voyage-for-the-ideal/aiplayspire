package battleaimod.networking;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.concurrent.atomic.AtomicBoolean;

/** Probe the configured endpoint directly, independently of the old 5124 launcher handshake. */
final class ExternalBackendMonitor {
    private static final AtomicBoolean started = new AtomicBoolean();
    private static volatile boolean ready;

    private ExternalBackendMonitor() { }
    static boolean ready() { return ready; }

    static void start() {
        if (!started.compareAndSet(false, true)) { return; }
        Thread thread = new Thread(() -> {
            while (!Thread.currentThread().isInterrupted()) {
                try (Socket socket = new Socket()) {
                    socket.connect(new InetSocketAddress("127.0.0.1", BackendEndpoint.port()), 500);
                    socket.setSoTimeout(1000);
                    new DataOutputStream(socket.getOutputStream()).writeUTF("{\"type\":\"PING\"}");
                    JsonObject pong = new JsonParser().parse(new DataInputStream(socket.getInputStream()).readUTF()).getAsJsonObject();
                    ready = pong.has("type") && "PONG".equals(pong.get("type").getAsString())
                            && pong.has("ready") && pong.get("ready").getAsBoolean();
                } catch (Exception error) {
                    ready = false;
                }
                try { Thread.sleep(1000); }
                catch (InterruptedException error) { Thread.currentThread().interrupt(); }
            }
        }, "external-battle-backend-health");
        thread.setDaemon(true);
        thread.start();
    }
}
