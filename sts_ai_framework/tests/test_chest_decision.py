import copy
import os
import sys
import unittest

from sts_ai_framework.event_pools import CHEST_CURSE_POOL, chest_relic_pool
from sts_ai_framework.llm_agent_parts.action_mixin import ActionMixin
from sts_ai_framework.llm_agent_parts.choice_mixin import ChoiceMixin
from sts_ai_framework.llm_agent_parts.decision_mixin import DecisionMixin
from sts_ai_framework.models import ActionType, GameState, RelicState


def make_chest_state(relics=None, choice_list=None, can_proceed=True,
                     chest_boss=False, gold_reward=True, gold_amount=50,
                     relic_tier="uncommon", act=2):
    state = GameState(
        player={"current_hp": 50, "max_hp": 80, "block": 0, "energy": 0, "gold": 100},
        deck=[], hand=[], draw_pile=[], discard_pile=[], exhaust_pile=[],
        draw_pile_size=0, discard_pile_size=0, exhaust_pile_size=0,
        monsters=[], floor=14, act=act, room_phase="COMPLETE", screen_type="CHEST",
        choice_list=choice_list or ["open"],
    )
    state.relics = relics or []
    state.can_proceed = can_proceed
    state.chest_boss = chest_boss
    state.chest_gold_reward = gold_reward
    state.chest_gold_amount = gold_amount
    state.chest_relic_tier = relic_tier
    return state


class ChestValueEngine:
    """可调评分引擎：遗物加分、诅咒减分，用于驱动 open/skip 两个方向。"""

    def __init__(self, relic_bonus=5.0, curse_cost=1.0):
        self.relic_bonus = relic_bonus
        self.curse_cost = curse_cost
        self.applied_effects = []

    def evaluate_state(self, state):
        curses = sum(1 for c in state["deck"] if c in CHEST_CURSE_POOL)
        score = (state["hp"] + 0.5 * state["max_hp"] + 0.01 * state["gold"]
                 + self.relic_bonus * len(state["relics"]) - self.curse_cost * curses)
        if (state.get("key_states") or {}).get("sapphire"):
            score += self.relic_bonus
        return score

    def _apply_choice(self, state, choice):
        result = copy.deepcopy(state)
        for effect in choice.get("effects", []):
            self.applied_effects.append(effect)
            kind = effect["type"]
            amount = effect.get("amount", 1) or 1
            if kind == "gain_gold":
                result["gold"] += amount
            elif kind == "obtain_relic":
                result["relics"].append(effect["relic_id"])
            elif kind == "add_card":
                result["deck"].extend([effect["card_id"]] * max(1, amount))
        return result


class Harness(DecisionMixin, ChoiceMixin, ActionMixin):
    def __init__(self, value_engine=None):
        self.value_engine = value_engine
        self._pending_event = None
        self._pending_grid = None
        self.last_screen_type = "NONE"


def cursed_key_engine(relic_bonus=5.0, curse_cost=1.0):
    return ChestValueEngine(relic_bonus=relic_bonus, curse_cost=curse_cost)


