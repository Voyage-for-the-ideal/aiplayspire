package savestate;

import com.google.gson.*;
import com.megacrit.cardcrawl.cards.DamageInfo;
import savestate.actions.ActionState;
import savestate.actions.CurrentActionState;
import savestate.powers.PowerState;

import java.lang.reflect.Type;

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
