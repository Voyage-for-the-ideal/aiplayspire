package battleaimod.search;

import com.google.gson.JsonElement;
import savestate.*;
import savestate.relics.RelicState;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.*;

/** Read-only projection matching the existing jsonEncode contract, without its tree.
 * Metadata is resolved once; neither snapshot values nor snapshot digests are cached.
 * A subtype overriding jsonEncode keeps its original leaf encoder, including all
 * extension fields. This intentionally does not guess schemas from Java fields.
 */
final class SaveStateFields {
    interface Reader { Object get(Object value); }

    static final class Binding {
        final String name;
        final byte[] encodedName;
        final boolean uuid;
        final Reader reader;
        final Binding[] embedded;
        final Field primitive;
        final Class<?> primitiveType;

        Binding(String name, Reader reader) { this(name, reader, null); }
        Binding(String name, Reader reader, Binding[] embedded) {
            this(name, reader, embedded, null);
        }
        Binding(String name, Reader reader, Binding[] embedded, Field primitive) {
            this.name = name;
            this.encodedName = SearchStateKey.encodeFieldName(name);
            this.uuid = "uuid".equals(name);
            this.reader = reader;
            this.embedded = embedded;
            this.primitive = primitive;
            this.primitiveType = primitive == null ? null : primitive.getType();
        }
    }

    static final class Plan {
        final Binding[] fields;
        final Method encoder;
        final boolean root;

        Plan(Binding[] fields, Method encoder, boolean root) {
            this.fields = fields;
            this.encoder = encoder;
            this.root = root;
        }

        JsonElement encode(Object value) {
            try {
                return (JsonElement) encoder.invoke(value);
            } catch (ReflectiveOperationException e) {
                throw new IllegalStateException("Snapshot leaf encoder failed: " + value.getClass(), e);
            }
        }
    }

    private static final Map<Class<?>, Binding[]> SCHEMAS = new HashMap<>();
    static {
        Binding[] creature = schema(CreatureState.class,
            "name id is_player is_bloodied draw_x draw_y dialog_x dialog_y gold display_gold " +
            "is_dying is_dead half_dead flip_horizontal flip_vertical escape_timer is_escaping " +
            "last_damage_taken hb_x hb_y hb_w hb_h current_health max_health current_block hb_alpha " +
            "anim_x anim_y reticle_alpha reticle_rendered hb health_hb powers");
        register(CreatureState.class, creature);
        register(HitboxState.class, schema(HitboxState.class,
            "x y c_x:cX c_y:cY width height hovered just_hovered click_started clicked"));
        register(PlayerState.class, schema(PlayerState.class,
            "inspect_hb chosen_class_name:chosenClass game_hand_size master_hand_size starting_max_hp:startingMaxHP " +
            "energy_manager_energy energy_manager_max_master energy_panel_total_energy is_ending_turn " +
            "viewing_relics inspect_mode damaged_this_combat title card_in_use master_deck draw_pile hand " +
            "discard_pile exhaust_pile limbo potion_slots:potionSLots stance temporary_hp relics max_orbs " +
            "master_max_orbs potions orbs orbs_channeled_this_combat",
            new Binding("creature", value -> value, creature)));
        register(CardState.class, schema(CardState.class,
            "upgraded base_damage cost cost_for_turn in_bottle_lightning in_bottle_flame in_bottle_tornado " +
            "free_to_play_once uuid is_cost_modified_for_turn is_cost_modified magic_number block " +
            "base_magic_number base_block times_upgraded exhaust is_ethereal purge_on_use misc damage " +
            "retain self_retain shuffle_back_into_draw_pile target_enum_ordinal modifiers:cardModifiers",
            new Binding("card_id", value -> StateFactories.cardIds[((CardState) value).cardIdIndex])));
        register(PotionState.class, schema(PotionState.class, "id:potionId slot"));
        register(RelicState.class, schema(RelicState.class, "relic_id counter grayscale pulse"));
        register(SaveState.CardStateContainer.class, schema(SaveState.CardStateContainer.class, "card_index card_state"));
        register(MapRoomNodeState.class, schema(MapRoomNodeState.class,
            "taken highlighted has_emerald_key is_battle_over cannot_lose elite_trigger mugged combat_event " +
            "reward_allowed reward_time skip_monster_turn wait_timer room_type phase_name:phase monster_data"));

        List<Binding> rng = new ArrayList<>();
        rng.add(binding(RngState.class, "seed", "seed"));
        rng.add(binding(RngState.class, "event_helper_chances", "eventHelperChances"));
        Field counter = field(com.megacrit.cardcrawl.random.Random.class, "counter");
        for (String key : ("monster map event merchant card treasure relic potion monster_hp ai shuffle card_random misc").split(" ")) {
            Field source = field(RngState.class, camel(key + "_rng"));
            rng.add(new Binding(key + "_rng_counter", value -> read(counter, read(source, value))));
        }
        register(RngState.class, sorted(rng));
        register(SaveState.class, schema(SaveState.class,
            "floor_num turn screen_name:screen previous_screen_name:previousScreen is_screen_up ascension_level " +
            "mantra_gained bomb_id_offset total_discarded_this_turn player_state rng_state cur_map_node_state " +
            "lastCombatMetricKey lesson_learned_count parasite_count end_turn_queued is_ending_turn " +
            "grid_card_select_amount hand_select_screen_state grid_card_select_screen_state card_reward_screen_state " +
            "cards_played_this_turn cards_played_this_turn_backup cards_played_this_combat grid_selected_cards drawn_cards"));
    }

