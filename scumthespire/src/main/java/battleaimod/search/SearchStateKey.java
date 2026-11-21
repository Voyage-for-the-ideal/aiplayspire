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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.nio.charset.StandardCharsets;

public final class SearchStateKey {
    public static final String ALGORITHM = "murmur3_128";
    private static final char[] HEX = "0123456789abcdef".toCharArray();
    private static final boolean VERIFY_DIRECT = Boolean.getBoolean("battleai.verifyDirectStateKey");
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
        SearchStateKey result = new SearchStateKey(HASHER.get().hashSnapshot(state));
        if (VERIFY_DIRECT && !result.equals(fromJson(state.jsonEncode()))) {
            throw new IllegalStateException("Direct SaveState fingerprint differs from canonical reference");
        }
        return result;
    }

    public static String algorithm() {
        return ALGORITHM;
    }

    public static SearchStateKey fromJson(JsonElement state) {
        return new SearchStateKey(HASHER.get().hash(state));
    }

    /** Immutable schema bytes only; values and UUID aliases are never cached here. */
    static byte[] encodeFieldName(String name) {
        return (new JsonPrimitive(name).toString() + ":").getBytes(StandardCharsets.UTF_8);
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
        private static final int MAX_SHAPES = 256;
        private static final int MAX_SHAPES_PER_SIZE = 8;
        private static final int MAX_STRINGS = 4096;
        private static final int MAX_CACHED_STRING_LENGTH = 256;
        private static final int MAX_UUID_OBJECT_ALIASES = 512;
        private static final HashFunction HASH_FUNCTION = Hashing.murmur3_128();
        private final byte[] buffer = new byte[BUFFER_SIZE];
        private final byte[] integerBuffer = new byte[20];
        private final Map<String, String> uuidAliases = new HashMap<>();
        private final Map<UUID, String> uuidObjectAliases = new HashMap<>();
        // Cache immutable encoding metadata only, never JsonElements or their
        // digests: snapshots/extension payloads may be mutated between calls.
        private final Map<Integer, List<ObjectShape>> shapes = new HashMap<>();
        private final Map<String, byte[]> quotedAscii = new HashMap<>();
        private int shapeCount;
        private final SaveStateFields.RootFields rootFields = new SaveStateFields.RootFields();
        private Hasher hasher;
        private int bufferLength;
        private int nextUuidAlias;
        private boolean snapshotUuidObjects;

        private byte[] hash(JsonElement state) {
            reset(false);
            write(state);
            flush();
            return hasher.hash().asBytes();
        }

        private byte[] hashSnapshot(SaveState state) {
            reset(true);
            writeSnapshot(state);
            flush();
            return hasher.hash().asBytes();
        }

        private void reset(boolean snapshotUuidObjects) {
            this.snapshotUuidObjects = snapshotUuidObjects;
            hasher = HASH_FUNCTION.newHasher();
            bufferLength = 0;
            uuidAliases.clear();
            uuidObjectAliases.clear();
            nextUuidAlias = 0;
        }

        private void writeSnapshot(Object value) {
            if (value == null) { writeAscii("null"); return; }
            if (value instanceof JsonElement) { write((JsonElement) value); return; }
            if (value instanceof Boolean) { writeAscii(value.toString()); return; }
            if (value instanceof Number) { writeNumber((Number) value); return; }
            if (value instanceof String || value instanceof UUID) { writeString(value.toString()); return; }
            if (value instanceof Enum) { writeString(((Enum<?>) value).name()); return; }
            if (value instanceof Iterable) {
                writeByte('[');
                boolean first = true;
                for (Object item : (Iterable<?>) value) {
                    if (!first) writeByte(',');
                    first = false;
                    writeSnapshot(item);
                }
                writeByte(']');
                return;
            }
            if (value instanceof Object[]) {
                writeByte('[');
                Object[] array = (Object[]) value;
                for (int i = 0; i < array.length; i++) {
                    if (i > 0) writeByte(',');
                    writeSnapshot(array[i]);
                }
                writeByte(']');
                return;
            }
            SaveStateFields.Plan plan = SaveStateFields.plan(value.getClass());
            if (plan.fields == null) {
                write(plan.encode(value));
            } else {
                writeFields(value, plan.root ? rootFields.get(plan.fields) : plan.fields);
            }
        }

        private void writeFields(Object value, SaveStateFields.Binding[] fields) {
            writeByte('{');
            for (int i = 0; i < fields.length; i++) {
                if (i > 0) writeByte(',');
                SaveStateFields.Binding field = fields[i];
                writeBytes(field.encodedName);
                if (field.primitive != null) {
                    writePrimitiveField(value, field);
                } else if (field.embedded != null) {
                    writeFields(value, field.embedded);
                } else {
                    Object item = field.reader.get(value);
                    if (field.uuid && item instanceof UUID) {
                        writeString(uuidAlias((UUID) item));
                    } else if (field.uuid && item instanceof String) {
                        writeString(uuidAlias((String) item));
                    } else if (field.uuid && item instanceof JsonElement
                            && isStringUuid(field.name, (JsonElement) item)) {
                        writeString(uuidAlias(((JsonElement) item).getAsString()));
                    } else {
                        writeSnapshot(item);
                    }
                }
            }
            writeByte('}');
        }

        private void writePrimitiveField(Object value, SaveStateFields.Binding field) {
            // Core schema metadata selects typed getters, avoiding boxing and
            // general Object dispatch for every card's numeric/boolean fields.
            try {
                Class<?> type = field.primitiveType;
                if (type == int.class) writeInteger(field.primitive.getInt(value));
                else if (type == boolean.class) writeAscii(field.primitive.getBoolean(value) ? "true" : "false");
                else if (type == float.class) writeAscii(Float.toString(field.primitive.getFloat(value)));
                else if (type == long.class) writeInteger(field.primitive.getLong(value));
                else if (type == double.class) writeAscii(Double.toString(field.primitive.getDouble(value)));
                else writeSnapshot(field.reader.get(value));
            } catch (IllegalAccessException e) {
                throw new IllegalStateException("Snapshot field access failed: " + field.primitive, e);
            }
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
            } else if (primitive.isNumber()) {
                writeNumber(primitive.getAsNumber());
            } else {
                // JsonPrimitive.toString allocates a StringWriter/JsonWriter
                // for every number and boolean. Their JSON spelling is already
                // provided by getAsString (including exponent/negative zero).
                writeAscii(primitive.getAsString());
            }
        }

        private void writeObject(JsonObject object) {
            ObjectShape shape = sortedShape(object);
            String[] keys = shape.sorted;

            writeByte('{');
            for (int i = 0; i < keys.length; i++) {
                if (i > 0) {
                    writeByte(',');
                }
                String key = keys[i];
                JsonElement value = object.get(key);
                if (shape.encodedNames == null) {
                    writeString(key);
                    writeByte(':');
                } else {
                    writeBytes(shape.encodedNames[i]);
                }
                if (isStringUuid(key, value)) {
                    writeString(uuidAlias(value.getAsString()));
                } else {
                    write(value);
                }
            }
            writeByte('}');
        }

        private ObjectShape sortedShape(JsonObject object) {
            int size = object.entrySet().size();
            List<ObjectShape> candidates = shapes.get(size);
            if (candidates != null) {
                for (ObjectShape shape : candidates) {
                    if (shape.matches(object)) {
                        return shape;
                    }
                }
            }
            String[] insertionOrder = new String[size];
            int index = 0;
            for (Map.Entry<String, JsonElement> entry : object.entrySet()) {
                insertionOrder[index++] = entry.getKey();
            }
            String[] sorted = insertionOrder.clone();
            Arrays.sort(sorted);
            if (shapeCount < MAX_SHAPES && (candidates == null || candidates.size() < MAX_SHAPES_PER_SIZE)) {
                if (candidates == null) {
                    candidates = new ArrayList<>();
                    shapes.put(size, candidates);
                }
                ObjectShape shape = new ObjectShape(insertionOrder, sorted, true);
                candidates.add(shape);
                shapeCount++;
                return shape;
            }
            return new ObjectShape(insertionOrder, sorted, false);
        }

        private static final class ObjectShape {
            final String[] insertionOrder;
            final String[] sorted;
            final byte[][] encodedNames;

            ObjectShape(String[] insertionOrder, String[] sorted, boolean cacheEncoding) {
                this.insertionOrder = insertionOrder;
                this.sorted = sorted;
                this.encodedNames = cacheEncoding ? new byte[sorted.length][] : null;
                if (cacheEncoding) {
                    for (int i = 0; i < sorted.length; i++) encodedNames[i] = encodeFieldName(sorted[i]);
                }
            }

            boolean matches(JsonObject object) {
                int index = 0;
                for (Map.Entry<String, JsonElement> entry : object.entrySet()) {
                    if (!insertionOrder[index++].equals(entry.getKey())) {
                        return false;
                    }
                }
                return true;
            }
        }

        private static boolean isStringUuid(String key, JsonElement value) {
            return "uuid".equals(key) && value != null && value.isJsonPrimitive()
                    && value.getAsJsonPrimitive().isString();
        }

        private String uuidAlias(String uuid) {
            String alias = uuidAliases.get(uuid);
            if (alias == null) {
                UUID object = snapshotUuidObjects && isCanonicalUuid(uuid) ? UUID.fromString(uuid) : null;
                if (object != null) alias = uuidObjectAliases.get(object);
                if (alias == null) alias = "card-uuid-" + nextUuidAlias++;
                uuidAliases.put(uuid, alias);
                if (object != null && uuidObjectAliases.size() < MAX_UUID_OBJECT_ALIASES) {
                    uuidObjectAliases.put(object, alias);
                }
            }
            return alias;
        }

        private String uuidAlias(UUID uuid) {
            // Canonical UUID text and UUID objects have a bijection. Use the
            // object directly, bridging only exact canonical strings from opaque
            // JSON leaves. Noncanonical/uppercase strings retain separate aliases.
            String alias = uuidObjectAliases.get(uuid);
            if (alias == null) {
                if (uuidObjectAliases.size() < MAX_UUID_OBJECT_ALIASES) {
                    alias = "card-uuid-" + nextUuidAlias++;
                    uuidObjectAliases.put(uuid, alias);
                } else {
                    alias = uuidAlias(uuid.toString());
                }
            }
            return alias;
        }

        private static boolean isCanonicalUuid(String value) {
            if (value.length() != 36) return false;
            for (int i = 0; i < value.length(); i++) {
                char c = value.charAt(i);
                if (i == 8 || i == 13 || i == 18 || i == 23) {
                    if (c != '-') return false;
                } else if (!((c >= '0' && c <= '9') || (c >= 'a' && c <= 'f'))) {
                    return false;
                }
            }
            return true;
        }

        private void writeNumber(Number value) {
            // Only integral wrapper classes have a canonical spelling independent
            // of the source representation. Keep Float/Double/BigDecimal and
            // Gson's LazilyParsedNumber verbatim (negative zero, exponent, scale).
            if (value instanceof Integer || value instanceof Long
                    || value instanceof Short || value instanceof Byte) {
                writeInteger(value.longValue());
            } else {
                writeAscii(value.toString());
            }
        }

        private void writeInteger(long value) {
            if (value >= 0 && value < 10) { writeByte('0' + (int) value); return; }
            // Accumulate negative digits so Long.MIN_VALUE also fits.
            boolean negative = value < 0;
            long remaining = negative ? value : -value;
            int position = integerBuffer.length;
            do {
                integerBuffer[--position] = (byte) ('0' - remaining % 10);
                remaining /= 10;
            } while (remaining != 0);
            if (negative) integerBuffer[--position] = '-';
            writeBytes(integerBuffer, position, integerBuffer.length - position);
        }

        private void writeString(String value) {
            byte[] encoded = value.length() <= MAX_CACHED_STRING_LENGTH ? quotedAscii.get(value) : null;
            if (encoded != null) {
                writeBytes(encoded);
                return;
            }
            if (value.length() <= MAX_CACHED_STRING_LENGTH && quotedAscii.size() < MAX_STRINGS
                    && isPlainAscii(value)) {
                encoded = new byte[value.length() + 2];
                encoded[0] = encoded[encoded.length - 1] = '"';
                for (int i = 0; i < value.length(); i++) {
                    encoded[i + 1] = (byte) value.charAt(i);
                }
                quotedAscii.put(value, encoded);
                writeBytes(encoded);
                return;
            }
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

        private static boolean isPlainAscii(String value) {
            for (int i = 0; i < value.length(); i++) {
                char c = value.charAt(i);
                if (c < 0x20 || c > 0x7e || c == '"' || c == '\\') {
                    return false;
                }
            }
            return true;
        }

        private void writeBytes(byte[] value) {
            writeBytes(value, 0, value.length);
        }

        private void writeBytes(byte[] value, int offset, int length) {
            // Almost every schema prefix/cached string fits in the current
            // block. Avoid the generic chunk loop and Math.min on that path.
            if (length <= buffer.length - bufferLength) {
                System.arraycopy(value, offset, buffer, bufferLength, length);
                bufferLength += length;
                return;
            }
            int end = offset + length;
            while (offset < end) {
                if (bufferLength == buffer.length) {
                    flush();
                }
                int count = Math.min(end - offset, buffer.length - bufferLength);
                System.arraycopy(value, offset, buffer, bufferLength, count);
                bufferLength += count;
                offset += count;
            }
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
            int length = value.length();
            if (length <= buffer.length - bufferLength) {
                int start = bufferLength;
                for (int i = 0; i < length; i++) buffer[start + i] = (byte) value.charAt(i);
                bufferLength += length;
                return;
            }
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
