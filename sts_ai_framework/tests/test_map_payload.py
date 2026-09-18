"""Tests for the value-network map payload (plan freshness + history)."""

import unittest

from sts_ai_framework.llm_agent_parts.map_payload import map_context_fields
from sts_ai_framework.models import (
    GameState,
    KeyState,
    MapNodeState,
    PathHistoryEntry,
)


def _state(**overrides):
    state = GameState(
        act=2,
        floor=20,
        map_nodes=[
            MapNodeState(x=3, y=2, symbol="M"),
            MapNodeState(x=3, y=3, symbol="E", is_current=True),
            MapNodeState(x=3, y=4, symbol="R"),
        ],
        keys=KeyState(),
    )
    for key, value in overrides.items():
        setattr(state, key, value)
    return state


class PlanFreshnessTests(unittest.TestCase):
    ROUTE = (16, ["R", "E", "$"])  # anchored on the floor it was computed

    def test_same_floor_yields_first_route_room(self):
        payload = map_context_fields(_state(floor=16), self.ROUTE)
        self.assertEqual(payload["plan"], ["R"])

    def test_one_floor_later_shifts_past_entered_room(self):
        payload = map_context_fields(_state(floor=17), self.ROUTE)
        self.assertEqual(payload["plan"], ["E"])

    def test_exhausted_route_omits_plan(self):
        payload = map_context_fields(_state(floor=19), self.ROUTE)
        self.assertNotIn("plan", payload)

    def test_stale_route_floor_moved_backwards_omits_plan(self):
        payload = map_context_fields(_state(floor=15), self.ROUTE)
        self.assertNotIn("plan", payload)

    def test_plain_symbol_list_passes_through(self):
        payload = map_context_fields(_state(), ["e"])
        self.assertEqual(payload["plan"], ["E"])

    def test_no_plan_omits_key(self):
        payload = map_context_fields(_state(), None)
        self.assertNotIn("plan", payload)


class HistoryTests(unittest.TestCase):
    def test_history_filters_current_act_and_current_floor(self):
        state = _state(
            floor=20,
            path_history=[
                PathHistoryEntry(floor=1, resolved_symbol="M"),
                PathHistoryEntry(floor=16, resolved_symbol="R"),
                PathHistoryEntry(floor=17, resolved_symbol="B"),
                PathHistoryEntry(floor=18, resolved_symbol="M"),
                PathHistoryEntry(floor=19, resolved_symbol="?"),
                PathHistoryEntry(floor=20, resolved_symbol="$"),  # current room
                PathHistoryEntry(floor=21, resolved_symbol="E"),  # future
                PathHistoryEntry(floor=None, resolved_symbol="T"),  # malformed
            ],
        )
        payload = map_context_fields(state, None)
        self.assertEqual(payload["history"], ["M", "?"])

    def test_history_empty_at_act_start(self):
        state = _state(
            act=3,
            floor=35,
            path_history=[PathHistoryEntry(floor=34, resolved_symbol="B")],
        )
        payload = map_context_fields(state, None)
        self.assertEqual(payload["history"], [])


class PayloadShapeTests(unittest.TestCase):
    def test_menu_state_yields_empty_payload(self):
        self.assertEqual(map_context_fields(GameState(), None), {})
        self.assertEqual(map_context_fields(None, None), {})

    def test_mapless_state_yields_empty_payload(self):
        self.assertEqual(map_context_fields(_state(map_nodes=[]), None), {})

    def test_basic_fields_packed(self):
        payload = map_context_fields(_state(), None)
        self.assertEqual(payload["act"], 2)
        self.assertEqual(payload["floor_in_act"], 2)  # floor 20 - act2 start 18
        self.assertEqual(payload["current_node"], (3, 3))
        self.assertEqual(payload["keys"], {"ruby": False, "emerald": False,
                                           "sapphire": False})
        self.assertEqual(len(payload["map_nodes"]), 3)
        self.assertIn("history", payload)


if __name__ == "__main__":
    unittest.main()
