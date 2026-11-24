"""Mapped synthetic states through the unchanged format-12 feature pipeline."""
import json
import unittest

from selectcard.src.map_context import decode_map_features, inference_context, ROOM_SYMBOL_TO_ID
from sts_ai_framework.headless.state import HeadlessStateMapper
from sts_ai_framework.llm_agent_parts.map_payload import map_context_fields
from sts_ai_framework.llm_agent_parts.value_payload import value_state_payload
from sts_ai_framework.models import GameState
from sts_ai_framework.routing.map_graph import MapGraph
from sts_ai_framework.tests.test_headless_state import observation


class Format12ContractTest(unittest.TestCase):
    def test_card_upgrade_and_public_features(self):
        mapper = HeadlessStateMapper()
        state = mapper.map_state(observation(seed=42, rng_state={"future": 1}, router_plan=[2]), run_id="A", sequence=0)
        value = value_state_payload(state)
        self.assertEqual(value["deck"], ["Searing Blow+3"])
        self.assertEqual((value["character"], value["ascension"]), ("IRONCLAD", 10))
        self.assertFalse(set(value) & {"seed", "seed_group", "rng", "rng_state", "next_boss", "router_plan"})

    def test_real_edges_unique_degrees_and_float_coordinates(self):
        state = HeadlessStateMapper().map_state(observation(), run_id="A", sequence=0)
        graph = MapGraph(state.map_nodes)
        self.assertEqual(len(graph.edges_from((0, 1))), 1)
        packed = inference_context(map_context_fields(state))
        self.assertEqual(len(packed["map_rooms"]), 105)
        self.assertEqual((packed["map_flags"][7] >> 4) & 3, 1)
        self.assertEqual(packed["map_outgoing_mask"][7], 4)  # x=0 -> x=1, not a winged shortcut.
        rooms, flags = decode_map_features(packed["map_rooms"], packed["map_flags"])
        self.assertEqual(rooms[15], ROOM_SYMBOL_TO_ID["E"])
        self.assertEqual(flags[15][:3], [1.0, 1.0, 0.0])
        self.assertAlmostEqual(flags[15][3], 1 / 6)
        self.assertAlmostEqual(flags[15][4], 2 / 14)
        self.assertEqual(flags[22][2], 1.0)  # Reachable next room.

    def test_key_known_mask_after_json_and_map_loss(self):
        mapper = HeadlessStateMapper()
        mapper.map_state(observation(observedKeys={"emerald": True}), run_id="A", sequence=0)
        payload = mapper.map_payload(observation(act=2, floor=18, map=None), run_id="A", sequence=1)
        restored = GameState(**json.loads(json.dumps(payload)))
        packed = inference_context(map_context_fields(restored))
        self.assertEqual(packed["key_states"], [0, 1, 0])
        self.assertEqual(packed["key_known_mask"], [0, 1, 0])
        self.assertFalse(any(packed["map_rooms"]))

    def test_only_eight_departed_current_act_rooms_most_recent_first(self):
        symbols = ["M", "?", "$", "R", "E", "T"]
        history = [{"floor": f, "selected_symbol": "?", "resolved_symbol": symbols[f % len(symbols)]}
                   for f in range(1, 32)]
        state = HeadlessStateMapper().map_state(observation(act=2, floor=30, map=None, history=history), run_id="A", sequence=0)
        context = map_context_fields(state)
        self.assertEqual(context["history"], [symbols[f % len(symbols)] for f in range(18, 30)])
        packed = inference_context(context)
        self.assertEqual(packed["history_rooms"], [ROOM_SYMBOL_TO_ID[symbols[f % len(symbols)]] for f in range(29, 21, -1)])
        self.assertEqual(len(packed["history_rooms"]), 8)

    def test_history_resets_at_act_boundary_without_dropping_keys(self):
        raw = observation(act=3, floor=35, map=None, observedKeys={"emerald": True}, history=[
            {"floor": 34, "selected_symbol": "B", "resolved_symbol": "B"}])
        state = HeadlessStateMapper().map_state(raw, run_id="A", sequence=0)
        packed = inference_context(map_context_fields(state))
        self.assertEqual(packed["history_rooms"], [0] * 8)
        self.assertEqual(packed["key_known_mask"], [0, 1, 0])


if __name__ == "__main__":
    unittest.main()
