package battleaimod.evaluation;

import com.google.gson.JsonArray;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import savestate.SaveState;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Builds minimal SaveState JSON for evaluator tests.  Uses GremlinFat (no extra
 * monster-state fields) and empty card/relic/orb/power lists where possible.
 * No game runtime is required: the states are only parsed, never loaded.
 */
public final class TestStateBuilder {

    public static final String MONSTER_ID = "GremlinFat";

    /** Plain monster spec for the builder. */
    public static class Monster {
        public String id = MONSTER_ID;
        public int hp;
        public int maxHp;
        public int block;
        public String intent = "BUFF";
        public int baseDamage;
        public int multiplier = 1;
        public boolean isMultiDamage;
        public byte nextMove;
        public int strength;
        // Lagavulin-specific (only set for id "Lagavulin")
        public boolean lagavulinAsleep;
        // Hexaghost-specific (only set for id "Hexaghost")
        public boolean hexActivated;
        public boolean hexBurnUpgraded;
        public int hexOrbActiveCount;
    }

    private TestStateBuilder() {
    }

    public static Monster monster(int hp) {
        Monster m = new Monster();
        m.hp = hp;
        m.maxHp = hp;
        return m;
    }

    public static Monster attacking(int hp, int damage) {
        Monster m = monster(hp);
        m.intent = "ATTACK";
        m.baseDamage = damage;
        return m;
    }

    /** A Lagavulin monster in the given sleep state (idle, no attack intent). */
    public static Monster lagavulin(int hp, boolean asleep) {
        Monster m = monster(hp);
        m.id = "Lagavulin";
        m.intent = "SLEEP";
        m.lagavulinAsleep = asleep;
        return m;
    }

    /**
     * A Hexaghost monster with the given orb counter / next move (the real
     * cycle state machine; see HexaghostProfile for the move constants).
     */
    public static Monster hexaghost(int hp, int orbActiveCount, byte nextMove,
                                    boolean burnUpgraded) {
        Monster m = monster(hp);
        m.id = "Hexaghost";
        m.intent = "ATTACK_DEBUFF";
        m.baseDamage = 6;
        m.hexActivated = true;
        m.hexOrbActiveCount = orbActiveCount;
        m.nextMove = nextMove;
        m.hexBurnUpgraded = burnUpgraded;
        return m;
    }

    public static JsonObject saveState(int playerHp, int playerMaxHp, Monster... monsters) {
        return saveState(playerHp, playerMaxHp, 0, 1, Arrays.asList(monsters));
    }

    public static JsonObject saveState(int playerHp, int playerMaxHp, int playerBlock,
                                       Monster... monsters) {
        return saveState(playerHp, playerMaxHp, playerBlock, 1, Arrays.asList(monsters));
    }

    public static JsonObject saveState(int playerHp, int playerMaxHp, int playerBlock, int turn,
                                       Monster... monsters) {
        return saveState(playerHp, playerMaxHp, playerBlock, turn, Arrays.asList(monsters));
    }

    public static JsonObject saveState(int playerHp, int playerMaxHp, int playerBlock, int turn,
                                       List<Monster> monsters) {
        JsonObject state = new JsonObject();
        state.addProperty("floor_num", 1);
        state.addProperty("turn", turn);
        state.addProperty("mantra_gained", 0);
        state.addProperty("screen_name", "NONE");
        state.add("previous_screen_name", JsonNull.INSTANCE);
        state.addProperty("is_screen_up", false);
        state.addProperty("ascension_level", 0);
        state.addProperty("bomb_id_offset", 0);
        state.addProperty("total_discarded_this_turn", 0);
        state.addProperty("lesson_learned_count", 0);
        state.addProperty("parasite_count", 0);
        state.add("cards_played_this_turn", new JsonArray());
        state.add("player_state", playerState(playerHp, playerMaxHp, playerBlock));
        state.add("cur_map_node_state", roomState(monsters));
        state.add("rng_state", rngState());
        return state;
    }

    private static JsonObject rngState() {
        JsonObject rng = new JsonObject();
        rng.addProperty("seed", 0L);
        String[] counters = {"monster_rng_counter", "map_rng_counter", "event_rng_counter",
                "merchant_rng_counter", "card_rng_counter", "treasure_rng_counter",
                "relic_rng_counter", "potion_rng_counter", "monster_hp_rng_counter",
                "ai_rng_counter", "shuffle_rng_counter", "card_random_rng_counter",
                "misc_rng_counter"};
        for (String counter : counters) {
            rng.addProperty(counter, 0);
        }
        rng.add("event_helper_chances", new JsonArray());
        return rng;
    }

    private static JsonObject hitbox() {
        JsonObject hb = new JsonObject();
        hb.addProperty("x", 0f);
        hb.addProperty("y", 0f);
        hb.addProperty("c_x", 0f);
        hb.addProperty("c_y", 0f);
        hb.addProperty("width", 100f);
        hb.addProperty("height", 100f);
        hb.addProperty("hovered", false);
        hb.addProperty("just_hovered", false);
        hb.addProperty("clicked", false);
        hb.addProperty("click_started", false);
        return hb;
    }

