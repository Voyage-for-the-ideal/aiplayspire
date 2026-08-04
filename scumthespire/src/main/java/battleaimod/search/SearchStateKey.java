package battleaimod.search;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import savestate.SaveState;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

public final class SearchStateKey {
    private final String digest;

    private SearchStateKey(String canonicalState) {
        this.digest = sha256(canonicalState);
    }

    public static SearchStateKey fromSaveState(SaveState state) {
        return fromJson(state.jsonEncode());
    }

    public static SearchStateKey fromJson(JsonElement state) {
        return new SearchStateKey(canonicalize(state));
    }

    static String canonicalize(JsonElement element) {
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
                result.append(canonicalize(array.get(i)));
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
                result.append(quote(key)).append(':').append(canonicalize(object.get(key)));
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

    private static String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder result = new StringBuilder(hash.length * 2);
            for (byte current : hash) {
                result.append(String.format("%02x", current & 0xff));
            }
            return result.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is unavailable", e);
        }
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof SearchStateKey)) {
            return false;
        }
        SearchStateKey that = (SearchStateKey) other;
        return digest.equals(that.digest);
    }

    @Override
    public int hashCode() {
        return digest.hashCode();
    }

    @Override
    public String toString() {
        return digest;
    }
}
