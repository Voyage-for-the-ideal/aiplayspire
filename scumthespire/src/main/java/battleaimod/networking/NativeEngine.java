package battleaimod.networking;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.Locale;

/** Only the serial planner executor owns a Session. Never access the game from JNI. */
public final class NativeEngine implements AutoCloseable {
    private static final String RESOURCE = "/native/windows-x86_64/battle_jni.dll";
    private static String loadedHash;
    private long handle;

    public NativeEngine() throws IOException {
        loadLibrary();
        handle = open();
    }

    private static String digest(InputStream source) throws IOException {
        try (InputStream in = source) {
            MessageDigest sha = MessageDigest.getInstance("SHA-256");
            byte[] buffer = new byte[32768]; int n;
            while ((n = in.read(buffer)) != -1) sha.update(buffer, 0, n);
            StringBuilder result = new StringBuilder();
            for (byte b : sha.digest()) result.append(String.format("%02x", b & 255));
            return result.toString();
        } catch (java.security.NoSuchAlgorithmException impossible) { throw new AssertionError(impossible); }
    }

    private static synchronized void loadLibrary() throws IOException {
        if (loadedHash != null) return;
        String arch = System.getProperty("os.arch");
        if (!System.getProperty("os.name").toLowerCase(Locale.ROOT).startsWith("windows") ||
                !("amd64".equalsIgnoreCase(arch) || "x86_64".equalsIgnoreCase(arch)))
            throw new IOException("Start Steve requires a Windows x64 JVM");
        Path root = Paths.get(System.getProperty("user.home"), ".aiplayspire", "native", "2");
        Files.createDirectories(root);
        String hash = digest(resource());
        Path dir = Files.createDirectories(root.resolve(hash));
        Path dll = dir.resolve("battle_jni.dll");
        if (!Files.exists(dll)) {
            Path temp = Files.createTempFile(dir, "battle-", ".tmp");
            try {
                // JRE 8 on Windows: do not unlink and recreate an open/scanned temp file.
                try (InputStream in = resource(); OutputStream out = Files.newOutputStream(temp)) {
                    byte[] buf = new byte[32768]; int n;
                    while ((n = in.read(buf)) != -1) out.write(buf, 0, n);
                }
                if (!hash.equals(digest(Files.newInputStream(temp)))) throw new IOException("Incomplete native library");
                try { Files.move(temp, dll); } catch (FileAlreadyExistsException race) { /* verify winner */ }
            } finally { Files.deleteIfExists(temp); }
        }
        if (!hash.equals(digest(Files.newInputStream(dll)))) throw new IOException("Native library hash mismatch: " + dll);
        System.load(dll.toAbsolutePath().toString());
        loadedHash = hash;
    }

    private static InputStream resource() throws IOException {
        InputStream in = NativeEngine.class.getResourceAsStream(RESOURCE);
        if (in == null) throw new IOException("Missing embedded " + RESOURCE);
        return in;
    }

    static synchronized String buildId() { return loadedHash; }
    public synchronized JsonObject search(JsonObject request) {
        if (handle == 0) throw new IllegalStateException("closed session");
        byte[] reply = call(handle, request.toString().getBytes(StandardCharsets.UTF_8));
        return new JsonParser().parse(new String(reply, StandardCharsets.UTF_8)).getAsJsonObject();
    }
    @Override public synchronized void close() {
        if (handle != 0) { close(handle); handle = 0; }
    }
    private static native long open();
    private static native byte[] call(long handle, byte[] request);
    private static native void close(long handle);
}
