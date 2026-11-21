package battleaimod.search;

import battleaimod.evaluation.TestStateBuilder;
import com.google.gson.*;
import org.junit.Test;
import savestate.*;

import java.util.*;

import static org.junit.Assert.*;

public class DirectStateKeyTest {
    private JsonObject fixture() {
        JsonObject json = TestStateBuilder.saveState(50, 80, 0, TestStateBuilder.attacking(90, 12));
        for (String key : Arrays.asList("cards_played_this_turn_backup", "cards_played_this_combat", "grid_selected_cards", "drawn_cards")) {
            json.add(key, new JsonArray());
        }
        JsonObject player = json.getAsJsonObject("player_state");
        player.getAsJsonArray("hand").add(card("Strike_R", 1));
        player.getAsJsonArray("hand").add(card("Defend_R", 2));
        player.getAsJsonArray("draw_pile").add(card("Strike_R", 3));
        player.getAsJsonArray("draw_pile").add(card("Defend_R", 4));
        return json;
    }

    private JsonObject card(String id, int instance) {
        if (!StateFactories.cardIdToIndexMap.containsKey(id)) {
            List<String> ids = new ArrayList<>();
            if (StateFactories.cardIds != null) Collections.addAll(ids, StateFactories.cardIds);
            StateFactories.cardIdToIndexMap.put(id, ids.size());
            ids.add(id);
            StateFactories.cardIds = ids.toArray(new String[0]);
        }
        JsonObject card = new JsonObject();
        card.addProperty("card_id", id);
        card.addProperty("uuid", new UUID(0, instance).toString());
        for (String field : ("upgraded in_bottle_lightning in_bottle_flame in_bottle_tornado free_to_play_once " +
                "is_cost_modified_for_turn is_cost_modified exhaust purge_on_use is_ethereal retain self_retain " +
                "shuffle_back_into_draw_pile").split(" ")) card.addProperty(field, false);
        for (String field : ("magic_number base_magic_number times_upgraded misc target_enum_ordinal").split(" ")) card.addProperty(field, 0);
        card.addProperty("cost", 1); card.addProperty("cost_for_turn", 1);
        card.addProperty("damage", 6); card.addProperty("base_damage", 6);
        card.addProperty("block", 5); card.addProperty("base_block", 5);
        card.add("modifiers", JsonNull.INSTANCE);
        return card;
    }

    private SearchStateKey checked(JsonObject json) { return checked(new SaveState(json)); }

    private SearchStateKey checked(SaveState state) {
        SearchStateKey expected = SearchStateKey.fromJson(state.jsonEncode());
        SearchStateKey actual = SearchStateKey.fromSaveState(state);
        assertEquals(expected, actual);
        return actual;
    }

    @Test public void matchesFullCanonicalStateAndReadsMutableFieldsAgain() {
        SaveState state = new SaveState(fixture());
        SearchStateKey original = checked(state);
        state.turn++;
        assertNotEquals(original, checked(state));
        state.turn--;
        assertEquals(original, checked(state));
    }

    @Test public void retainsEveryRngCounterAndSeed() {
        JsonObject json = fixture();
        SearchStateKey original = checked(json);
        List<String> keys = new ArrayList<>();
        for (Map.Entry<String, JsonElement> entry : json.getAsJsonObject("rng_state").entrySet()) keys.add(entry.getKey());
        for (String key : keys) {
            if (key.equals("event_helper_chances")) continue;
            JsonObject changed = new JsonParser().parse(json.toString()).getAsJsonObject();
            JsonObject rng = changed.getAsJsonObject("rng_state");
            rng.addProperty(key, rng.get(key).getAsLong() + 1);
            assertNotEquals(key, original, checked(changed));
        }
        json.getAsJsonObject("rng_state").getAsJsonArray("event_helper_chances").add(.25f);
        assertNotEquals(original, checked(json));
    }