    private static JsonObject creature(String id, boolean isPlayer, int hp, int maxHp, int block,
                                       JsonArray powers) {
        JsonObject creature = new JsonObject();
        creature.addProperty("name", id);
        creature.addProperty("id", id);
        creature.addProperty("is_player", isPlayer);
        creature.addProperty("is_bloodied", false);
        creature.addProperty("draw_x", 0f);
        creature.addProperty("draw_y", 0f);
        creature.addProperty("dialog_x", 0f);
        creature.addProperty("dialog_y", 0f);
        creature.addProperty("gold", 0);
        creature.addProperty("display_gold", 0);
        creature.addProperty("is_dying", false);
        creature.addProperty("is_dead", false);
        creature.addProperty("half_dead", false);
        creature.addProperty("flip_horizontal", false);
        creature.addProperty("flip_vertical", false);
        creature.addProperty("escape_timer", 0f);
        creature.addProperty("is_escaping", false);
        creature.addProperty("last_damage_taken", 0);
        creature.addProperty("hb_x", 0f);
        creature.addProperty("hb_y", 0f);
        creature.addProperty("hb_w", 100f);
        creature.addProperty("hb_h", 100f);
        creature.addProperty("current_health", hp);
        creature.addProperty("max_health", maxHp);
        creature.addProperty("current_block", block);
        creature.addProperty("hb_alpha", 0f);
        creature.addProperty("anim_x", 0f);
        creature.addProperty("anim_y", 0f);
        creature.addProperty("reticle_alpha", 0f);
        creature.addProperty("reticle_rendered", false);
        creature.add("hb", hitbox());
        creature.add("health_hb", hitbox());
        creature.add("powers", powers);
        return creature;
    }

    private static JsonObject playerState(int hp, int maxHp) {
        return playerState(hp, maxHp, 0);
    }

    private static JsonObject playerState(int hp, int maxHp, int block) {
        JsonObject player = new JsonObject();
        player.add("creature", creature("The Ironclad", true, hp, maxHp, block, new JsonArray()));
        player.addProperty("chosen_class_name", "IRONCLAD");
        player.addProperty("game_hand_size", 5);
        player.addProperty("master_hand_size", 5);
        player.addProperty("starting_max_hp", maxHp);
        player.addProperty("potion_slots", 3);
        player.addProperty("temporary_hp", 0);
        player.addProperty("energy_manager_energy", 3);
        player.addProperty("energy_panel_total_energy", 3);
        player.addProperty("energy_manager_max_master", 3);
        player.addProperty("is_ending_turn", false);
        player.addProperty("viewing_relics", false);
        player.addProperty("inspect_mode", false);
        player.add("inspect_hb", JsonNull.INSTANCE);
        player.addProperty("damaged_this_combat", 0);
        player.addProperty("title", "");
        player.add("card_in_use", JsonNull.INSTANCE);
        player.add("master_deck", new JsonArray());
        player.add("draw_pile", new JsonArray());
        player.add("hand", new JsonArray());
        player.add("discard_pile", new JsonArray());
        player.add("exhaust_pile", new JsonArray());
        player.add("limbo", new JsonArray());
        player.add("relics", new JsonArray());
        player.add("potions", emptyPotionSlots());
        player.addProperty("max_orbs", 0);
        player.addProperty("master_max_orbs", 0);
        player.add("orbs", new JsonArray());
        player.add("orbs_channeled_this_combat", new JsonArray());
        player.addProperty("stance", "Neutral");
        return player;
    }

    private static JsonArray emptyPotionSlots() {
        JsonArray potions = new JsonArray();
        for (int i = 0; i < 3; i++) {
            JsonObject slot = new JsonObject();
            slot.addProperty("id", "Potion Slot");
            slot.addProperty("slot", i);
            potions.add(slot);
        }
        return potions;
    }

    private static JsonObject roomState(List<Monster> monsters) {
        JsonObject room = new JsonObject();
        room.addProperty("taken", false);
        room.addProperty("highlighted", false);
        room.addProperty("has_emerald_key", false);
        room.addProperty("is_battle_over", false);
        room.addProperty("cannot_lose", false);
        room.addProperty("elite_trigger", false);
        room.addProperty("mugged", false);
        room.addProperty("combat_event", false);
        room.addProperty("reward_allowed", false);
        room.addProperty("reward_time", false);
        room.addProperty("skip_monster_turn", false);
        room.addProperty("phase_name", "COMBAT");
        room.addProperty("room_type", "MONSTER");
        room.addProperty("wait_timer", 0f);

        JsonArray monsterData = new JsonArray();
        for (Monster monster : monsters) {
            monsterData.add(monsterState(monster));
        }
        room.add("monster_data", monsterData);
        return room;
    }

