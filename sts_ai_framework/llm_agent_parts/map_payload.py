"""Map-context fields for value-network payloads (v3 map-conditioned model).

The v3 value network accepts optional ``act`` / ``map_nodes`` /
``current_node`` / ``keys`` / ``plan`` / ``history`` keys in its state
dictionaries (see ``selectcard/src/map_context.py``).  This helper derives
them from the framework ``GameState``; with a v2 checkpoint loaded the
engine simply ignores the extra keys, so wiring them is always safe.

``plan`` semantics: the value network's ``[PLAN]`` horizon is 1 (the next
room).  A raw cached route goes stale the moment the agent enters its first
room, so callers pass ``(anchor_floor, symbols)`` tuples recorded at the
last MAP decision and this helper shifts them to the current floor.  A
stale or exhausted route omits the key entirely; the inference side then
masks the plan token instead of reading a wrong room.
"""

from typing import Any, Dict, List, Optional

from ..models import GameState

# Keep in sync with selectcard/src/map_context.py PLAN_HORIZON.
PLAN_HORIZON = 1

# Room rows start on these floors (boss/reward floors 17 and 34 have no node).
_ACT_START_FLOOR = {1: 1, 2: 18, 3: 35}


def _floor_in_act(act: int, floor: int) -> int:
    start = _ACT_START_FLOOR.get(act, 1)
    return max(0, int(floor) - start)


def _shift_plan(plan, floor: Optional[int]) -> Optional[List[str]]:
    """Upcoming room symbols from an anchored route.

    ``plan`` is either a plain symbol list (passed through as-is) or an
    ``(anchor_floor, symbols)`` tuple recorded at the last MAP decision.
    The tuple is shifted by the floors elapsed since its anchor so index 0
    is always the room after the current position.
    """
    if plan is None:
        return None
    if isinstance(plan, tuple):
        anchor_floor, symbols = plan
        try:
            shift = int(floor or 0) - int(anchor_floor)
        except (TypeError, ValueError):
            return None
        if shift < 0 or shift >= len(symbols):
            return None
        plan = symbols[shift:]
    return [str(symbol).upper() for symbol in plan if symbol]


def _act_history_symbols(state: GameState, act: int) -> List[str]:
    """Resolved room symbols walked this act, before the current floor."""
    start = _ACT_START_FLOOR.get(act, 1)
    floor = int(state.floor or 0)
    history: List[str] = []
    for entry in getattr(state, "path_history", None) or []:
        entry_floor = getattr(entry, "floor", None)
        resolved = getattr(entry, "resolved_symbol", None)
        if entry_floor is None or not resolved:
            continue
        if start <= int(entry_floor) < floor:
            history.append(str(resolved).upper())
    return history


def map_context_fields(state: GameState, plan=None) -> Dict[str, Any]:
    """Extra state keys for the map-conditioned value network.

    Returns ``{}`` outside a run or whenever the act map is unavailable;
    the inference side degrades gracefully to a missing-context forward pass.
    """
    if state is None or not getattr(state, "act", None):
        return {}
    nodes = getattr(state, "map_nodes", None) or []
    if not nodes:
        return {}

    packed_nodes = [
        {
            "x": node.x,
            "y": node.y,
            "symbol": str(node.symbol or "?").upper(),
            "burning": bool(node.burning),
            "children": [
                {"x": edge.x, "y": edge.y} for edge in (node.children or [])
            ],
        }
        for node in nodes
    ]

    current = None
    current_node = getattr(state, "current_map_node", None)
    if current_node is not None:
        current = (current_node.x, current_node.y)
    else:
        for node in nodes:
            if getattr(node, "is_current", False):
                current = (node.x, node.y)
                break

    keys = getattr(state, "keys", None)
    act = int(state.act)
    payload: Dict[str, Any] = {
        "act": act,
        "floor_in_act": _floor_in_act(act, state.floor or 1),
        "current_node": current,
        "map_nodes": packed_nodes,
        "keys": {
            "ruby": bool(getattr(keys, "ruby", False)),
            "emerald": bool(getattr(keys, "emerald", False)),
            "sapphire": bool(getattr(keys, "sapphire", False)),
        },
        "history": _act_history_symbols(state, act),
    }
    shifted = _shift_plan(plan, state.floor)
    if shifted:
        payload["plan"] = shifted[:PLAN_HORIZON]
    return payload


__all__ = ["map_context_fields", "PLAN_HORIZON"]