    @Test public void directIntegerEncodingRetainsExtremeSeedsAndSignedFields() {
        for (long seed : new long[]{Long.MIN_VALUE, Long.MAX_VALUE, -10L, -1L, 0L, 10L}) {
            JsonObject json = fixture();
            json.getAsJsonObject("rng_state").addProperty("seed", seed);
            json.addProperty("total_discarded_this_turn", Integer.MIN_VALUE);
            json.getAsJsonObject("player_state").getAsJsonObject("creature")
                    .addProperty("gold", Integer.MAX_VALUE);
            checked(json);
        }
    }

    @Test public void typedPrimitiveFieldsRetainFloatSpellingAndBooleanChanges() {
        for (float number : new float[]{-0.0f, 0.125f, Float.MIN_VALUE, Float.MAX_VALUE}) {
            JsonObject json = fixture();
            JsonObject creature = json.getAsJsonObject("player_state").getAsJsonObject("creature");
            creature.addProperty("draw_x", number);
            creature.addProperty("is_bloodied", true);
            SearchStateKey original = checked(json);
            creature.addProperty("is_bloodied", false);
            assertNotEquals(original, checked(json));
        }
    }

    @Test public void retainsPileOrderCardInstanceFieldsAndHistoryOrder() {
        JsonObject json = fixture();
        SearchStateKey original = checked(json);
        JsonArray pile = json.getAsJsonObject("player_state").getAsJsonArray("draw_pile");
        JsonElement first = pile.remove(0); pile.add(first);
        assertNotEquals(original, checked(json));
        json = fixture();
        json.getAsJsonObject("player_state").getAsJsonArray("hand").get(0).getAsJsonObject().addProperty("cost_for_turn", 0);
        assertNotEquals(original, checked(json));
        json = fixture();
        JsonArray history = json.getAsJsonArray("cards_played_this_combat");
        for (int i = 0; i < 2; i++) {
            JsonObject entry = new JsonObject(); entry.addProperty("card_index", i);
            entry.add("card_state", JsonNull.INSTANCE); history.add(entry);
        }
        SearchStateKey ordered = checked(json);
        first = history.remove(0); history.add(first);
        assertNotEquals(ordered, checked(json));
    }

    @Test public void retainsUuidReferenceTopologyWithoutDependingOnConcreteIds() {
        JsonObject json = fixture();
        json.getAsJsonArray("cards_played_this_turn_backup").add(card("Strike_R", 1));
        SearchStateKey original = checked(json);
        json.getAsJsonObject("player_state").getAsJsonArray("hand").get(0).getAsJsonObject()
            .addProperty("uuid", new UUID(0, 101).toString());
        json.getAsJsonArray("cards_played_this_turn_backup").get(0).getAsJsonObject()
            .addProperty("uuid", new UUID(0, 101).toString());
        assertEquals(original, checked(json));
        json.getAsJsonArray("cards_played_this_turn_backup").get(0).getAsJsonObject()
            .addProperty("uuid", new UUID(0, 102).toString());
        assertNotEquals(original, checked(json));
    }

    @Test public void uuidObjectAliasesMatchOpaqueStringReferencesInBothOrders() {
        JsonObject json = fixture();
        String uuid = new UUID(0, 0xabcdefL).toString();
        JsonObject first = json.getAsJsonObject("player_state").getAsJsonArray("hand")
                .get(0).getAsJsonObject();
        first.addProperty("uuid", uuid);
        json.getAsJsonObject("player_state").getAsJsonArray("master_deck").add(first);
        SaveState state = new SaveState(json);
        HashMap<String, StateElement.ElementFactories> saved = new HashMap<>(StateFactories.elementFactories);
        JsonObject payload = new JsonObject(); payload.addProperty("uuid", uuid);
        StateElement extra = new StateElement() {
            public String encode() { return jsonEncode().toString(); }
            public JsonObject jsonEncode() { return payload; }
            public void restore() { }
        };
        try {
            for (String key : Arrays.asList("aaa_uuid_reference", "zzz_uuid_reference")) {
                StateFactories.elementFactories.put(key, new StateElement.ElementFactories(() -> extra, text -> extra));
                state.additionalElements.put(key, extra);
                SearchStateKey original = checked(state);
                for (String opaque : Arrays.asList(uuid.toUpperCase(Locale.ROOT), "0-0-0-0-abcdef", "not-a-uuid")) {
                    payload.addProperty("uuid", opaque);
                    assertNotEquals(original, checked(state));
                }
                payload.addProperty("uuid", uuid);
                assertEquals(original, checked(state));
            }
        } finally {
            StateFactories.elementFactories.clear(); StateFactories.elementFactories.putAll(saved);
        }
    }

