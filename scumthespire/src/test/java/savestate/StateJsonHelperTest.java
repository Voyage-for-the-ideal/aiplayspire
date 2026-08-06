package savestate;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;

public class StateJsonHelperTest {
    @Test
    public void normalizesConcreteUuidsWithoutMutatingInput() {
        JsonElement original = json("{\"card\":{\"uuid\":\"client-uuid\",\"card_id\":\"Creative AI\"},"
                + "\"label\":\"client-uuid\"}");

        JsonElement normalized = StateJsonHelper.normalizeCardUuids(original);

        assertEquals("client-uuid", original.getAsJsonObject().getAsJsonObject("card")
                .get("uuid").getAsString());
        assertEquals("card-uuid-0", normalized.getAsJsonObject().getAsJsonObject("card")
                .get("uuid").getAsString());
        assertEquals("client-uuid", normalized.getAsJsonObject().get("label").getAsString());
    }

    @Test
    public void preservesReferencesAcrossFields() {
        JsonElement client = StateJsonHelper.normalizeCardUuids(json(cardHistory("client-uuid", "client-uuid")));
        JsonElement server = StateJsonHelper.normalizeCardUuids(json(cardHistory("server-uuid", "server-uuid")));
        JsonElement brokenReference = StateJsonHelper
                .normalizeCardUuids(json(cardHistory("server-uuid", "other-server-uuid")));

        assertEquals(client, server);
        assertNotEquals(client, brokenReference);
    }

    @Test
    public void preservesCardPropertiesArrayOrderAndCardCount() {
        JsonElement base = StateJsonHelper.normalizeCardUuids(json("[{\"uuid\":\"a\",\"cost\":0},"
                + "{\"uuid\":\"b\",\"cost\":1}]"));
        JsonElement reordered = StateJsonHelper.normalizeCardUuids(json("[{\"uuid\":\"b\",\"cost\":1},"
                + "{\"uuid\":\"a\",\"cost\":0}]"));
        JsonElement changedCost = StateJsonHelper.normalizeCardUuids(json("[{\"uuid\":\"x\",\"cost\":2},"
                + "{\"uuid\":\"y\",\"cost\":1}]"));
        JsonElement missingCard = StateJsonHelper
                .normalizeCardUuids(json("[{\"uuid\":\"x\",\"cost\":0}]"));

        assertNotEquals(base, reordered);
        assertNotEquals(base, changedCost);
        assertNotEquals(base, missingCard);
    }

    private static JsonElement json(String value) {
        return new JsonParser().parse(value);
    }

    private static String cardHistory(String turnUuid, String combatUuid) {
        return "{\"cards_played_this_turn_backup\":[{\"card_id\":\"Creative AI\",\"uuid\":\""
                + turnUuid + "\"}],\"cards_played_this_combat\":[{\"card_index\":-1,"
                + "\"card_state\":{\"card_id\":\"Creative AI\",\"uuid\":\"" + combatUuid + "\"}}]}";
    }
}
