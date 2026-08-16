import copy
import os
import sys
import unittest

from sts_ai_framework.llm_agent_parts.action_mixin import ActionMixin
from sts_ai_framework.llm_agent_parts.choice_mixin import ChoiceMixin
from sts_ai_framework.llm_agent_parts.decision_mixin import DecisionMixin
from sts_ai_framework.models import ActionType, GameState
from sts_ai_framework.__main__ import _is_action_effective


def make_state(event=None, hp=50, choice_list=None):
    return GameState(
        player={"current_hp": hp, "max_hp": 80, "block": 0, "energy": 0, "gold": 100},
        deck=[], hand=[], draw_pile=[], discard_pile=[], exhaust_pile=[],
        draw_pile_size=0, discard_pile_size=0, exhaust_pile_size=0,
        monsters=[], floor=10, act=1, room_phase="EVENT", screen_type="EVENT",
        choice_list=choice_list or [], event=event,
    )


def choice(index, effects=None, followup="NONE"):
    return {
        "button_index": index,
        "action_index": index,
        "enabled": True,
        "label": f"choice {index}",
        "kind": "EFFECT",
        "followup": followup,
        "outcomes": [{"probability": 1.0, "effects": effects or []}],
    }


def event(kind, choices, status="KNOWN"):
    return {
        "id": "Test Event", "class_name": "TestEvent", "phase": "INTRO",
        "semantics_status": status, "decision_kind": kind, "choices": choices,
    }


def match_choice(slot, action_index=None, card_id=None, enabled=True):
    effect = {"type": "reveal_match_card", "slot": slot}
    if card_id is not None:
        effect["revealed_card_id"] = card_id
    return {
        "button_index": slot,
        "action_index": action_index,
        "enabled": enabled,
        "label": f"slot {slot}",
        "kind": "MINI_GAME_CARD",
        "followup": "EVENT",
        "outcomes": [{"probability": 1.0, "effects": [effect]}],
    }


class FakeValueEngine:
    def __init__(self):
        self.choices = None

    def recommend_choice(self, state, choices, exclude_purge_ids=None):
        self.choices = choices
        return choices[-1]


class Harness(DecisionMixin, ActionMixin, ChoiceMixin):
    def __init__(self, value_engine=None):
        self.value_engine = value_engine
        self._pending_event = None
        self._pending_grid = None


class BossRewardValueEngine:
    def __init__(self, selected_index):
        self.selected_index = selected_index
        self.state = None
        self.choices = None

    def recommend_choice(self, state, choices):
        self.state = state
        self.choices = choices
        return {"index": self.selected_index}


