package savestate;

import com.google.gson.JsonArray;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import org.junit.Test;
import java.util.HashMap;
import java.util.Map;
import static org.junit.Assert.*;

/** Gson drops null object members inside action envelopes, unlike the top-level card JSON. */
public class CardStateOptionalModifiersTest {
    @Test public void omittedModifiersMatchExplicitNull() {
        String[] oldIds = StateFactories.cardIds;
        Map<String, Integer> oldMap = new HashMap<>(StateFactories.cardIdToIndexMap);
        try {
            StateFactories.cardIds = new String[]{"RegressionCard"};
            StateFactories.cardIdToIndexMap.put("RegressionCard", 0);
            JsonObject omitted = card();
            JsonObject explicit = card(); explicit.add("modifiers", JsonNull.INSTANCE);
            assertEquals(new CardState(explicit).jsonEncode(), new CardState(omitted).jsonEncode());
            assertEquals(new CardState(explicit).jsonEncode(), new CardState(omitted.toString()).jsonEncode());
        } finally {
            StateFactories.cardIds = oldIds;
            StateFactories.cardIdToIndexMap.clear(); StateFactories.cardIdToIndexMap.putAll(oldMap);
        }
    }

    @Test public void emptyModifierArrayStillRoundTrips() {
        String[] oldIds = StateFactories.cardIds;
        Map<String, Integer> oldMap = new HashMap<>(StateFactories.cardIdToIndexMap);
        try {
            StateFactories.cardIds = new String[]{"RegressionCard"};
            StateFactories.cardIdToIndexMap.put("RegressionCard", 0);
            JsonObject value = card(); value.add("modifiers", new JsonArray());
            assertTrue(new CardState(value).jsonEncode().get("modifiers").isJsonArray());
        } finally {
            StateFactories.cardIds = oldIds;
            StateFactories.cardIdToIndexMap.clear(); StateFactories.cardIdToIndexMap.putAll(oldMap);
        }
    }

    private JsonObject card() {
        JsonObject out = new JsonObject(); out.addProperty("card_id", "RegressionCard");
        out.addProperty("uuid", "00000000-0000-0000-0000-000000000001");
        for (String name : new String[]{"upgraded", "in_bottle_lightning", "in_bottle_tornado", "in_bottle_flame",
                "free_to_play_once", "is_cost_modified_for_turn", "is_cost_modified", "exhaust", "purge_on_use",
                "is_ethereal", "retain", "self_retain", "shuffle_back_into_draw_pile"}) { out.addProperty(name, false); }
        for (String name : new String[]{"base_damage", "cost", "cost_for_turn", "magic_number", "block",
                "base_magic_number", "base_block", "times_upgraded", "misc", "damage"}) { out.addProperty(name, 0); }
        return out;
    }
}
