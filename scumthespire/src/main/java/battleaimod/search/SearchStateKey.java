package battleaimod.search;

import com.google.common.hash.HashFunction;
import com.google.common.hash.Hasher;
import com.google.common.hash.Hashing;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import savestate.SaveState;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class SearchStateKey {
    public static final String ALGORITHM = "murmur3_128";
    private static final char[] HEX = "0123456789abcdef".toCharArray();
    private static final ThreadLocal<CanonicalHasher> HASHER = new ThreadLocal<CanonicalHasher>() {
        @Override
        protected CanonicalHasher initialValue() {
            return new CanonicalHasher();
        }
    };

    private final byte[] digest;
    private final int hashCode;
    private String encodedDigest;

    private SearchStateKey(byte[] digest) {
        this.digest = digest;
        this.hashCode = Arrays.hashCode(digest);
    }

    public static SearchStateKey fromSaveState(SaveState state) {
        return fromJson(state.jsonEncode());
    }

    public static String algorithm() {
        return ALGORITHM;
    }

    public static SearchStateKey fromJson(JsonElement state) {
        return new SearchStateKey(HASHER.get().hash(state));
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
        return Arrays.equals(digest, that.digest);
    }

    @Override
    public int hashCode() {
        return hashCode;
    }

    @Override
    public String toString() {
        if (encodedDigest == null) {
            char[] encoded = new char[digest.length * 2];
            for (int i = 0; i < digest.length; i++) {
                int value = digest[i] & 0xff;
                encoded[i * 2] = HEX[value >>> 4];
                encoded[i * 2 + 1] = HEX[value & 0x0f];
            }
            encodedDigest = new String(encoded);
        }
        return encodedDigest;
    }

    private static final class CanonicalHasher {
        private static final int BUFFER_SIZE = 8 * 1024;
        private static final HashFunction HASH_FUNCTION = Hashing.murmur3_128();
        private final byte[] buffer = new byte[BUFFER_SIZE];
        private final Map<String, String> uuidAliases = new HashMap<>();
        private Hasher hasher;
        private int bufferLength;
        private int nextUuidAlias;

        private byte[] hash(JsonElement state) {
            hasher = HASH_FUNCTION.newHasher();
            bufferLength = 0;
            uuidAliases.clear();
            nextUuidAlias = 0;
            write(state);
            flush();
            return hasher.hash().asBytes();
        }

        private void write(JsonElement element) {
            if (element == null || element.isJsonNull()) {
                writeAscii("null");
                return;
            }
            if (element.isJsonArray()) {
                writeByte('[');
                for (int i = 0; i < element.getAsJsonArray().size(); i++) {
                    if (i > 0) {
                        writeByte(',');
                    }
                    write(element.getAsJsonArray().get(i));
                }
                writeByte(']');
                return;
            }
            if (element.isJsonObject()) {
                writeObject(element.getAsJsonObject());
                return;
            }

            JsonPrimitive primitive = element.getAsJsonPrimitive();
            if (primitive.isString()) {
                writeString(primitive.getAsString());
            } else {
                writeAscii(primitive.toString());
            }
        }

        private void writeObject(JsonObject object) {
            List<String> keys = new ArrayList<>();
            for (Map.Entry<String, JsonElement> entry : object.entrySet()) {
                keys.add(entry.getKey());
            }
            Collections.sort(keys);

            writeByte('{');
            for (int i = 0; i < keys.size(); i++) {
                if (i > 0) {
                    writeByte(',');
                }
                String key = keys.get(i);
                JsonElement value = object.get(key);
                writeString(key);
                writeByte(':');
                if (isStringUuid(key, value)) {
                    writeString(uuidAlias(value.getAsString()));
                } else {
                    write(value);
                }
            }
            writeByte('}');
        }

        private static boolean isStringUuid(String key, JsonElement value) {
            return "uuid".equals(key) && value != null && value.isJsonPrimitive()
                    && value.getAsJsonPrimitive().isString();
        }

        private String uuidAlias(String uuid) {
            String alias = uuidAliases.get(uuid);
            if (alias == null) {
                alias = "card-uuid-" + nextUuidAlias++;
                uuidAliases.put(uuid, alias);
            }
            return alias;
        }

        private void writeString(String value) {
            writeByte('"');
            for (int index = 0; index < value.length(); index++) {
                char current = value.charAt(index);
                switch (current) {
                    case '"':
                        writeAscii("\\\"");
                        break;
                    case '\\':
                        writeAscii("\\\\");
                        break;
                    case '\t':
                        writeAscii("\\t");
                        break;
                    case '\b':
                        writeAscii("\\b");
                        break;
                    case '\n':
                        writeAscii("\\n");
                        break;
                    case '\r':
                        writeAscii("\\r");
                        break;
                    case '\f':
                        writeAscii("\\f");
                        break;
                    default:
                        if (current <= 0x1f || current == '\u2028' || current == '\u2029') {
                            writeUnicodeEscape(current);
                        } else if (Character.isHighSurrogate(current) && index + 1 < value.length()
                                && Character.isLowSurrogate(value.charAt(index + 1))) {
                            writeCodePoint(Character.toCodePoint(current, value.charAt(++index)));
                        } else if (Character.isSurrogate(current)) {
                            writeByte('?');
                        } else {
                            writeCodePoint(current);
                        }
                }
            }
            writeByte('"');
        }

        private void writeUnicodeEscape(char value) {
            writeAscii("\\u");
            writeByte(HEX[(value >>> 12) & 0x0f]);
            writeByte(HEX[(value >>> 8) & 0x0f]);
            writeByte(HEX[(value >>> 4) & 0x0f]);
            writeByte(HEX[value & 0x0f]);
        }

        private void writeCodePoint(int codePoint) {
            if (codePoint <= 0x7f) {
                writeByte(codePoint);
            } else if (codePoint <= 0x7ff) {
                writeByte(0xc0 | codePoint >>> 6);
                writeByte(0x80 | codePoint & 0x3f);
            } else if (codePoint <= 0xffff) {
                writeByte(0xe0 | codePoint >>> 12);
                writeByte(0x80 | codePoint >>> 6 & 0x3f);
                writeByte(0x80 | codePoint & 0x3f);
            } else {
                writeByte(0xf0 | codePoint >>> 18);
                writeByte(0x80 | codePoint >>> 12 & 0x3f);
                writeByte(0x80 | codePoint >>> 6 & 0x3f);
                writeByte(0x80 | codePoint & 0x3f);
            }
        }

        private void writeAscii(String value) {
            for (int i = 0; i < value.length(); i++) {
                writeByte(value.charAt(i));
            }
        }

        private void writeByte(int value) {
            if (bufferLength == buffer.length) {
                flush();
            }
            buffer[bufferLength++] = (byte) value;
        }

        private void flush() {
            if (bufferLength > 0) {
                hasher.putBytes(buffer, 0, bufferLength);
                bufferLength = 0;
            }
        }
    }
}
