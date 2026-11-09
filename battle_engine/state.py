"""Versioned public projection shared with the Java guard and native importer.

This projection is a divergence detector, not a complete simulation-state hash.
"""
import json

RNG_NAMES = ("ai_rng_counter", "card_random_rng_counter", "misc_rng_counter",
             "monster_hp_rng_counter", "potion_rng_counter", "shuffle_rng_counter")
CARD_ATTRIBUTES = ("base_damage", "base_block", "base_magic_number", "misc", "exhaust",
                   "is_ethereal", "self_retain", "purge_on_use", "shuffle_back_into_draw_pile",
                   "target_enum_ordinal")


def card_attributes(state):
    return {c["uuid"]: {k: c[k] for k in CARD_ATTRIBUTES if k in c}
            for pile in ("hand", "draw_pile", "discard_pile", "exhaust_pile")
            for c in state["player_state"][pile] if c.get("uuid") is not None}


def normalize(value):
    if isinstance(value, str) and value.lstrip().startswith(("{", "[")):
        return normalize(json.loads(value))
    if isinstance(value, dict):
        return {k: normalize(v) for k, v in value.items()}
    if isinstance(value, list):
        return [normalize(v) for v in value]
    return value


def load(path):
    return normalize(json.loads(path.read_text(encoding="utf-8-sig")))


def projection(state):
    p = state["player_state"]
    creature = p["creature"]
    result = dict(format=1, turn=state["turn"], hp=creature["current_health"],
                  max_hp=creature["max_health"], block=creature["current_block"],
                  energy=p["energy_panel_total_energy"])
    for pile in ("hand", "draw_pile", "discard_pile", "exhaust_pile"):
        result[pile] = [dict(id=c["card_id"], upgrades=c.get("times_upgraded", int(c.get("upgraded", False))),
                             cost=c["cost"], turn_cost=c["cost_for_turn"],
                             free=c.get("free_to_play_once", False), retain=c.get("retain", False))
                        for c in p[pile]]
    result["monsters"] = [dict(id=m["creature"]["id"], hp=m["creature"]["current_health"],
                               max_hp=m["creature"]["max_health"], block=m["creature"]["current_block"])
                          for m in state["cur_map_node_state"]["monster_data"]]
    result["potions"] = [v["id"] for v in p["potions"]]
    result["orbs"] = [dict(id=o["lookup_key"], dark=o["evoke_amount"] if o["lookup_key"] == "Dark" else 0)
                       for o in p["orbs"]]
    result["rng"] = {name: state["rng_state"][name] for name in RNG_NAMES}
    return result


def context(state):
    """Source-only guard, including identity and opaque SaveState power details."""
    p = state["player_state"]
    monsters = state["cur_map_node_state"]["monster_data"]
    result = dict(seed=state["rng_state"]["seed"], floor=state["floor_num"], role=p["chosen_class_name"],
                stance=p.get("stance", "Neutral"),
                card_attributes=card_attributes(state),
                hand_uuids=[c.get("uuid") for c in p["hand"]], player_powers=p["creature"]["powers"],
                monster_powers=[m["creature"]["powers"] for m in monsters],
                monster_moves=[m["next_move"] for m in monsters],
                relics=[dict(id=r["relic_id"], counter=r["counter"]) for r in p["relics"]])
    if state.get("is_screen_up"):
        selection = dict(screen=state["screen_name"], limbo=p["limbo"], card_in_use=p["card_in_use"])
        for key in ("hand_select_screen_state", "grid_card_select_screen_state", "card_reward_screen_state"):
            if state.get(key) is not None:
                selection[key] = state[key]
        result["selection"] = selection
    return result