    private static final ClassValue<Plan> PLANS = new ClassValue<Plan>() {
        @Override protected Plan computeValue(Class<?> type) {
            try {
                Method encoder = type.getMethod("jsonEncode");
                encoder.setAccessible(true);
                // Inherited serializers are fine; overridden serializers must
                // retain their own payload rather than being truncated to base fields.
                Class<?> owner = encoder.getDeclaringClass();
                return new Plan(SCHEMAS.get(owner), encoder, owner == SaveState.class);
            } catch (NoSuchMethodException e) {
                throw new IllegalStateException("Unsupported snapshot value: " + type, e);
            }
        }
    };

    static Plan plan(Class<?> type) { return PLANS.get(type); }

    /** Per-hasher metadata; handles dynamic extension registration and overrides. */
    static final class RootFields {
        private String[] extensionOrder;
        private Binding[] fields;

        Binding[] get(Binding[] base) {
            Set<String> keys = StateFactories.elementFactories.keySet();
            if (extensionOrder != null && extensionOrder.length == keys.size()) {
                int index = 0;
                boolean same = true;
                for (String key : keys) {
                    if (!extensionOrder[index++].equals(key)) { same = false; break; }
                }
                if (same) return fields;
            }
            extensionOrder = keys.toArray(new String[0]);
            TreeMap<String, Binding> merged = new TreeMap<>();
            for (Binding binding : base) merged.put(binding.name, binding);
            for (String key : extensionOrder) {
                // jsonEncode iterates the factory registry and lets extensions
                // overwrite an existing root key; reproduce that exact behavior.
                merged.put(key, new Binding(key, value -> {
                    StateElement element = ((SaveState) value).additionalElements.get(key);
                    return element.jsonEncode();
                }));
            }
            fields = merged.values().toArray(new Binding[0]);
            return fields;
        }
    }

    private static void register(Class<?> type, Binding[] fields) { SCHEMAS.put(type, fields); }

    private static Binding[] schema(Class<?> type, String specification, Binding... extra) {
        List<Binding> result = new ArrayList<>();
        for (String entry : specification.split(" ")) {
            String[] parts = entry.split(":");
            result.add(binding(type, parts[0], parts.length == 2 ? parts[1] : camel(parts[0])));
        }
        Collections.addAll(result, extra);
        return sorted(result);
    }

    private static Binding[] sorted(List<Binding> fields) {
        fields.sort(Comparator.comparing(binding -> binding.name));
        return fields.toArray(new Binding[0]);
    }

    private static Binding binding(Class<?> type, String name, String member) {
        Field source;
        try {
            source = type.getDeclaredField(member);
        } catch (NoSuchFieldException absent) {
            // Creature's hb_x etc are intentionally not camelCase.
            source = field(type, name);
        }
        source.setAccessible(true);
        Field resolved = source;
        return new Binding(name, value -> read(resolved, value), null,
                source.getType().isPrimitive() ? source : null);
    }

    private static Field field(Class<?> type, String name) {
        try {
            Field result = type.getDeclaredField(name);
            result.setAccessible(true);
            return result;
        } catch (NoSuchFieldException e) {
            throw new IllegalStateException("SaveState schema changed: " + type.getName() + "." + name, e);
        }
    }

    private static Object read(Field field, Object value) {
        try {
            return field.get(value);
        } catch (IllegalAccessException e) {
            throw new IllegalStateException("Snapshot field access failed: " + field, e);
        }
    }

    private static String camel(String name) {
        StringBuilder result = new StringBuilder();
        boolean upper = false;
        for (char c : name.toCharArray()) {
            if (c == '_') upper = true;
            else { result.append(upper ? Character.toUpperCase(c) : c); upper = false; }
        }
        return result.toString();
    }
}
