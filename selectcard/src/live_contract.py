"""Versioned, observable-only contract shared by live logging and offline export."""
import copy
import hashlib
import json
import math

TRACE_SCHEMA = "live-learning-v1"
VALUE_FIELDS = frozenset(("hp", "max_hp", "gold", "floor", "ascension", "character",
    "visible_boss", "act", "floor_in_act", "current_node", "map_nodes", "keys",
    "history", "deck", "relics", "relic_states"))
FORBIDDEN = frozenset(("seed", "seed_group", "rng", "rng_state", "next_boss", "router_plan",
    "outcome", "terminal_floor", "heart_victory", "victory", "master_deck"))


def fingerprint(value):
    return hashlib.sha256(json.dumps(value, sort_keys=True, separators=(",", ":"),
        ensure_ascii=True, allow_nan=False).encode()).hexdigest()


def file_sha256(path):
    result = hashlib.sha256()
    with open(path, "rb") as handle:
        for part in iter(lambda: handle.read(1024 * 1024), b""):
            result.update(part)
    return result.hexdigest()


def check_public(value):
    if isinstance(value, dict):
        if FORBIDDEN.intersection(value):
            raise ValueError("hidden information or labels in observable features")
        for child in value.values():
            check_public(child)
    elif isinstance(value, (list, tuple)):
        for child in value:
            check_public(child)
    elif isinstance(value, float) and not math.isfinite(value):
        raise ValueError("nonfinite observable feature")


def value_state(payload):
    if set(payload) - VALUE_FIELDS:
        raise ValueError("unknown value-state fields")
    check_public(payload)
    state = copy.deepcopy(payload)
    for name in ("hp", "max_hp", "gold", "floor", "ascension"):
        value = state.get(name)
        if isinstance(value, bool) or not isinstance(value, (int, float)) or not math.isfinite(value):
            raise ValueError("missing/invalid value feature: " + name)
    if not (0 <= state["hp"] <= state["max_hp"] and state["max_hp"] > 0 and state["gold"] >= 0
            and 0 <= state["floor"] <= 57 and 0 <= state["ascension"] <= 20
            and int(state["ascension"]) == state["ascension"]):
        raise ValueError("value feature outside supported range")
    if state.get("character") not in ("IRONCLAD", "THE_SILENT", "DEFECT", "WATCHER"):
        raise ValueError("missing/unknown character")
    for name in ("deck", "relics"):
        if not isinstance(state.get(name), list) or any(not isinstance(x, str) for x in state[name]):
            raise ValueError("invalid item list: " + name)
    # JSON roundtrip makes tuple/list representations identical without changing item order.
    return json.loads(json.dumps(state, allow_nan=False))


def value_fingerprint(payload):
    state = value_state(payload)
    # Runtime item encoding aggregates and sorts; deck display order is not a game effect.
    for name in ("deck", "relics"):
        state[name] = sorted(state[name])
    if "relic_states" in state:
        state["relic_states"] = sorted(state["relic_states"], key=lambda x: x["id"])
    return fingerprint(state)
