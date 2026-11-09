"""Small, strict boundary between observable game facts and training labels."""
import hashlib
import json
import math

CHARACTERS = ("IRONCLAD", "THE_SILENT", "DEFECT", "WATCHER")
FORMAT = "noncombat-policy-v1"
MAX_ITEMS = 73
MAX_CANDIDATES = 128
SCALARS = ("hp", "max_hp", "gold", "floor", "act", "ascension", "grid_selected_count", "grid_num_cards")
SCALES = (100., 100., 1000., 57., 4., 20., 10., 10.)
DECISIONS = ("CARD_REWARD", "BOSS_REWARD", "REST", "SHOP_SCREEN", "MAP", "EVENT", "CHEST", "COMBAT_REWARD", "GRID")


def digest(value):
    return hashlib.sha256(json.dumps(value, sort_keys=True, separators=(",", ":"), ensure_ascii=True).encode()).hexdigest()


def split_for(seed_group):
    """Related seeds, including other characters/ascensions, never cross splits."""
    bucket = int(digest(str(seed_group))[:16], 16) % 100
    return "train" if bucket < 80 else "val" if bucket < 90 else "test"


def observable_state(raw):
    """Allowlist projection, used identically at training and inference.

    Deliberately excludes outcome, RNG, seed, next_boss, router plans and all
    arbitrary metadata. Unknown is None, never a made-up observed zero.
    """
    fields_present = set(raw.model_fields_set) if hasattr(raw, "model_fields_set") else set(raw)
    raw = raw.model_dump(mode="json") if hasattr(raw, "model_dump") else raw
    player = raw.get("player") or {}
    state = {key: raw.get(key) for key in ("character", "visible_boss", "screen_type", "floor", "act")}
    state.update(hp=player.get("current_hp"), max_hp=player.get("max_hp"), gold=player.get("gold"),
                 ascension=raw.get("ascension_level"), grid_selected_count=raw.get("grid_selected_count"),
                 grid_num_cards=raw.get("grid_num_cards"), grid_purpose=raw.get("grid_purpose"))
    state["known"] = {key: state.get(key) is not None for key in SCALARS}
    state["field_presence"] = {}
    for key, source in (("map", "map_nodes"), ("position", "current_map_node"), ("history", "path_history"),
                        ("visible_boss", "visible_boss"), ("potions", "potions"), ("shop_inventory", "shop_inventory"),
                        ("grid_selected_cards", "grid_selected_cards"), ("keys", "keys"), ("purge_cost", "shop_purge_cost"), ("event", "event")):
        state["field_presence"][key] = source in fields_present and raw.get(source) is not None
    for field, keys in {
        "deck": ("id", "upgrades", "count", "misc", "is_bottled"), "relics": ("id", "counter"),
        "potions": ("id", "index", "is_empty"),
        "map_nodes": ("x", "y", "symbol", "is_current", "burning", "children"),
        "current_map_choices": ("x", "y", "symbol", "winged", "burning"),
        "path_history": ("floor", "selected_symbol", "resolved_symbol"),
        "shop_inventory": ("slot_id", "id", "kind", "price", "upgrades", "on_sale", "sold_out", "legal"),
        "grid_selected_cards": ("id", "upgrades"),
    }.items():
        state[field] = [{key: item[key] for key in keys if key in item} for item in (raw.get(field) or [])]
    state["path_history"] = state["path_history"][-8:]
    state["current_map_node"] = {k: v for k, v in (raw.get("current_map_node") or {}).items() if k in ("x", "y", "symbol")}
    state["keys"] = {key: (raw.get("keys") or {}).get(key) for key in ("ruby", "emerald", "sapphire")}
    state["purge_cost"] = raw.get("shop_purge_cost")
    event = raw.get("event") or {}
    state["event"] = {k: event.get(k) for k in ("id", "phase", "decision_kind", "semantics_status")}
    return state


def validate_example(example):
    if example.get("format") != FORMAT:
        raise ValueError("unsupported policy format")
    state = example["pre_state"]
    if example.get("split") != split_for(example.get("seed_group", example["run_id"])):
        raise ValueError("seed group/split mismatch")
    if state.get("character") not in CHARACTERS:
        raise ValueError("missing/unknown character")
    if state.get("ascension") is None or not 0 <= state["ascension"] <= 20:
        raise ValueError("invalid ascension")
    if example.get("decision_type") not in DECISIONS:
        raise ValueError("unsupported decision type")
    candidates = example["candidates"]
    if not 0 < len(candidates) <= MAX_CANDIDATES:
        raise ValueError("candidate overflow/empty")
    ids = [c["action_id"] for c in candidates]
    if len(set(ids)) != len(ids):
        raise ValueError("duplicate action ID")
    chosen = next((c for c in candidates if c["action_id"] == example["chosen_action_id"]), None)
    if chosen is None or not chosen.get("legal", False):
        raise ValueError("chosen action missing/illegal")
    if example.get("provenance", {}).get("status") != "exact":
        raise ValueError("unproven candidate/state reconstruction")
    forbidden = {"outcome", "seed", "seed_played", "seed_group", "rng", "rng_state", "master_deck", "next_boss", "router_plan",
                 "victory", "heart_victory", "terminal_floor", "terminal_known", "chosen_action_id", "post_state"}
    def check(value):
        if isinstance(value, dict):
            if forbidden.intersection(value):
                raise ValueError("label/hidden information in model inputs")
            for child in value.values():
                check(child)
        elif isinstance(value, list):
            for child in value:
                check(child)
        elif isinstance(value, float) and not math.isfinite(value):
            raise ValueError("nonfinite input")
    check(state)
    check(candidates)
    outcome = example.get("outcome", {})
    if outcome.get("terminal_known") not in (True, False):
        raise ValueError("missing terminal observation mask")
    if outcome.get("heart_victory") is True and (outcome.get("victory") is not True or outcome.get("terminal_known") is not True):
        raise ValueError("inconsistent Heart outcome")
    if outcome.get("terminal_known") is False and (outcome.get("victory") is not None or outcome.get("heart_victory") is not None):
        raise ValueError("censored outcome must remain unknown")
    return example
