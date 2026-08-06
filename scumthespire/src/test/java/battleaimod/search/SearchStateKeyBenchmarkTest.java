package battleaimod.search;

import com.google.common.hash.Hashing;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.Assume;
import org.junit.Test;
import savestate.StateJsonHelper;

import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

public class SearchStateKeyBenchmarkTest {
    private static volatile int blackhole;

    @Test
    public void benchmarkWhenStateFileIsProvided() throws Exception {
        String stateFile = System.getProperty("stateFile");
        Assume.assumeTrue("set -DstateFile to run the manual benchmark",
                stateFile != null && !stateFile.trim().isEmpty());

        JsonElement state = readState(Paths.get(stateFile));
        int warmup = positiveProperty("stateWarmup", 200);
        int iterations = positiveProperty("stateIterations", 1_000);
        int samples = positiveProperty("stateSamples", 10);
        byte[] canonicalBytes = legacyCanonicalize(StateJsonHelper.normalizeCardUuids(state))
                .getBytes(StandardCharsets.UTF_8);

        runFullKey(state, warmup);
        runLegacyCanonical(state, warmup);
        runSha256(canonicalBytes, warmup);
        runMurmur3(canonicalBytes, warmup);

        for (int sample = 0; sample < samples; sample++) {
            long fullNanos = time(() -> runFullKey(state, iterations));
            long canonicalNanos = time(() -> runLegacyCanonical(state, iterations));
            long shaNanos = time(() -> runSha256(canonicalBytes, iterations));
            long murmurNanos = time(() -> runMurmur3(canonicalBytes, iterations));
            System.out.println("STATE_KEY_BENCHMARK sample=" + sample
                    + " full_ns=" + fullNanos / iterations
                    + " legacy_canonical_ns=" + canonicalNanos / iterations
                    + " sha256_ns=" + shaNanos / iterations
                    + " murmur3_128_ns=" + murmurNanos / iterations);
        }
    }

    private static JsonElement readState(Path path) throws IOException {
        try (Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            return new JsonParser().parse(reader);
        }
    }

    private static int positiveProperty(String name, int defaultValue) {
        int value = Integer.getInteger(name, defaultValue);
        if (value <= 0) {
            throw new IllegalArgumentException(name + " must be positive");
        }
        return value;
    }

    private static long time(Runnable task) {
        long startedAt = System.nanoTime();
        task.run();
        return System.nanoTime() - startedAt;
    }

    private static void runFullKey(JsonElement state, int iterations) {
        for (int i = 0; i < iterations; i++) {
            blackhole ^= SearchStateKey.fromJson(state).hashCode();
        }
    }

    private static void runLegacyCanonical(JsonElement state, int iterations) {
        for (int i = 0; i < iterations; i++) {
            blackhole ^= legacyCanonicalize(StateJsonHelper.normalizeCardUuids(state)).hashCode();
        }
    }

    private static void runSha256(byte[] canonicalBytes, int iterations) {
        MessageDigest digest = newDigest();
        for (int i = 0; i < iterations; i++) {
            digest.reset();
            blackhole ^= digest.digest(canonicalBytes)[0];
        }
    }

    private static void runMurmur3(byte[] canonicalBytes, int iterations) {
        for (int i = 0; i < iterations; i++) {
            blackhole ^= Hashing.murmur3_128().hashBytes(canonicalBytes).asBytes()[0];
        }
    }

    private static MessageDigest newDigest() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is unavailable", e);
        }
    }

    private static String legacyCanonicalize(JsonElement element) {
        if (element == null || element.isJsonNull()) {
            return "null";
        }
        if (element.isJsonArray()) {
            JsonArray array = element.getAsJsonArray();
            StringBuilder result = new StringBuilder("[");
            for (int i = 0; i < array.size(); i++) {
                if (i > 0) {
                    result.append(',');
                }
                result.append(legacyCanonicalize(array.get(i)));
            }
            return result.append(']').toString();
        }
        if (element.isJsonObject()) {
            JsonObject object = element.getAsJsonObject();
            List<String> keys = new ArrayList<>();
            for (Map.Entry<String, JsonElement> entry : object.entrySet()) {
                keys.add(entry.getKey());
            }
            Collections.sort(keys);

            StringBuilder result = new StringBuilder("{");
            for (int i = 0; i < keys.size(); i++) {
                if (i > 0) {
                    result.append(',');
                }
                String key = keys.get(i);
                result.append(quote(key)).append(':').append(legacyCanonicalize(object.get(key)));
            }
            return result.append('}').toString();
        }
        return element.toString();
    }

    private static String quote(String value) {
        JsonArray holder = new JsonArray();
        holder.add(value);
        String encoded = holder.toString();
        return encoded.substring(1, encoded.length() - 1);
    }
}