    private static JsonObject monsterState(Monster monster) {
        JsonArray powers = new JsonArray();
        if (monster.strength != 0) {
            JsonObject strength = new JsonObject();
            strength.addProperty("power_id", "Strength");
            strength.addProperty("amount", monster.strength);
            powers.add(strength);
        }

        JsonObject state = new JsonObject();
        state.add("creature", creature(monster.id, false, monster.hp, monster.maxHp, monster.block,
                powers));
        state.addProperty("death_timer", 0f);
        state.addProperty("tint_fade_out_called", false);
        state.addProperty("escaped", false);
        state.addProperty("escape_next", false);
        state.addProperty("cannot_escape", false);
        state.addProperty("type_name", "NORMAL");
        state.addProperty("next_move", 0);
        state.add("intent_hb", hitbox());
        state.addProperty("intent_name", monster.intent);
        state.addProperty("tip_intent_name", monster.intent);
        state.addProperty("intent_alpha", 0f);
        state.addProperty("intent_alpha_target", 0f);
        state.addProperty("intent_offset_x", 0f);
        state.add("move_name", JsonNull.INSTANCE);

        JsonObject moveInfo = new JsonObject();
        moveInfo.addProperty("next_move", monster.nextMove);
        moveInfo.addProperty("intent_name", monster.intent);
        moveInfo.addProperty("base_damage", monster.baseDamage);
        moveInfo.addProperty("multiplier", monster.multiplier);
        moveInfo.addProperty("is_multi_damage", monster.isMultiDamage);
        state.add("move_info", moveInfo);

        state.add("damage", new JsonArray());
        state.add("move_history", new JsonArray());

        // Monster-specific JSON fields required by their state classes
        if (monster.id.equals("GremlinNob")) {
            state.addProperty("used_bellow", false);
            state.addProperty("can_vuln", true);
        } else if (monster.id.equals("Lagavulin")) {
            state.addProperty("debuff_turn_count", 0);
            state.addProperty("idle_count", 0);
            state.addProperty("asleep", monster.lagavulinAsleep);
            state.addProperty("is_out", !monster.lagavulinAsleep);
            state.addProperty("is_out_triggered", false);
        } else if (monster.id.equals("SlimeBoss")) {
            state.addProperty("first_turn", false);
        } else if (monster.id.equals("SpikeSlime_L") || monster.id.equals("AcidSlime_L")) {
            state.addProperty("split_triggered", false);
        } else if (monster.id.equals("Hexaghost")) {
            state.addProperty("activated", monster.hexActivated);
            state.addProperty("burn_upgraded", monster.hexBurnUpgraded);
            state.addProperty("orb_active_count", monster.hexOrbActiveCount);
            JsonArray orbs = new JsonArray();
            for (int i = 0; i < 6; i++) {
                orbs.add(i < monster.hexOrbActiveCount);
            }
            state.add("active_orbs", orbs);
        }

        return state;
    }

    /** Builds a SaveState instance from the JSON (parsed, never loaded). */
    public static SaveState state(int playerHp, int playerMaxHp, Monster... monsters) {
        return state(playerHp, playerMaxHp, 0, 1, Arrays.asList(monsters));
    }

    public static SaveState state(int playerHp, int playerMaxHp, int playerBlock,
                                  Monster... monsters) {
        return state(playerHp, playerMaxHp, playerBlock, 1, Arrays.asList(monsters));
    }

    public static SaveState state(int playerHp, int playerMaxHp, int playerBlock, int turn,
                                  Monster... monsters) {
        return state(playerHp, playerMaxHp, playerBlock, turn, Arrays.asList(monsters));
    }

    public static SaveState state(int playerHp, int playerMaxHp, List<Monster> monsters) {
        return state(playerHp, playerMaxHp, 0, 1, monsters);
    }

    public static SaveState state(int playerHp, int playerMaxHp, int playerBlock, int turn,
                                  List<Monster> monsters) {
        return new SaveState(saveState(playerHp, playerMaxHp, playerBlock, turn, monsters));
    }

    /** Deep-copies a monster list so tests can tweak individual enemies. */
    public static List<Monster> copy(List<Monster> monsters) {
        List<Monster> result = new ArrayList<>(monsters.size());
        for (Monster monster : monsters) {
            Monster copy = new Monster();
            copy.id = monster.id;
            copy.hp = monster.hp;
            copy.maxHp = monster.maxHp;
            copy.block = monster.block;
            copy.intent = monster.intent;
            copy.baseDamage = monster.baseDamage;
            copy.multiplier = monster.multiplier;
            copy.isMultiDamage = monster.isMultiDamage;
            copy.nextMove = monster.nextMove;
            copy.strength = monster.strength;
            copy.lagavulinAsleep = monster.lagavulinAsleep;
            copy.hexActivated = monster.hexActivated;
            copy.hexBurnUpgraded = monster.hexBurnUpgraded;
            copy.hexOrbActiveCount = monster.hexOrbActiveCount;
            result.add(copy);
        }
        return result;
    }
}
