package savestate;

import com.google.gson.JsonArray;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class SaveStateDiffTest {
    @Test
    public void acceptsRenamedTransientCardUuidWithMatchingReferences() {
        JsonObject client = diffState("10590ee7-4b45-477f-aa38-f94a3012e5de",
                "10590ee7-4b45-477f-aa38-f94a3012e5de");
        JsonObject server = diffState("00000000-0000-0000-0000-0000000da991",
                "00000000-0000-0000-0000-0000000da991");

        assertTrue(SaveState.diff(client.toString(), server.toString()));
    }

    @Test
    public void rejectsDifferentTransientCardReferenceTopology() {
        JsonObject client = diffState("10590ee7-4b45-477f-aa38-f94a3012e5de",
                "10590ee7-4b45-477f-aa38-f94a3012e5de");
        JsonObject server = diffState("00000000-0000-0000-0000-0000000da991",
                "00000000-0000-0000-0000-0000000da992");

        assertFalse(SaveState.diff(client.toString(), server.toString()));
    }

    @Test
    public void acceptsPlayedCardFieldsResetAfterNecronomiconCopyExhausts() {
        JsonObject client = diffState("10590ee7-4b45-477f-aa38-f94a3012e5de",
                "10590ee7-4b45-477f-aa38-f94a3012e5de");
        JsonObject server = diffState("00000000-0000-0000-0000-0000000da991",
                "00000000-0000-0000-0000-0000000da991");

        setTransientPlayedCardHistoryFields(client, 5, -1, 2, 1, false);
        setTransientPlayedCardHistoryFields(server, 9, 0, 4, 0, true);

        assertTrue(SaveState.diff(client.toString(), server.toString()));
    }

    @Test
    public void stillRejectsMeaningfulPlayedCardDifferences() {
        JsonObject client = diffState("10590ee7-4b45-477f-aa38-f94a3012e5de",
                "10590ee7-4b45-477f-aa38-f94a3012e5de");
        JsonObject server = diffState("00000000-0000-0000-0000-0000000da991",
                "00000000-0000-0000-0000-0000000da991");

        client.getAsJsonArray("cards_played_this_turn_backup").get(0)
                .getAsJsonObject().addProperty("base_damage", 5);
        server.getAsJsonArray("cards_played_this_turn_backup").get(0)
                .getAsJsonObject().addProperty("base_damage", 6);

        assertFalse(SaveState.diff(client.toString(), server.toString()));
    }

    private static JsonObject diffState(String turnUuid, String combatUuid) {
        JsonObject state = new JsonObject();
        state.addProperty("screen_name", "NONE");
        state.add("previous_screen_name", JsonNull.INSTANCE);
        state.addProperty("is_screen_up", false);
        state.addProperty("turn", 1);
        state.addProperty("mantra_gained", 0);
        state.addProperty("end_turn_queued", false);
        state.addProperty("is_ending_turn", false);
        state.addProperty("lesson_learned_count", 0);
        state.addProperty("parasite_count", 0);
        state.addProperty("grid_card_select_amount", 0);
        state.addProperty("total_discarded_this_turn", 0);
        state.addProperty("rng_state", "rng");
        state.addProperty("player_state", playerState().toString());
        state.addProperty("cur_map_node_state", roomState().toString());
        state.add("cards_played_this_turn", new JsonArray());

        JsonArray turnBackup = new JsonArray();
        turnBackup.add(card(turnUuid));
        state.add("cards_played_this_turn_backup", turnBackup);

        JsonObject combatEntry = new JsonObject();
        combatEntry.addProperty("card_index", -1);
        combatEntry.add("card_state", card(combatUuid));
        JsonArray combatHistory = new JsonArray();
        combatHistory.add(combatEntry);
        state.add("cards_played_this_combat", combatHistory);

        state.add("grid_selected_cards", new JsonArray());
        state.add("drawn_cards", new JsonArray());
        state.add("hand_select_screen_state", JsonNull.INSTANCE);
        state.add("grid_card_select_screen_state", JsonNull.INSTANCE);
        state.add("card_reward_screen_state", JsonNull.INSTANCE);
        return state;
    }

    private static JsonObject playerState() {
        JsonObject creature = creature("player");
        JsonObject player = new JsonObject();
        player.addProperty("max_orbs", 0);
        player.add("orbs", new JsonArray());
        player.addProperty("hand", "");
        player.addProperty("stance", "Neutral");
        player.addProperty("creature", creature.toString());
        player.addProperty("energy_panel_total_energy", 0);
        return player;
    }

    private static JsonObject roomState() {
        JsonObject monster = new JsonObject();
        monster.addProperty("creature", creature("monster").toString());
        monster.addProperty("intent_name", "NONE");
        monster.addProperty("move_history", "");
        monster.addProperty("move_info", "none");

        JsonObject room = new JsonObject();
        room.addProperty("monster_data", monster.toString());
        return room;
    }

    private static JsonObject creature(String name) {
        JsonObject creature = new JsonObject();
        creature.addProperty("name", name);
        creature.addProperty("current_health", 1);
        creature.addProperty("current_block", 0);
        creature.addProperty("powers", "[]");
        return creature;
    }

    private static JsonObject card(String uuid) {
        JsonObject card = new JsonObject();
        card.addProperty("card_id", "Creative AI");
        card.addProperty("uuid", uuid);
        card.addProperty("cost_for_turn", 0);
        return card;
    }

    private static void setTransientPlayedCardHistoryFields(JsonObject state, int damage, int block,
                                                            int magicNumber, int costForTurn,
                                                            boolean costModifiedForTurn) {
        JsonObject turnCard = state.getAsJsonArray("cards_played_this_turn_backup").get(0)
                .getAsJsonObject();
        JsonObject combatCard = state.getAsJsonArray("cards_played_this_combat").get(0)
                .getAsJsonObject().getAsJsonObject("card_state");
        setTransientPlayedCardFields(turnCard, damage, block, magicNumber, costForTurn,
                costModifiedForTurn);
        setTransientPlayedCardFields(combatCard, damage, block, magicNumber, costForTurn,
                costModifiedForTurn);
    }

    private static void setTransientPlayedCardFields(JsonObject card, int damage, int block,
                                                     int magicNumber, int costForTurn,
                                                     boolean costModifiedForTurn) {
        card.addProperty("damage", damage);
        card.addProperty("block", block);
        card.addProperty("magic_number", magicNumber);
        card.addProperty("cost_for_turn", costForTurn);
        card.addProperty("is_cost_modified_for_turn", costModifiedForTurn);
    }
}