class StructuredEventTests(unittest.TestCase):
    def test_boss_reward_uses_only_real_choices(self):
        engine = BossRewardValueEngine(2)
        state = make_state(choice_list=["Relic A", "Relic B", "Relic C"])
        state.screen_type = "BOSS_REWARD"

        action = Harness(engine)._get_model_boss_reward_decision(state)

        self.assertEqual(ActionType.CHOOSE, action.type)
        self.assertEqual(2, action.choice_index)
        self.assertEqual([0, 1, 2], [item["index"] for item in engine.choices])
        self.assertTrue(all(item["action"] == "composite_event" for item in engine.choices))

    def test_boss_reward_invalid_model_index_falls_back_to_first_relic(self):
        engine = BossRewardValueEngine(-1)
        state = make_state(choice_list=["Relic A", "Relic B", "Relic C"])
        state.screen_type = "BOSS_REWARD"

        action = Harness(engine)._get_model_boss_reward_decision(state)

        self.assertEqual(ActionType.CHOOSE, action.type)
        self.assertEqual(0, action.choice_index)

    def test_act_one_boss_reward_uses_next_act_floor(self):
        engine = BossRewardValueEngine(0)
        state = make_state(choice_list=["Relic A"])
        state.screen_type = "BOSS_REWARD"
        state.floor = 16

        Harness(engine)._get_model_boss_reward_decision(state)

        self.assertEqual(17, engine.state["floor"])
        self.assertEqual(16, state.floor)

    def test_act_two_boss_reward_uses_next_act_floor(self):
        engine = BossRewardValueEngine(0)
        state = make_state(choice_list=["Relic A"])
        state.screen_type = "BOSS_REWARD"
        state.floor = 33

        Harness(engine)._get_model_boss_reward_decision(state)

        self.assertEqual(34, engine.state["floor"])
        self.assertEqual(33, state.floor)

    def test_post_boss_card_reward_uses_next_act_floor(self):
        engine = BossRewardValueEngine(0)
        state = make_state(choice_list=["Inflame"])
        state.screen_type = "CARD_REWARD"
        state.floor = 16
        state.post_boss_card_reward = True
        state.reward_card_ids = ["Inflame"]

        Harness(engine)._get_model_card_decision(state)

        self.assertEqual(17, engine.state["floor"])
        self.assertEqual(16, state.floor)

    def test_normal_card_reward_keeps_current_floor(self):
        engine = BossRewardValueEngine(0)
        state = make_state(choice_list=["Inflame"])
        state.screen_type = "CARD_REWARD"
        state.floor = 16
        state.reward_card_ids = ["Inflame"]

        Harness(engine)._get_model_card_decision(state)

        self.assertEqual(16, engine.state["floor"])

    def test_old_server_payload_remains_valid(self):
        state = make_state(choice_list=["leave"])
        self.assertIsNone(state.event)

    def test_forced_choice_uses_action_index_and_stores_grid_context(self):
        state = make_state(event("FORCED", [choice(0, [
            {"type": "select_cards", "purpose": "PURGE", "count": 1},
        ], "GRID")]), choice_list=["offer"])
        agent = Harness()
        action = agent._get_structured_event_decision(state)
        self.assertEqual(ActionType.CHOOSE, action.type)
        self.assertEqual(0, action.choice_index)
        self.assertEqual("purge", agent._pending_grid["purpose"])

    def test_deterministic_choices_go_to_value_engine(self):
        engine = FakeValueEngine()
        state = make_state(event("DETERMINISTIC", [
            choice(0, [{"type": "gain_gold", "amount": 10}]),
            choice(1, [{"type": "gain_max_hp", "amount": 5}]),
        ]), choice_list=["gold", "max hp"])
        action = Harness(engine)._get_structured_event_decision(state)
        self.assertEqual(1, action.choice_index)
        self.assertEqual("composite_event", engine.choices[0]["action"])

    def test_complex_rule_avoids_only_immediately_lethal_choice(self):
        state = make_state(event("COMPLEX", [
            choice(0, [{"type": "lose_hp", "amount": 50}]),
            choice(1, []),
        ]), hp=50, choice_list=["die", "leave"])
        action = Harness()._get_structured_event_decision(state)
        self.assertEqual(1, action.choice_index)

    def test_cost_free_positive_choice_dominates_no_op(self):
        state = make_state(event("COMPLEX", [
            choice(0, [{"type": "gain_gold", "amount": 10}]),
            choice(1, []),
        ]), choice_list=["gold", "leave"])
        action = Harness()._get_structured_event_decision(state)
        self.assertEqual(0, action.choice_index)

    def test_full_hp_heal_does_not_falsely_dominate_no_op(self):
        state = make_state(event("COMPLEX", [
            choice(0, [{"type": "gain_hp", "amount": 10}]),
            choice(1, []),
        ]), hp=80, choice_list=["heal", "leave"])
        self.assertIsNone(Harness()._get_structured_event_decision(state))

    def test_lethal_event_index_is_rejected(self):
        state = make_state(event("COMPLEX", [
            choice(0, [{"type": "lose_hp", "amount": 50}]),
            choice(1, [{"type": "gain_gold", "amount": 10}]),
        ]), hp=50, choice_list=["die", "gold"])
        agent = Harness()
        self.assertFalse(agent._is_safe_event_action_index(state, 0))
        self.assertTrue(agent._is_safe_event_action_index(state, 1))

    def test_unknown_event_is_left_for_llm(self):
        state = make_state(event("UNKNOWN", [choice(0)], status="UNKNOWN"), choice_list=["mystery"])
        self.assertIsNone(Harness()._get_structured_event_decision(state))

    def test_pending_event_clears_on_floor_change(self):
        agent = Harness()
        agent._pending_event = {"event_id": "Old", "floor": 9}
        agent._pending_grid = {"purpose": "purge"}
        agent._sync_pending_event_state(make_state())
        self.assertIsNone(agent._pending_event)
        self.assertIsNone(agent._pending_grid)

    def test_event_phase_change_counts_as_effective_action(self):
        before = make_state(event("FORCED", [choice(0)]), choice_list=["continue"])
        after = before.model_copy(deep=True)
        after.event.phase = "RESULT"
        self.assertTrue(_is_action_effective(
            before, after, type("Action", (), {"type": ActionType.CHOOSE})()
        ))

    def test_structured_effects_apply_without_text_parsing(self):
        try:
            selectcard_root = os.path.join(os.path.dirname(os.path.dirname(os.path.dirname(__file__))), "selectcard")
            if selectcard_root not in sys.path:
                sys.path.insert(0, selectcard_root)
            from src.inference import STSInferenceEngine
        except ModuleNotFoundError as exc:
            if exc.name == "torch":
                self.skipTest("PyTorch is not installed in this Python environment")
            raise
        engine = STSInferenceEngine.__new__(STSInferenceEngine)
        base = {"hp": 20, "max_hp": 40, "gold": 99, "deck": ["Strike_R", "Regret"], "relics": ["Anchor"]}
        result = engine._apply_choice(copy.deepcopy(base), {
            "action": "composite_event",
            "effects": [
                {"type": "lose_hp", "amount": 7},
                {"type": "remove_card", "card_id": "Strike_R", "amount": 1},
                {"type": "add_card", "card_id": "Doubt", "amount": 2},
                {"type": "lose_all_gold"},
            ],
        })
        self.assertEqual(13, result["hp"])
        self.assertNotIn("Strike_R", result["deck"])
        self.assertEqual(2, result["deck"].count("Doubt"))
        self.assertEqual(0, result["gold"])

    def test_random_relic_keeps_pool_instead_of_fake_relic_id(self):
        state = make_state(event("COMPLEX", [choice(0, [{"type": "random_relic", "pool": "RARE"}])]), choice_list=["relic"])
        effect = state.event.choices[0].outcomes[0].effects[0]
        self.assertEqual("RARE", effect.pool)
        self.assertIsNone(effect.relic_id)

    def test_match_game_remembers_revealed_cards_and_completes_known_pair(self):
        agent = Harness()
        first_reveal = make_state(event("COMPLEX", [
            match_choice(2, enabled=False, card_id="Inflame"),
            match_choice(5, action_index=0),
        ]), choice_list=["slot 5"])
        first_reveal.event.class_name = "GremlinMatchGame"
        first_reveal.event.phase = "PLAY"
        self.assertEqual(0, agent._get_structured_event_decision(first_reveal).choice_index)

        second_reveal = make_state(event("COMPLEX", [
            match_choice(2, action_index=0),
            match_choice(5, enabled=False, card_id="Inflame"),
            match_choice(9, action_index=1),
        ]), choice_list=["slot 2", "slot 9"])
        second_reveal.event.class_name = "GremlinMatchGame"
        second_reveal.event.phase = "PLAY"
        self.assertEqual(0, agent._get_structured_event_decision(second_reveal).choice_index)

    def test_match_game_waits_while_cards_are_resolving(self):
        state = make_state(event("COMPLEX", [
            match_choice(1, enabled=False, card_id="Doubt"),
            match_choice(4, enabled=False, card_id="Strike_R"),
        ]))
        state.event.class_name = "GremlinMatchGame"
        state.event.phase = "PLAY"
        self.assertEqual(ActionType.WAIT, Harness()._get_structured_event_decision(state).type)


if __name__ == "__main__":
    unittest.main()