    @Test public void uuidObjectAliasCacheResetsAndSaturatesWithoutChangingKeys() {
        JsonObject json = fixture();
        JsonArray cards = new JsonArray();
        for (int i = 0; i < 530; i++) {
            JsonObject card = card("Strike_R", i + 100);
            card.addProperty("misc", i); cards.add(card);
        }
        JsonObject player = json.getAsJsonObject("player_state");
        player.add("hand", cards);
        player.getAsJsonArray("master_deck").add(cards.get(0));
        player.getAsJsonArray("master_deck").add(cards.get(529));
        SaveState state = new SaveState(json);
        SearchStateKey original = checked(state);
        CardState first = state.playerState.hand[0];
        state.playerState.hand[0] = state.playerState.hand[1];
        state.playerState.hand[1] = first;
        assertNotEquals(original, checked(state));
        state.playerState.hand[1] = state.playerState.hand[0];
        state.playerState.hand[0] = first;
        assertEquals(original, checked(state));
    }

    @Test public void retainsAllHistoryAndSelectionLists() {
        for (String key : Arrays.asList("grid_selected_cards", "drawn_cards")) {
            JsonObject json = fixture();
            SearchStateKey original = checked(json);
            JsonObject entry = new JsonObject(); entry.addProperty("card_index", 0);
            entry.add("card_state", JsonNull.INSTANCE); json.getAsJsonArray(key).add(entry);
            assertNotEquals(key, original, checked(json));
        }
        JsonObject json = fixture();
        SearchStateKey original = checked(json);
        json.getAsJsonArray("cards_played_this_turn").add(0);
        assertNotEquals(original, checked(json));
    }

    @Test public void overriddenLeafSerializerRetainsAdditionalFields() {
        SaveState state = new SaveState(fixture());
        SearchStateKey original = checked(state);
        state.playerState.hand[0] = new ExtendedCard(card("Strike_R", 1));
        assertNotEquals(original, checked(state));
    }

    private static final class ExtendedCard extends CardState {
        ExtendedCard(JsonObject json) { super(json); }
        @Override public JsonObject jsonEncode() {
            JsonObject json = super.jsonEncode(); json.addProperty("mod_counter", 91); return json;
        }
    }

    @Test public void dynamicExtensionFieldsAndRootOverridesRemainComplete() {
        SaveState state = new SaveState(fixture());
        SearchStateKey original = checked(state);
        HashMap<String, StateElement.ElementFactories> saved = new HashMap<>(StateFactories.elementFactories);
        try {
            StateElement extra = new StateElement() {
                public String encode() { return jsonEncode().toString(); }
                public JsonObject jsonEncode() { JsonObject json = new JsonObject(); json.addProperty("uuid", "custom"); json.addProperty("counter", 7); return json; }
                public void restore() { }
            };
            for (String key : Arrays.asList("extension_test", "uuid", "turn", "\\\"\n雪\u2028", "<>&='")) {
                StateFactories.elementFactories.put(key, new StateElement.ElementFactories(() -> extra, text -> extra));
                state.additionalElements.put(key, extra);
                assertNotEquals(original, checked(state));
            }
        } finally {
            StateFactories.elementFactories.clear(); StateFactories.elementFactories.putAll(saved);
        }
        assertEquals(original, checked(state));
    }
}
