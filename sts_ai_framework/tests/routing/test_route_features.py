import unittest

from sts_ai_framework.models import CurrentMapNodeState, GameState, MapChoiceState, PlayerState
from sts_ai_framework.routing import (MapGraph, RouteContext,
                                      RouteFeatureExtractor, RoutePolicyConfig)

from ._helpers import make_node


class RouteFeatureTests(unittest.TestCase):
    def _context(self, nodes, choice):
        state = GameState(
            player=PlayerState(current_hp=50, max_hp=80, block=0, energy=0, gold=100),
            floor=10,
            act=1,
            room_phase="COMPLETE",
            screen_type="MAP",
            choice_list=[choice.symbol],
            current_map_choices=[choice],
            current_map_node=CurrentMapNodeState(x=choice.x, y=choice.y,
                                                  symbol=choice.symbol),
            map_nodes=nodes,
        )
        return RouteContext.from_game_state(state, RoutePolicyConfig())

    def test_safe_and_aggressive_pivot_is_real_optionality(self):
        nodes = [
            make_node(0, 2, "M", children=[{"x": 0, "y": 3}, {"x": 1, "y": 3}]),
            make_node(0, 3, "E"),
            make_node(1, 3, "R"),
        ]
        choice = MapChoiceState(choice_index=0, x=0, y=2, symbol="M")
        context = self._context(nodes, choice)
        features = RouteFeatureExtractor(context.graph).extract(nodes[0], context)
        self.assertTrue(features.has_safe_branch)
        self.assertTrue(features.has_aggressive_branch)
        self.assertEqual(1, features.optionality_pivots)

    def test_single_route_reports_forced_elite_chain(self):
        nodes = [
            make_node(0, 2, "M", children=[{"x": 0, "y": 3}]),
            make_node(0, 3, "E", children=[{"x": 0, "y": 4}]),
            make_node(0, 4, "E"),
        ]
        choice = MapChoiceState(choice_index=0, x=0, y=2, symbol="M")
        context = self._context(nodes, choice)
        features = RouteFeatureExtractor(context.graph).extract(nodes[0], context)
        self.assertEqual(2, features.forced_elites)
        self.assertTrue(features.forced_elite_within_horizon)
        self.assertFalse(features.reaches_rest)


if __name__ == "__main__":
    unittest.main()
