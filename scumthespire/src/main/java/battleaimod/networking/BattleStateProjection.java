package battleaimod.networking;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

/** Wire projection v1; kept in sync with battle_engine/state.py and src/Snapshot.cpp.
 * Deliberately a public divergence detector, not a full-state equivalence claim. */
public final class BattleStateProjection {
    private BattleStateProjection() { }

    public static JsonElement normalize(JsonElement value) {
        if (value.isJsonPrimitive() && value.getAsJsonPrimitive().isString()) {
            String text = value.getAsString().trim();
            if (text.startsWith("{") || text.startsWith("[")) {
                return normalize(new JsonParser().parse(text));
            }
        }
        if (value.isJsonArray()) {
            JsonArray out = new JsonArray();
            for (JsonElement entry : value.getAsJsonArray()) { out.add(normalize(entry)); }
            return out;
        }
        if (value.isJsonObject()) {
            JsonObject out = new JsonObject();
            for (java.util.Map.Entry<String, JsonElement> entry : value.getAsJsonObject().entrySet()) {
                out.add(entry.getKey(), normalize(entry.getValue()));
            }
            return out;
        }
        return value;
    }

    public static JsonObject fromSnapshot(JsonObject snapshot) {
        JsonObject s = normalize(snapshot).getAsJsonObject();
        JsonObject p = s.getAsJsonObject("player_state"), c = p.getAsJsonObject("creature");
        JsonObject out = new JsonObject();
        out.addProperty("format", 1);
        out.add("turn", s.get("turn")); out.add("hp", c.get("current_health"));
        out.add("max_hp", c.get("max_health")); out.add("block", c.get("current_block"));
        out.add("energy", p.get("energy_panel_total_energy"));
        for (String pile : new String[]{"hand", "draw_pile", "discard_pile", "exhaust_pile"}) {
            JsonArray cards = new JsonArray();
            for (JsonElement element : p.getAsJsonArray(pile)) {
                JsonObject card = element.getAsJsonObject(), v = new JsonObject();
                v.add("id", card.get("card_id"));
                v.addProperty("upgrades", card.has("times_upgraded") ? card.get("times_upgraded").getAsInt()
                        : card.has("upgraded") && card.get("upgraded").getAsBoolean() ? 1 : 0);
                v.add("cost", card.get("cost")); v.add("turn_cost", card.get("cost_for_turn"));
                v.addProperty("free", card.has("free_to_play_once") && card.get("free_to_play_once").getAsBoolean());
                v.addProperty("retain", card.has("retain") && card.get("retain").getAsBoolean());
                cards.add(v);
            }
            out.add(pile, cards);
        }
        JsonArray monsters = new JsonArray();
        for (JsonElement m : s.getAsJsonObject("cur_map_node_state").getAsJsonArray("monster_data")) {
            JsonObject mc = m.getAsJsonObject().getAsJsonObject("creature"), v = new JsonObject();
            v.add("id", mc.get("id")); v.add("hp", mc.get("current_health"));
            v.add("max_hp", mc.get("max_health")); v.add("block", mc.get("current_block"));
            monsters.add(v);
        }
        out.add("monsters", monsters);
        JsonArray potions = new JsonArray();
        for (JsonElement potion : p.getAsJsonArray("potions")) { potions.add(potion.getAsJsonObject().get("id")); }
        out.add("potions", potions);
        JsonArray orbs = new JsonArray();
        for (JsonElement orb : p.getAsJsonArray("orbs")) {
            JsonObject o = orb.getAsJsonObject(), v = new JsonObject();
            v.add("id", o.get("lookup_key"));
            v.addProperty("dark", "Dark".equals(o.get("lookup_key").getAsString()) ? o.get("evoke_amount").getAsInt() : 0);
            orbs.add(v);
        }
        out.add("orbs", orbs);
        JsonObject rng = new JsonObject();
        for (String name : new String[]{"ai_rng_counter", "card_random_rng_counter", "misc_rng_counter",
                "monster_hp_rng_counter", "potion_rng_counter", "shuffle_rng_counter"}) {
            rng.add(name, s.getAsJsonObject("rng_state").get(name));
        }
        out.add("rng", rng);
        return out;
    }

    public static JsonObject context(JsonObject snapshot) {
        JsonObject s = normalize(snapshot).getAsJsonObject(), p = s.getAsJsonObject("player_state");
        JsonObject out = new JsonObject();
        if (s.has("is_screen_up") && s.get("is_screen_up").getAsBoolean()) {
            JsonObject selection = new JsonObject();
            selection.add("screen", s.get("screen_name")); selection.add("limbo", p.get("limbo"));
            selection.add("card_in_use", p.get("card_in_use"));
            for (String key : new String[]{"hand_select_screen_state", "grid_card_select_screen_state", "card_reward_screen_state"}) {
                if (s.has(key) && !s.get(key).isJsonNull()) selection.add(key, s.get(key));
            }
            out.add("selection", selection);
        }
        out.add("seed", s.getAsJsonObject("rng_state").get("seed"));
        out.add("floor", s.get("floor_num")); out.add("role", p.get("chosen_class_name"));
        out.addProperty("stance", p.has("stance") ? p.get("stance").getAsString() : "Neutral");
        JsonObject attributes = new JsonObject();
        for (String pile : new String[]{"hand", "draw_pile", "discard_pile", "exhaust_pile"}) {
            for (JsonElement entry : p.getAsJsonArray(pile)) {
                JsonObject card = entry.getAsJsonObject();
                if (!card.has("uuid") || card.get("uuid").isJsonNull()) continue;
                JsonObject details = new JsonObject();
                for (String field : new String[]{"base_damage", "base_block", "base_magic_number", "misc", "exhaust",
                        "is_ethereal", "self_retain", "purge_on_use", "shuffle_back_into_draw_pile", "target_enum_ordinal"}) {
                    if (card.has(field)) details.add(field, card.get(field));
                }
                attributes.add(card.get("uuid").getAsString(), details);
            }
        }
        out.add("card_attributes", attributes);
        JsonArray uuids = new JsonArray(), powers = new JsonArray(), moves = new JsonArray(), relics = new JsonArray();
        for (JsonElement card : p.getAsJsonArray("hand")) { uuids.add(card.getAsJsonObject().get("uuid")); }
        for (JsonElement entry : s.getAsJsonObject("cur_map_node_state").getAsJsonArray("monster_data")) {
            JsonObject m = entry.getAsJsonObject();
            powers.add(m.getAsJsonObject("creature").get("powers")); moves.add(m.get("next_move"));
        }
        for (JsonElement entry : p.getAsJsonArray("relics")) {
            JsonObject r = entry.getAsJsonObject(), v = new JsonObject();
            v.add("id", r.get("relic_id")); v.add("counter", r.get("counter")); relics.add(v);
        }
        out.add("hand_uuids", uuids); out.add("player_powers", p.getAsJsonObject("creature").get("powers"));
        out.add("monster_powers", powers); out.add("monster_moves", moves); out.add("relics", relics);
        return out;
    }
}
