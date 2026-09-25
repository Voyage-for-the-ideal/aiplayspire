"""Tests for the value-network map payload (history and no route plan)."""

import unittest

from sts_ai_framework.llm_agent_parts.map_payload import map_context_fields
from sts_ai_framework.llm_agent_parts.value_payload import value_state_payload
from sts_ai_framework.models import (
    GameState,
    KeyState,
    MapNodeState,
    PathHistoryEntry,
    PlayerState,
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


class PlanRemovalTests(unittest.TestCase):
    def test_plan_is_never_emitted(self):
        payload = map_context_fields(_state())
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
        payload = map_context_fields(state)
        self.assertEqual(payload["history"], ["M", "?"])

    def test_history_empty_at_act_start(self):
        state = _state(
            act=3,
            floor=35,
            path_history=[PathHistoryEntry(floor=34, resolved_symbol="B")],
        )
        payload = map_context_fields(state)
        self.assertEqual(payload["history"], [])


class PayloadShapeTests(unittest.TestCase):
    def test_menu_state_yields_empty_payload(self):
        self.assertEqual(map_context_fields(GameState()), {})
        self.assertEqual(map_context_fields(None), {})

    def test_mapless_state_yields_empty_payload(self):
        self.assertEqual(map_context_fields(_state(map_nodes=[])), {})

    def test_basic_fields_packed(self):
        payload = map_context_fields(_state())
        self.assertEqual(payload["act"], 2)
        self.assertEqual(payload["floor_in_act"], 2)  # floor 20 - act2 start 18
        self.assertEqual(payload["current_node"], (3, 3))
        self.assertEqual(payload["keys"], {"ruby": False, "emerald": False,
                                           "sapphire": False})
        self.assertEqual(len(payload["map_nodes"]), 3)
        self.assertIn("history", payload)


class ValuePayloadTests(unittest.TestCase):
    def test_common_payload_carries_character_and_omits_plan(self):
        state = _state(
            character="THE_SILENT",
            player=PlayerState(current_hp=55, max_hp=70, block=0, energy=3, gold=99),
        )
        payload = value_state_payload(state)
        self.assertEqual(payload["character"], "THE_SILENT")
        self.assertNotIn("plan", payload)

    def test_common_payload_rejects_missing_or_unknown_character(self):
        player = PlayerState(current_hp=55, max_hp=70, block=0, energy=3, gold=99)
        with self.assertRaisesRegex(ValueError, "GameState.character"):
            value_state_payload(_state(player=player))
        with self.assertRaisesRegex(ValueError, "GameState.character"):
            value_state_payload(_state(player=player, character="SILENT"))


if __name__ == "__main__":
    unittest.main()
