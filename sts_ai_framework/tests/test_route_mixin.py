import unittest
from contextlib import redirect_stdout
import io
import sys
import types
from unittest.mock import patch

from sts_ai_framework import config
from sts_ai_framework.llm_agent_parts.action_mixin import ActionMixin
from sts_ai_framework.llm_agent_parts.choice_mixin import ChoiceMixin
from sts_ai_framework.llm_agent_parts.route_mixin import RouteMixin
from sts_ai_framework.models import ActionType, GameState


class FailingCompletions:
    def __init__(self):
        self.calls = 0

    def create(self, **_kwargs):
        self.calls += 1
        raise AssertionError("structured MAP routing must not call the LLM")


class MapHarness(ActionMixin, ChoiceMixin, RouteMixin):
    def __init__(self):
        self.last_screen_type = None
        self._pending_event = None
        self._pending_grid = None
        self.value_engine = None
        self.model_name = "test-model"
        self.completions = FailingCompletions()
        self.llm_client = type(
            "Client", (), {"chat": type("Chat", (), {"completions": self.completions})()}
        )()


def map_state(*, hp=70, gold=100, choices=None, nodes=None):
    return GameState(
        player={"current_hp": hp, "max_hp": 80, "block": 0, "energy": 0, "gold": gold},
        deck=[], hand=[], draw_pile=[], discard_pile=[], exhaust_pile=[],
        draw_pile_size=0, discard_pile_size=0, exhaust_pile_size=0,
        monsters=[], floor=10, act=1, room_phase="COMPLETE", screen_type="MAP",
        choice_list=["elite", "rest"],
        current_map_choices=choices if choices is not None else [
            {"choice_index": 0, "x": 0, "y": 5, "symbol": "E"},
            {"choice_index": 1, "x": 1, "y": 5, "symbol": "R"},
        ],
        map_nodes=nodes if nodes is not None else [
            {"x": 0, "y": 5, "symbol": "E", "children": []},
            {"x": 1, "y": 5, "symbol": "R", "children": []},
        ],
    )


class RouteMixinTests(unittest.TestCase):
    def test_structured_map_uses_local_score_without_llm(self):
        agent = MapHarness()
        action = agent.choose_action(map_state(hp=75))
        self.assertEqual(ActionType.CHOOSE, action.type)
        self.assertEqual(0, action.choice_index)
        self.assertEqual(0, agent.completions.calls)

    def test_low_hp_prefers_rest_and_returns_legal_server_index(self):
        agent = MapHarness()
        action = agent.choose_action(map_state(hp=12))
        self.assertEqual(ActionType.CHOOSE, action.type)
        self.assertEqual(1, action.choice_index)
        self.assertLess(action.choice_index, 2)
        self.assertEqual(0, agent.completions.calls)

    def test_invalid_structured_index_is_not_intercepted(self):
        agent = MapHarness()
        state = map_state(choices=[
            {"choice_index": 9, "x": 0, "y": 5, "symbol": "E"},
        ])
        self.assertIsNone(agent._get_deterministic_map_action(state))

    def test_missing_map_graph_is_not_intercepted(self):
        agent = MapHarness()
        state = map_state(nodes=[])
        self.assertIsNone(agent._get_deterministic_map_action(state))

    def test_single_synthetic_boss_choice_does_not_call_llm(self):
        agent = MapHarness()
        state = map_state(
            choices=[{"choice_index": 0, "x": -1, "y": 15, "symbol": "B"}],
            nodes=[],
        )
        state.choice_list = ["boss"]
        action = agent.choose_action(state)
        self.assertEqual(ActionType.CHOOSE, action.type)
        self.assertEqual(0, action.choice_index)
        self.assertEqual(0, agent.completions.calls)

    def test_legacy_route_policy_preserves_old_scoring(self):
        with patch.object(config, "ROUTE_POLICY", "legacy"):
            agent = MapHarness()
            action = agent.choose_action(map_state(hp=12))
            self.assertEqual(ActionType.CHOOSE, action.type)
            self.assertEqual(1, action.choice_index)
            self.assertEqual(0, agent.completions.calls)

    def test_map_fallback_first_legal_when_graph_is_invalid(self):
        agent = MapHarness()
        state = map_state(
            choices=[{"choice_index": 5, "x": 0, "y": 5, "symbol": "E"}],
            nodes=[{"x": 0, "y": 5, "symbol": "E", "children": []}],
        )
        action = agent.choose_action(state)
        self.assertEqual(ActionType.CHOOSE, action.type)
        self.assertEqual(0, action.choice_index)
        self.assertEqual(0, agent.completions.calls)

    def test_missing_structured_choices_uses_first_visible_choice_without_llm(self):
        agent = MapHarness()
        state = map_state(choices=[])
        action = agent.choose_action(state)
        self.assertEqual(ActionType.CHOOSE, action.type)
        self.assertEqual(0, action.choice_index)
        self.assertEqual(0, agent.completions.calls)

    def test_empty_map_choices_waits_without_llm(self):
        agent = MapHarness()
        state = map_state(choices=[], nodes=[])
        state.choice_list = []
        action = agent.choose_action(state)
        self.assertEqual(ActionType.WAIT, action.type)
        self.assertIsNone(action.choice_index)
        self.assertEqual(0, agent.completions.calls)

    def test_planner_mode_returns_route_logs(self):
        # Thin adapter path: the planner module is optional for this task
        # and can be absent in runtime; this test verifies the integration shape.
        routing_pkg = types.ModuleType("sts_ai_framework.routing")
        planner_pkg = types.ModuleType("sts_ai_framework.routing.planner")
        routing_pkg.__path__ = []  # allow import as package

        class RouteDecision:
            def __init__(self):
                self.choice_index = 1
                self.mode = "FIRST_LEGAL"
                self.score = 7.5
                self.components = ["current_layer"]
                self.explanation = "planner policy"

        class RoutePlanner:
            def choose(self, _state):
                return RouteDecision()

        planner_pkg.RoutePlanner = RoutePlanner

        with patch.dict(
            sys.modules,
            {"sts_ai_framework.routing": routing_pkg, "sts_ai_framework.routing.planner": planner_pkg},
        ):
            agent = MapHarness()
            state = map_state()
            sink = io.StringIO()
            with redirect_stdout(sink):
                action = agent.choose_action(state)
            output = sink.getvalue()
            self.assertEqual(ActionType.CHOOSE, action.type)
            self.assertEqual(0, action.choice_index)
            self.assertIn("[route_candidate]", output)
            self.assertIn("[route_decision]", output)
            self.assertIn("mode=FIRST_LEGAL", output)

    def test_planner_wait_mode_prefers_wait(self):
        routing_pkg = types.ModuleType("sts_ai_framework.routing")
        planner_pkg = types.ModuleType("sts_ai_framework.routing.planner")
        routing_pkg.__path__ = []

        class RouteDecision:
            mode = "WAIT"
            score = 0.0
            components = []
            explanation = "no action"

        class RoutePlanner:
            def choose(self, _state):
                return RouteDecision()

        planner_pkg.RoutePlanner = RoutePlanner

        with patch.dict(
            sys.modules,
            {"sts_ai_framework.routing": routing_pkg, "sts_ai_framework.routing.planner": planner_pkg},
        ):
            agent = MapHarness()
            action = agent.choose_action(map_state())
            self.assertEqual(ActionType.WAIT, action.type)


if __name__ == "__main__":
    unittest.main()
