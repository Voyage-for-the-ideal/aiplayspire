"""Frozen historical input projection, also applied to live inference."""
import copy

RUN_PROFILE = "run-context-v1"
LIVE_PROFILE = "live-observable-v1"
MIXED_PROFILE = "mixed-observable-v1"
PROFILES = (LIVE_PROFILE, RUN_PROFILE, MIXED_PROFILE)
CONTEXT_FIELDS = ("map", "position", "history", "visible_boss")
OPTIONAL_FIELDS = ("potions", "shop_inventory", "grid_selected_cards", "keys", "purge_cost", "event")


def project_state(state, profile):
    if profile not in PROFILES:
        raise ValueError("unsupported policy input profile")
    result = copy.deepcopy(state)
    if profile == LIVE_PROFILE:
        return result
    if profile == MIXED_PROFILE:
        result["input_profile"] = MIXED_PROFILE
        known = result.setdefault("known", {})
        presence = result.pop("field_presence", {})
        known.setdefault("map", presence.get("map", bool(result.get("map_nodes"))))
        known.setdefault("position", presence.get("position", bool(result.get("current_map_node"))))
        known.setdefault("history", presence.get("history", bool(result.get("path_history"))))
        known.setdefault("visible_boss", presence.get("visible_boss", True) and result.get("visible_boss") not in (None, "UNKNOWN", "UNKNOWN_BOSS", "NO_BOSS"))
        for field in OPTIONAL_FIELDS:
            known.setdefault(field, presence.get(field, field in result and result[field] is not None))
            if not known[field]:
                result[field] = {} if field in ("keys", "event") else [] if field in ("potions", "shop_inventory", "grid_selected_cards") else None
        if not known["map"]:
            result["map_nodes"] = []
        if not known["position"]:
            result["current_map_node"] = {}
            result["current_map_choices"] = []
            for node in result.get("map_nodes", []):
                node["is_current"] = False
        if not known["history"]:
            result["path_history"] = []
        if not known["visible_boss"]:
            result["visible_boss"] = None
        return result
    result["input_profile"] = RUN_PROFILE
    known = result.setdefault("known", {})
    known.setdefault("map", bool(result.get("map_nodes")))
    node = result.get("current_map_node") or {}
    known.setdefault("position", bool(known["map"] and 0 <= node.get("y", -1) < 15 and 0 <= node.get("x", -1) < 7))
    known.setdefault("history", True)
    known.setdefault("visible_boss", result.get("visible_boss") not in (None, "UNKNOWN", "UNKNOWN_BOSS", "NO_BOSS"))
    if not known["map"]:
        result["map_nodes"] = []
    if not known["position"]:
        result["current_map_node"] = {}
        result["current_map_choices"] = []
        for node in result.get("map_nodes", []):
            node["is_current"] = False
    if not known["history"]:
        result["path_history"] = []
    if not known["visible_boss"]:
        result["visible_boss"] = None
    for node in result.get("map_nodes", []):
        node["burning"] = False  # account-wide Heart unlock is not in .run
    # These are intentionally outside the historical profile, even live.
    for key in ("potions", "shop_inventory", "grid_selected_cards"):
        result[key] = []
    result["keys"] = {key: None for key in ("ruby", "emerald", "sapphire")}
    result["purge_cost"] = None
    result["event"] = {}
    for card in result.get("deck", []):
        card.pop("misc", None)
        card.pop("is_bottled", None)
    for relic in result.get("relics", []):
        relic["counter"] = None
    return result


def project_example(example, profile):
    result = dict(example, pre_state=project_state(example["pre_state"], profile))
    if profile == RUN_PROFILE:
        result["candidates"] = [{k: v for k, v in c.items() if k not in ("misc", "is_bottled")}
                                for c in example["candidates"]]
    return result
