"""Wing Boots add route choices, not permanent edges in the value model."""

import unittest

from selectcard.src.map_context import inference_context
from sts_ai_framework.llm_agent_parts.map_payload import map_context_fields
from sts_ai_framework.models import GameState, MapEdgeState
from sts_ai_framework.routing.map_graph import MapGraph


class WingedMapPayloadTests(unittest.TestCase):
    def test_value_model_sees_static_map_while_router_keeps_jumps(self):
        state = GameState(act=1, map_nodes=[
            {"x": 1, "y": 0, "symbol": "M", "is_current": True,
             "children": [{"x": 1, "y": 1}]},
            {"x": 1, "y": 1, "symbol": "M"},
            {"x": 2, "y": 1, "symbol": "R"},
            {"x": 3, "y": 1, "symbol": "E"},
        ])
        expected = inference_context(map_context_fields(state))
        # Include an adjacent jump too: coordinates alone do not identify one.
        state.map_nodes[0].children.extend(
            MapEdgeState(x=x, y=1, winged=True) for x in (2, 3)
        )
        self.assertEqual(inference_context(map_context_fields(state)), expected)

        graph = MapGraph(state.map_nodes)
        for charges, expected_paths in [(0, {(1, 0)}), (1, {(1, 1), (2, 0), (3, 0)})]:
            with self.subTest(charges=charges):
                paths = graph.paths_from((1, 0), depth=1, wing_charges=charges)
                self.assertEqual({(p.nodes[-1].x, p.wing_charges_remaining) for p in paths},
                                 expected_paths)

    def test_unmarked_long_edge_still_fails_validation(self):
        state = GameState(act=1, map_nodes=[
            {"x": 1, "y": 0, "symbol": "M", "children": [{"x": 3, "y": 1}]},
            {"x": 3, "y": 1, "symbol": "E"},
        ])
        with self.assertRaisesRegex(ValueError, "Unsupported map edge"):
            inference_context(map_context_fields(state))
