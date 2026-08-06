package savestate;

import com.google.gson.*;
import com.megacrit.cardcrawl.cards.DamageInfo;
import savestate.actions.ActionState;
import savestate.actions.CurrentActionState;
import savestate.powers.PowerState;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class StateJsonHelper {
    private static final Gson GSON = new GsonBuilder()
            .registerTypeAdapter(CardState.class, new JsonSerializer<CardState>() {
                @Override
                public JsonElement serialize(CardState src, Type typeOfSrc, JsonSerializationContext context) {
                    return src == null ? JsonNull.INSTANCE : src.jsonEncode();
                }
            })
            .registerTypeAdapter(CardState.class, new JsonDeserializer<CardState>() {
                @Override
                public CardState deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context) {
                    return isNull(json) ? null : CardState.forJson(json.getAsJsonObject());
                }
            })
            .registerTypeAdapter(PowerState.class, new JsonSerializer<PowerState>() {
                @Override
                public JsonElement serialize(PowerState src, Type typeOfSrc, JsonSerializationContext context) {
                    return src == null ? JsonNull.INSTANCE : src.jsonEncode();
                }
            })
            .registerTypeAdapter(PowerState.class, new JsonDeserializer<PowerState>() {
                @Override
                public PowerState deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context) {
                    return isNull(json) ? null : PowerState.forJsonObject(json.getAsJsonObject());
                }
            })
            .registerTypeAdapter(DamageInfo.class, new JsonSerializer<DamageInfo>() {
                @Override
                public JsonElement serialize(DamageInfo src, Type typeOfSrc, JsonSerializationContext context) {
                    return src == null ? JsonNull.INSTANCE : new DamageInfoState(src).jsonEncode();
                }
            })
            .registerTypeAdapter(DamageInfo.class, new JsonDeserializer<DamageInfo>() {
                @Override
                public DamageInfo deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context) {
                    return isNull(json) ? null : new DamageInfoState(json.getAsJsonObject()).loadDamageInfo();
                }
            })
            .create();

    private StateJsonHelper() {
    }

    public static JsonObject actionStateToJson(ActionState actionState) {
        return typedStateToJson(actionState);
    }

    public static ActionState actionStateFromJson(JsonElement json) {
        return typedStateFromJson(json, ActionState.class);
    }

    public static JsonObject currentActionStateToJson(CurrentActionState actionState) {
        return typedStateToJson(actionState);
    }

    public static CurrentActionState currentActionStateFromJson(JsonElement json) {
        return typedStateFromJson(json, CurrentActionState.class);
    }

    public static JsonElement normalizeCardUuids(JsonElement json) {
        return normalizeCardUuids(json, new HashMap<String, String>(), new int[]{0});
    }

    private static JsonElement normalizeCardUuids(JsonElement json, Map<String, String> aliases,
                                                   int[] nextAlias) {
        if (isNull(json)) {
            return JsonNull.INSTANCE;
        }

        if (json.isJsonArray()) {
            JsonArray result = new JsonArray();
            for (JsonElement element : json.getAsJsonArray()) {
                result.add(normalizeCardUuids(element, aliases, nextAlias));
            }
            return result;
        }

        if (json.isJsonObject()) {
            JsonObject source = json.getAsJsonObject();
            JsonObject result = new JsonObject();
            List<String> keys = new ArrayList<>();
            for (Map.Entry<String, JsonElement> entry : source.entrySet()) {
                keys.add(entry.getKey());
            }
            Collections.sort(keys);

            for (String key : keys) {
                JsonElement value = source.get(key);
                if ("uuid".equals(key) && value != null && value.isJsonPrimitive()
                        && value.getAsJsonPrimitive().isString()) {
                    String uuid = value.getAsString();
                    String alias = aliases.get(uuid);
                    if (alias == null) {
                        alias = "card-uuid-" + nextAlias[0]++;
                        aliases.put(uuid, alias);
                    }
                    result.addProperty(key, alias);
                } else {
                    result.add(key, normalizeCardUuids(value, aliases, nextAlias));
                }
            }
            return result;
        }

        // JsonPrimitive is immutable, so sharing it does not compromise the copied tree.
        return json;
    }

    private static JsonObject typedStateToJson(Object state) {
        JsonObject result = new JsonObject();
        result.addProperty("state_class", state.getClass().getName());
        result.add("state", GSON.toJsonTree(state));
        return result;
    }

    private static <T> T typedStateFromJson(JsonElement json, Class<T> expectedType) {
        if (isNull(json)) {
            return null;
        }

        JsonObject stateJson = json.getAsJsonObject();
        try {
            Class<?> stateClass = Class.forName(stateJson.get("state_class").getAsString());
            Object state = GSON.fromJson(stateJson.get("state"), stateClass);
            return expectedType.cast(state);
        } catch (ClassNotFoundException e) {
            throw new IllegalStateException("Unknown serialized state class " + stateJson.get("state_class"), e);
        }
    }

    private static boolean isNull(JsonElement json) {
        return json == null || json.isJsonNull();
    }
}
