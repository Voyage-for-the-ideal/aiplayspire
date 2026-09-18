package savestate.selectscreen;

import com.google.gson.JsonArray;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class GridCardSelectScreenStateTest {
    @Test
    public void preservesScrySelectionFlagsAcrossJsonRoundTrip() {
        JsonObject state = new JsonObject();
        state.add("selected_cards", new JsonArray());
        state.add("current_action_state", JsonNull.INSTANCE);
        state.add("action_queue", JsonNull.INSTANCE);
        state.addProperty("is_discard", false);
        state.add("group_cards", new JsonArray());
        state.add("card_queue_state", JsonNull.INSTANCE);
        state.addProperty("is_confirm_button_disabled", false);
        state.addProperty("card_select_amount", 0);
        state.addProperty("num_cards", 3);
        state.addProperty("any_number", true);
        state.addProperty("for_clarity", false);
        state.addProperty("for_upgrade", false);
        state.addProperty("for_transform", false);
        state.addProperty("can_cancel", true);
        state.addProperty("for_purge", false);

        JsonObject restored = new GridCardSelectScreenState(state).jsonEncode();

        assertEquals(3, restored.get("num_cards").getAsInt());
        assertEquals(true, restored.get("any_number").getAsBoolean());
        assertEquals(false, restored.get("for_clarity").getAsBoolean());
        assertEquals(0, restored.getAsJsonArray("selected_cards").size());
        assertEquals(0, restored.getAsJsonArray("group_cards").size());
    }
}
