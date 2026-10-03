"""Map-context fields for value-network payloads (v3 map-conditioned model).

The v3 value network accepts optional ``act`` / ``map_nodes`` /
``current_node`` / ``keys`` / ``history`` keys in its state
dictionaries (see ``selectcard/src/map_context.py``). This helper derives
them from the framework ``GameState``. Checkpoint format 10 requires the
matching map-context schema, so legacy checkpoints are rejected at load time.
"""

from typing import Any, Dict, List

from ..models import GameState

# Room rows start on these floors (boss/reward floors 17 and 34 have no node).
_ACT_START_FLOOR = {1: 1, 2: 18, 3: 35}


def _floor_in_act(act: int, floor: int) -> int:
    start = _ACT_START_FLOOR.get(act, 1)
    return max(0, int(floor) - start)


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


def map_context_fields(state: GameState) -> Dict[str, Any]:
    """Extra state keys for the map-conditioned value network.

    Returns ``{}`` outside a run. Keys remain authoritative even when the act
    map is unavailable; map/history fields then degrade independently.
    """
    if state is None or not getattr(state, "act", None):
        return {}
    nodes = getattr(state, "map_nodes", None) or []

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
    return payload


__all__ = ["map_context_fields"]