class ChestDecisionTests(unittest.TestCase):
    def test_no_cursed_key_auto_opens_without_scoring(self):
        engine = cursed_key_engine(curse_cost=999.0)
        agent = Harness(engine)
        state = make_chest_state(relics=[RelicState(id="Anchor", name="Anchor")])
        action = agent.choose_action(state)
        self.assertEqual(ActionType.CHOOSE, action.type)
        self.assertEqual(0, action.choice_index)
        self.assertEqual([], engine.applied_effects)

    def test_cursed_key_skips_when_curse_outweighs_relic(self):
        engine = cursed_key_engine(relic_bonus=0.0, curse_cost=10.0)
        agent = Harness(engine)
        state = make_chest_state(relics=[RelicState(id="Cursed Key", name="Cursed Key")])
        action = agent.choose_action(state)
        self.assertEqual(ActionType.PROCEED, action.type)
        # 枚举覆盖：10 张诅咒 × 该档位全部遗物候选都进入过打分
        added_curses = {e["card_id"] for e in engine.applied_effects if e["type"] == "add_card"}
        self.assertEqual(set(CHEST_CURSE_POOL), added_curses)
        relic_ids = {e["relic_id"] for e in engine.applied_effects if e["type"] == "obtain_relic"}
        expected_pool = {rid for rid, _ in chest_relic_pool("uncommon", "DEFECT", {"Cursed Key"})}
        self.assertEqual(expected_pool, relic_ids)

    def test_cursed_key_opens_when_relic_outweighs_curse(self):
        engine = cursed_key_engine(relic_bonus=50.0, curse_cost=1.0)
        agent = Harness(engine)
        state = make_chest_state(relics=[RelicState(id="Cursed Key", name="Cursed Key")])
        action = agent.choose_action(state)
        self.assertEqual(ActionType.CHOOSE, action.type)
        self.assertEqual(0, action.choice_index)
        gold_effects = [e for e in engine.applied_effects if e["type"] == "gain_gold"]
        self.assertTrue(all(e["amount"] == 50 for e in gold_effects))

    def test_boss_chest_always_opens_without_scoring(self):
        engine = cursed_key_engine(curse_cost=999.0)
        agent = Harness(engine)
        state = make_chest_state(
            relics=[RelicState(id="Cursed Key", name="Cursed Key")],
            chest_boss=True, gold_reward=False, relic_tier=None)
        action = agent.choose_action(state)
        self.assertEqual(ActionType.CHOOSE, action.type)
        self.assertEqual(0, action.choice_index)
        self.assertEqual([], engine.applied_effects)

    def test_without_value_engine_falls_back_to_auto_open(self):
        agent = Harness(None)
        state = make_chest_state(relics=[RelicState(id="Cursed Key", name="Cursed Key")])
        action = agent.choose_action(state)
        self.assertEqual(ActionType.CHOOSE, action.type)
        self.assertEqual(0, action.choice_index)

    def test_skip_without_proceed_button_waits(self):
        engine = cursed_key_engine(relic_bonus=0.0, curse_cost=10.0)
        agent = Harness(engine)
        state = make_chest_state(
            relics=[RelicState(id="Cursed Key", name="Cursed Key")], can_proceed=False)
        action = agent.choose_action(state)
        self.assertEqual(ActionType.WAIT, action.type)

    def test_old_server_without_chest_fields_auto_opens(self):
        engine = cursed_key_engine(curse_cost=999.0)
        agent = Harness(engine)
        state = make_chest_state(relics=[RelicState(id="Cursed Key", name="Cursed Key")])
        state.chest_gold_reward = None
        state.chest_relic_tier = None
        action = agent.choose_action(state)
        self.assertEqual(ActionType.CHOOSE, action.type)
        self.assertEqual([], engine.applied_effects)


class RealEngineEffectTests(unittest.TestCase):
    def test_chest_effects_apply_and_omamori_blocks_any_curse(self):
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
        base = {"hp": 20, "max_hp": 40, "gold": 99, "deck": ["Strike_R"],
                "relics": ["Anchor"], "relic_states": [{"id": "Omamori", "name": "Omamori", "counter": 2}]}
        result = engine._apply_choice(copy.deepcopy(base), {
            "action": "composite_event",
            "effects": [
                {"type": "gain_gold", "amount": 50},
                {"type": "obtain_relic", "relic_id": "Pear"},
                {"type": "add_card", "card_id": "Decay"},
            ],
        })
        self.assertEqual(149, result["gold"])
        self.assertIn("Pear", result["relics"])
        self.assertNotIn("Decay", result["deck"])
        # 每次拦截消耗一枚 Omamori；两枚护符耗尽后第三张 Decay 才真正进入牌组
        result2 = engine._apply_choice(result, {
            "action": "composite_event",
            "effects": [{"type": "add_card", "card_id": "Decay"}],
        })
        self.assertNotIn("Decay", result2["deck"])
        self.assertEqual(0, result2["relic_states"][0]["counter"])
        result3 = engine._apply_choice(result2, {
            "action": "composite_event",
            "effects": [{"type": "add_card", "card_id": "Decay"}],
        })
        self.assertIn("Decay", result3["deck"])


if __name__ == "__main__":
    unittest.main()
