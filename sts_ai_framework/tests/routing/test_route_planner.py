import unittest

from sts_ai_framework.models import (CurrentMapNodeState, EventRoomChances, GameState,
                                     KeyState, MapChoiceState, MapEdgeState,
                                     MapNodeState, PlayerState, RelicState)

from ._helpers import (
    extract_choice_index,
    extract_path,
    load_symbol,
    make_node,
)


def _new_policy():
    policy_cls = load_symbol("policy", "RoutePolicy")
    for factory in (
        lambda: policy_cls(),
        lambda: policy_cls(None),
        lambda: policy_cls({}),
    ):
        try:
            return factory()
        except TypeError:
            continue
    return policy_cls()


def _feature_mapping(value):
    if isinstance(value, dict):
        return value
    if hasattr(value, "model_dump"):
        return value.model_dump()
    if hasattr(value, "dict"):
        return value.dict()
    if hasattr(value, "__dict__"):
        return value.__dict__
    return {}


class RoutingPlannerTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.RouteContext = load_symbol("context", "RouteContext")
        cls.RouteFeatureExtractor = load_symbol("features", "RouteFeatureExtractor")
        cls.RoutePlanner = load_symbol("planner", "RoutePlanner")

    def _mk_state(self, *, hp=60, gold=100, act=1, choices, nodes,
                  event_chances=None, keys=None, relics=None):
        return GameState(
            player=PlayerState(current_hp=hp, max_hp=80, block=0, energy=0, gold=gold),
            deck=[],
            hand=[],
            draw_pile=[],
            discard_pile=[],
            exhaust_pile=[],
            draw_pile_size=0,
            discard_pile_size=0,
            exhaust_pile_size=0,
            monsters=[],
            floor=12,
            act=act,
            room_phase="COMPLETE",
            screen_type="MAP",
            choice_list=[c.symbol for c in choices],
            current_map_choices=choices,
            map_nodes=nodes,
            current_map_node=CurrentMapNodeState(x=0, y=1, symbol="M"),
            event_chances=event_chances or EventRoomChances(),
            keys=keys or KeyState(),
            relics=relics or [],
        )

    def test_forced_elite_is_returned_even_when_optional_logic_differs(self):
        nodes = [
            make_node(0, 1, symbol="M", children=[{"x": 0, "y": 2}]),
            make_node(0, 2, symbol="E", children=[]),
        ]
        choices = [MapChoiceState(choice_index=0, x=0, y=2, symbol="E")]
        state = self._mk_state(hp=20, choices=choices, nodes=nodes)

        planner = self.RoutePlanner(_new_policy())
        decision = planner.choose(state)
        self.assertIsNotNone(decision)
        self.assertEqual(extract_choice_index(decision), 0)

    def test_optional_elite_vs_rest_prefers_rest_when_hp_is_critical(self):
        nodes = [
            make_node(0, 1, symbol="M", children=[
                {"x": 0, "y": 2, "winged": False},
                {"x": 1, "y": 2, "winged": False},
            ]),
            make_node(0, 2, symbol="E", children=[]),
            make_node(1, 2, symbol="R", children=[]),
        ]
        choices = [
            MapChoiceState(choice_index=0, x=0, y=2, symbol="E"),
            MapChoiceState(choice_index=1, x=1, y=2, symbol="R"),
        ]
        state = self._mk_state(hp=10, gold=50, choices=choices, nodes=nodes)

        planner = self.RoutePlanner(_new_policy())
        decision = planner.choose(state)
        self.assertIsNotNone(decision)
        self.assertEqual(extract_choice_index(decision), 1)

    def test_rest_missing_rest_choice_still_returns_legal_non_rest_route(self):
        nodes = [
            make_node(0, 1, symbol="M", children=[{"x": 0, "y": 2}]),
            make_node(0, 2, symbol="M", children=[]),
        ]
        choices = [MapChoiceState(choice_index=0, x=0, y=2, symbol="M")]
        state = self._mk_state(hp=10, gold=50, choices=choices, nodes=nodes)

        planner = self.RoutePlanner(_new_policy())
        decision = planner.choose(state)
        self.assertIsNotNone(decision)
        self.assertEqual(extract_choice_index(decision), 0)

    def test_feature_context_is_sensitive_to_hp_gold_and_question_mark_probability(self):
        nodes = [
            make_node(0, 1, symbol="M", children=[{"x": 0, "y": 2}]),
            make_node(0, 2, symbol="?", children=[]),
        ]
        graph = self.RouteContext.from_game_state(
            self._mk_state(hp=60, gold=120, choices=[
                MapChoiceState(choice_index=0, x=0, y=2, symbol="?"),
            ], nodes=nodes),
            _new_policy(),
        ).graph
        root = next(c for c in graph.paths_from(graph.start_node, 1, 0))
        candidate = self.RouteFeatureExtractor(graph, _new_policy()).extract(
            root, self.RouteContext.from_game_state(self._mk_state(hp=60, gold=120, choices=[
                MapChoiceState(choice_index=0, x=0, y=2, symbol="?"),
            ], nodes=nodes), _new_policy())
        )
        candidate_low = self.RouteFeatureExtractor(graph, _new_policy()).extract(
            root, self.RouteContext.from_game_state(self._mk_state(hp=20, gold=20, choices=[
                MapChoiceState(choice_index=0, x=0, y=2, symbol="?"),
            ], nodes=nodes), _new_policy())
        )

        feature_a = _feature_mapping(candidate)
        feature_b = _feature_mapping(candidate_low)
        self.assertTrue(feature_a and isinstance(feature_a, dict))
        self.assertTrue(feature_b and isinstance(feature_b, dict))

        hp_fields = [k for k in feature_a if "hp" in str(k).lower()]
        gold_fields = [k for k in feature_a if "gold" in str(k).lower()]
        self.assertTrue(hp_fields, "HP-related feature fields missing")
        self.assertTrue(gold_fields, "Gold-related feature fields missing")
        changed = any(feature_a[k] != feature_b.get(k) for k in hp_fields + gold_fields if k in feature_b)
        self.assertTrue(changed, "HP/Gold changes should affect feature extraction")

        # probability-related keys should be stable for deterministic states
        repeat = _feature_mapping(
            self.RouteFeatureExtractor(graph, _new_policy()).extract(
                root, self.RouteContext.from_game_state(self._mk_state(hp=60, gold=120, choices=[
                    MapChoiceState(choice_index=0, x=0, y=2, symbol="?"),
                ], nodes=nodes), _new_policy())
            )
        )
        self.assertEqual(feature_a, repeat)
        self.assertTrue(any("prob" in str(key).lower() for key in feature_a))

    def test_key_reachability_in_features(self):
        nodes = [
            make_node(0, 1, symbol="M", children=[{"x": 0, "y": 2}]),
            make_node(0, 2, symbol="$", children=[]),
        ]
        policy = _new_policy()
        graph_state_no_key = self._mk_state(hp=70, gold=120, choices=[
            MapChoiceState(choice_index=0, x=0, y=2, symbol="$"),
        ], nodes=nodes)
        graph_state_with_key = self._mk_state(hp=70, gold=120, choices=[
            MapChoiceState(choice_index=0, x=0, y=2, symbol="$"),
        ], nodes=nodes)
        graph_state_with_key.keys = graph_state_with_key.keys.model_copy(update={"ruby": True})

        graph = self.RouteContext.from_game_state(graph_state_no_key, policy).graph
        root = next(c for c in graph.paths_from(graph.start_node, 1, 0))
        extractor = self.RouteFeatureExtractor(graph, policy)

        no_key_features = _feature_mapping(extractor.extract(root, self.RouteContext.from_game_state(graph_state_no_key, policy)))
        with_key_features = _feature_mapping(
            extractor.extract(
                root,
                self.RouteContext.from_game_state(graph_state_with_key, policy),
            )
        )
        self.assertTrue(no_key_features and isinstance(no_key_features, dict))
        self.assertTrue(with_key_features and isinstance(with_key_features, dict))
        self.assertNotEqual(no_key_features, with_key_features)
        key_related = [k for k in no_key_features if "key" in str(k).lower()]
        self.assertTrue(key_related, "Key reachability metadata missing in features")

    def test_choose_is_deterministic_for_same_state(self):
        nodes = [
            make_node(0, 1, symbol="M", children=[{"x": 0, "y": 2}, {"x": 1, "y": 2}]),
            make_node(0, 2, symbol="M", children=[]),
            make_node(1, 2, symbol="R", children=[]),
        ]
        choices = [
            MapChoiceState(choice_index=0, x=0, y=2, symbol="M"),
            MapChoiceState(choice_index=1, x=1, y=2, symbol="R"),
        ]
        state = self._mk_state(hp=55, gold=200, choices=choices, nodes=nodes)
        planner = self.RoutePlanner(_new_policy())
        indices = {extract_choice_index(planner.choose(state)) for _ in range(100)}
        self.assertEqual(len(indices), 1)

    def test_question_mark_value_uses_effective_monster_probability(self):
        nodes = [make_node(0, 2, symbol="?", children=[])]
        choices = [MapChoiceState(choice_index=0, x=0, y=2, symbol="?")]
        safe = self._mk_state(
            hp=12, choices=choices, nodes=nodes,
            event_chances=EventRoomChances(event=1.0, monster=0, shop=0, treasure=0, elite=0),
        )
        dangerous = self._mk_state(
            hp=12, choices=choices, nodes=nodes,
            event_chances=EventRoomChances(event=0.1, monster=0.9, shop=0, treasure=0, elite=0),
        )
        planner = self.RoutePlanner(_new_policy())
        self.assertGreater(planner.choose(safe).score, planner.choose(dangerous).score)

    def test_shop_value_increases_with_gold(self):
        nodes = [make_node(0, 2, symbol="$", children=[])]
        choices = [MapChoiceState(choice_index=0, x=0, y=2, symbol="$")]
        planner = self.RoutePlanner(_new_policy())
        low = planner.choose(self._mk_state(gold=20, choices=choices, nodes=nodes))
        high = planner.choose(self._mk_state(gold=300, choices=choices, nodes=nodes))
        self.assertGreater(high.score, low.score)

    def test_act_three_preserves_only_reachable_burning_elite(self):
        nodes = [
            make_node(0, 2, symbol="M", children=[{"x": 0, "y": 3}]),
            MapNodeState(x=0, y=3, symbol="E", burning=True, children=[]),
            make_node(1, 2, symbol="R", children=[]),
        ]
        choices = [
            MapChoiceState(choice_index=0, x=0, y=2, symbol="M"),
            MapChoiceState(choice_index=1, x=1, y=2, symbol="R"),
        ]
        state = self._mk_state(
            hp=70, act=3, choices=choices, nodes=nodes,
            keys=KeyState(ruby=True, emerald=False, sapphire=True),
        )
        decision = self.RoutePlanner(_new_policy()).choose(state)
        self.assertEqual(0, decision.choice_index)
        self.assertFalse(decision.candidates[1].key_feasible)

    def test_act_three_preserves_only_reachable_rest_for_ruby(self):
        nodes = [
            make_node(0, 2, symbol="M", children=[]),
            make_node(1, 2, symbol="R", children=[]),
        ]
        choices = [
            MapChoiceState(choice_index=0, x=0, y=2, symbol="M"),
            MapChoiceState(choice_index=1, x=1, y=2, symbol="R"),
        ]
        state = self._mk_state(
            hp=70, act=3, choices=choices, nodes=nodes,
            keys=KeyState(ruby=False, emerald=True, sapphire=True),
        )
        decision = self.RoutePlanner(_new_policy()).choose(state)
        self.assertEqual(1, decision.choice_index)
        self.assertFalse(decision.candidates[0].key_feasible)

    def test_winged_current_choice_consumes_charge_before_future_search(self):
        nodes = [
            make_node(0, 2, symbol="M", children=[{"x": 1, "y": 3, "winged": True}]),
            make_node(1, 3, symbol="E", children=[]),
        ]
        choices = [MapChoiceState(
            choice_index=0, x=0, y=2, symbol="M", winged=True,
        )]
        state = self._mk_state(
            hp=70, choices=choices, nodes=nodes,
            relics=[RelicState(id="WingedGreaves", name="Wing Boots", counter=1)],
        )
        decision = self.RoutePlanner(_new_policy()).choose(state)
        self.assertEqual(((0, 2),), decision.candidates[0].best_path)
        self.assertAlmostEqual(decision.score, decision.breakdown.total)


if __name__ == "__main__":
    unittest.main()
