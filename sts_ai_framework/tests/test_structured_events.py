import copy
import os
import sys
import unittest

from sts_ai_framework.llm_agent_parts.action_mixin import ActionMixin
from sts_ai_framework.llm_agent_parts.choice_mixin import ChoiceMixin
from sts_ai_framework.llm_agent_parts.decision_mixin import DecisionMixin
from sts_ai_framework.models import ActionType, Card, GameState, RewardChoice
from sts_ai_framework.__main__ import _is_action_effective


def make_state(event=None, hp=50, choice_list=None):
    return GameState(
        player={"current_hp": hp, "max_hp": 80, "block": 0, "energy": 0, "gold": 100},
        deck=[], hand=[], draw_pile=[], discard_pile=[], exhaust_pile=[],
        draw_pile_size=0, discard_pile_size=0, exhaust_pile_size=0,
        monsters=[], floor=10, act=1, room_phase="EVENT", screen_type="EVENT",
        choice_list=choice_list or [], event=event, character="IRONCLAD",
    )


def card(card_id="Bash", name=None, index=0, choice_index=None):
    return {
        "index": index,
        "choice_index": choice_index,
        "uuid": f"test-{index}-{card_id}",
        "id": card_id,
        "name": name or card_id,
        "cost": 1,
        "cost_for_turn": 1,
    }


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

def cursed_tome_event(phase, choices, status="KNOWN"):
    return {
        "id": "Cursed Tome",
        "class_name": "CursedTome",
        "phase": phase,
        "semantics_status": status,
        "decision_kind": "COMPLEX",
        "choices": choices,
    }


class FakeValueEngine:
    """最小价值引擎：同时满足 legacy recommend_choice 与 expectimax 的三个接口。

    evaluate_state = hp + 0.5*max_hp + 0.01*gold + relic_bonus*len(relics)。
    """

    def __init__(self):
        self.choices = None
        self.exclude_purge_ids = None
        self.applied_effects = []
        self.relic_bonus = 20.0

    def recommend_choice(self, state, choices, exclude_purge_ids=None):
        self.choices = choices
        self.exclude_purge_ids = exclude_purge_ids
        return choices[-1]

    def evaluate_state(self, state):
        return (state["hp"] + 0.5 * state["max_hp"] + 0.01 * state["gold"]
                + self.relic_bonus * len(state["relics"]))

    def _apply_choice(self, state, choice):
        result = copy.deepcopy(state)
        for effect in choice.get("effects", []):
            self.applied_effects.append(effect["type"])
            kind = effect["type"]
            amount = effect.get("amount", 1) or 1
            if kind == "lose_hp":
                result["hp"] -= amount
            elif kind == "gain_hp":
                result["hp"] = min(result["max_hp"], result["hp"] + amount)
            elif kind == "gain_max_hp":
                result["max_hp"] += amount
            elif kind == "lose_max_hp":
                result["max_hp"] -= amount
            elif kind == "gain_gold":
                result["gold"] += amount
            elif kind in ("lose_gold",):
                result["gold"] -= amount
            elif kind == "lose_all_gold":
                result["gold"] = 0
            elif kind == "remove_card":
                result["deck"] = [c for c in result["deck"] if c != effect.get("card_id")]
            elif kind == "add_card":
                result["deck"].extend([effect["card_id"]] * max(1, amount))
            elif kind == "obtain_relic":
                result["relics"].append(effect["relic_id"])
            elif kind == "lose_relic":
                result["relics"] = [r for r in result["relics"] if r != effect.get("relic_id")]
        return result

    def rank_cards_for_purpose(self, state, purpose, count, exclude_ids=None):
        excluded = exclude_ids or set()
        return [card_id for card_id in state["deck"] if card_id not in excluded][:count]


class PurgeChoosingEngine(FakeValueEngine):
    def recommend_choice(self, state, choices, exclude_purge_ids=None):
        self.choices = choices
        self.exclude_purge_ids = exclude_purge_ids
        return next(choice for choice in choices if choice["action"] == "remove_card")


class SapphireValueEngine:
    def __init__(self, relic_gain):
        self.relic_gain = relic_gain

    def evaluate_state(self, state):
        return 0.5 + (self.relic_gain if "Anchor" in state["relics"] else 0.0)

    def _apply_choice(self, state, choice):
        result = copy.deepcopy(state)
        result["relics"].append(choice["target"])
        return result

class CursedTomeValueEngine:
    def __init__(self):
        self.last_apply = None

    def evaluate_state(self, state):
        curse_count = state["cards"].count("Necronomicurse") if state.get("cards") else 0
        return state["hp"] + 50 * len(state["relics"]) - 20 * curse_count

    def _apply_choice(self, state, choice):
        self.last_apply = choice
        result = copy.deepcopy(state)
        result.setdefault("relics", [])
        result.setdefault("cards", [])
        for effect in choice.get("effects", []):
            amount = effect.get("amount", 0) or 0
            if effect["type"] == "lose_hp":
                result["hp"] -= amount
            elif effect["type"] == "obtain_relic":
                result["relics"].append(effect["relic_id"])
            elif effect["type"] == "add_card":
                result["cards"].extend([effect["card_id"]] * max(1, amount))
        return result


class GridRankingEngine:
    def __init__(self):
        self.ranked_decks = []
        self.exclude_ids = []

    def rank_cards_for_purpose(self, state, purpose, count, exclude_ids=None):
        self.ranked_decks.append(list(state["deck"]))
        self.exclude_ids.append(set(exclude_ids or ()))
        excluded = exclude_ids or set()
        return [card_id for card_id in state["deck"] if card_id not in excluded][:count]


class Harness(DecisionMixin, ChoiceMixin, ActionMixin):
    def __init__(self, value_engine=None):
        self.value_engine = value_engine
        self._pending_event = None
        self._pending_grid = None
        self.last_screen_type = "NONE"


class StructuredEventTests(unittest.TestCase):
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

    def test_event_transition_without_best_keeps_existing_grid_target(self):
        state = make_state(event("FORCED", [choice(0, [
            {"type": "select_cards", "purpose": "PURGE", "count": 1},
        ], "GRID")]), choice_list=["offer"])
        state.event.phase = "RESULT"
        agent = Harness()
        pending = {
            "event_id": "Test Event",
            "event_phase": "INTRO",
            "purpose": "purge",
            "target_ids": ["Defend_R"],
            "num_to_select": 1,
            "selected_count": 0,
        }
        agent._pending_grid = pending.copy()

        agent._remember_event_followup(state, 0, best=None)

        self.assertEqual(["Defend_R"], agent._pending_grid["target_ids"])

    def test_deterministic_choices_go_to_value_engine(self):
        engine = FakeValueEngine()
        state = make_state(event("DETERMINISTIC", [
            choice(0, [{"type": "gain_gold", "amount": 10}]),
            choice(1, [{"type": "gain_max_hp", "amount": 5}]),
        ]), choice_list=["gold", "max hp"])
        action = Harness(engine)._get_structured_event_decision(state)
        self.assertEqual(1, action.choice_index)
        self.assertIn("gain_max_hp", engine.applied_effects)

    def test_expectimax_excludes_guaranteed_lethal_choice(self):
        engine = FakeValueEngine()
        state = make_state(event("DETERMINISTIC", [
            choice(0, [{"type": "lose_hp", "amount": 50}]),
            choice(1, [{"type": "gain_gold", "amount": 10}]),
        ]), hp=50, choice_list=["die", "gold"])
        action = Harness(engine)._get_structured_event_decision(state)
        self.assertEqual(1, action.choice_index)

    def test_stochastic_outcome_scores_probability_weighted_expectation(self):
        # ScrapOoze 型：0.5 出遗物 / 0.5 空，均 -3 HP；遗物值钱时挖，不值钱时走
        def dig_event():
            return event("COMPLEX", [
                {
                    "button_index": 0, "action_index": 0, "enabled": True,
                    "label": "dig", "kind": "STOCHASTIC", "followup": "NONE",
                    "outcomes": [
                        {"probability": 0.5, "effects": [
                            {"type": "lose_hp", "amount": 3},
                            {"type": "random_relic", "pool": "RARE"},
                        ]},
                        {"probability": 0.5, "effects": [{"type": "lose_hp", "amount": 3}]},
                    ],
                },
                choice(1, []),
            ])

        engine = FakeValueEngine()
        engine.relic_bonus = 100.0
        state = make_state(dig_event(), hp=50, choice_list=["dig", "leave"])
        self.assertEqual(0, Harness(engine)._get_structured_event_decision(state).choice_index)

        engine = FakeValueEngine()
        engine.relic_bonus = 0.0
        state = make_state(dig_event(), hp=50, choice_list=["dig", "leave"])
        self.assertEqual(1, Harness(engine)._get_structured_event_decision(state).choice_index)

    def test_expectimax_select_cards_sets_grid_intent(self):
        engine = FakeValueEngine()
        state = make_state(event("DETERMINISTIC", [
            choice(0, [{"type": "select_cards", "purpose": "PURGE", "count": 1}]),
            choice(1, []),
        ]), choice_list=["purge", "leave"])
        state.deck = [
            Card.model_validate(card("Strike_R", "Strike", index=0)),
            Card.model_validate(card("Defend_R", "Defend", index=1)),
        ]
        agent = Harness(engine)
        action = agent._get_structured_event_decision(state)
        self.assertEqual(ActionType.CHOOSE, action.type)
        self.assertEqual("purge", agent._pending_grid["purpose"])
        self.assertIn("Strike_R", agent._pending_grid["target_ids"])

    def test_combat_only_event_enters_forced_combat(self):
        # Colosseum INTRO：唯一 enabled 选项是战斗 → 直接进入
        state = make_state(event("COMPLEX", [
            choice(0, [{"type": "start_combat", "encounter": "Slavers"}], "COMBAT"),
        ]))
        action = Harness(FakeValueEngine())._get_structured_event_decision(state)
        self.assertEqual(ActionType.CHOOSE, action.type)
        self.assertEqual(0, action.choice_index)

    def test_event_with_combat_alternative_never_reaches_llm(self):
        # MaskedBandits 型：战斗选项不可打分，交钱选项不优于 no-op → 决策器放弃，
        # choose_action 层安全回退选中交钱（index 0），全程不触碰 LLM。
        agent = Harness(FakeValueEngine())
        state = make_state(event("COMPLEX", [
            choice(0, [{"type": "lose_all_gold"}]),
            choice(1, [{"type": "start_combat", "encounter": "Masked Bandits"}], "COMBAT"),
        ]), choice_list=["give gold", "fight"])
        action = agent.choose_action(state)
        self.assertEqual(ActionType.CHOOSE, action.type)
        self.assertEqual(0, action.choice_index)

    def test_unknown_event_uses_safe_default_never_llm(self):
        # UNKNOWN 事件 + 无价值引擎：文本解析跳过，安全回退选中第一个安全选项
        agent = Harness(None)
        state = make_state(event("UNKNOWN", [choice(0), choice(1)], status="UNKNOWN"),
                           choice_list=["mystery a", "mystery b"])
        action = agent.choose_action(state)
        self.assertEqual(ActionType.CHOOSE, action.type)
        self.assertEqual(0, action.choice_index)

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

    def test_grid_confirm_is_authoritative_even_if_selected_count_lags(self):
        state = make_state(choice_list=["confirm", "bash"])
        state.screen_type = "GRID"
        state.grid_purpose = "upgrade"
        state.grid_num_cards = 1
        state.grid_selected_count = 0
        state.deck = [Card.model_validate(card())]
        agent = Harness()
        agent._pending_grid = {"purpose": "upgrade", "target_ids": ["Bash"], "num_to_select": 1}
        action = agent._handle_grid_selection(state)
        self.assertEqual(ActionType.CHOOSE, action.type)
        self.assertEqual(0, action.choice_index)

    def test_generated_card_grid_uses_value_model_not_llm(self):
        state = make_state(choice_list=["cloak and dagger"])
        state.screen_type = "GRID"
        state.grid_purpose = "generated_card_reward"
        state.can_cancel = True
        state.grid_cards = [Card.model_validate(card("CloakAndDagger", "Cloak and Dagger", choice_index=0))]
        agent = Harness(FakeValueEngine())
        action = agent.choose_action(state)
        self.assertEqual(ActionType.CHOOSE, action.type)
        self.assertEqual(0, action.choice_index)
        self.assertEqual("CloakAndDagger", agent.value_engine.choices[0]["target"])
        self.assertEqual(1, len(agent.value_engine.choices))

    def test_on_the_fly_purge_only_ranks_cards_offered_by_grid(self):
        state = make_state(choice_list=["Defend"])
        state.screen_type = "GRID"
        state.grid_purpose = "purge"
        state.grid_num_cards = 1
        state.deck = [
            Card.model_validate(card("AscendersBane", "Ascender's Bane", index=0)),
            Card.model_validate(card("Defend_R", "Defend", index=1)),
        ]
        state.grid_cards = [
            Card.model_validate(card("Defend_R", "Defend", index=1, choice_index=0)),
        ]
        engine = GridRankingEngine()
        agent = Harness(engine)

        action = agent._handle_grid_selection(state)

        self.assertEqual(ActionType.CHOOSE, action.type)
        self.assertEqual(0, action.choice_index)
        self.assertEqual(["Defend_R"], agent._pending_grid["target_ids"])
        self.assertEqual([["AscendersBane", "Defend_R"]], engine.ranked_decks)
        self.assertIn("AscendersBane", engine.exclude_ids[0])

    def test_shop_purge_excludes_unpurgeable_cards(self):
        state = make_state(choice_list=["Purge (50 gold)", "Leave"])
        state.screen_type = "SHOP_SCREEN"
        bottled = card("Strike_R", "Strike", index=2)
        bottled["is_bottled"] = True
        state.deck = [
            Card.model_validate(card("AscendersBane", "Ascender's Bane")),
            Card.model_validate(card("Necronomicurse", "Necronomicurse", index=1)),
            Card.model_validate(card("CurseOfTheBell", "Curse of the Bell", index=4)),
            Card.model_validate(bottled),
            Card.model_validate(card("Defend_R", "Defend", index=3)),
        ]
        engine = PurgeChoosingEngine()
        agent = Harness(engine)

        agent._get_model_shop_decision(state)

        self.assertIn("AscendersBane", engine.exclude_purge_ids)
        self.assertIn("Necronomicurse", engine.exclude_purge_ids)
        self.assertIn("CurseOfTheBell", engine.exclude_purge_ids)
        self.assertIn("Strike_R", engine.exclude_purge_ids)
        self.assertNotIn("Defend_R", engine.exclude_purge_ids)
        self.assertEqual("remove_card", engine.choices[0]["action"])

    def test_shop_purge_keeps_id_with_an_unbottled_copy(self):
        state = make_state(choice_list=["Purge (50 gold)", "Leave"])
        state.screen_type = "SHOP_SCREEN"
        bottled = card("Strike_R", "Strike", index=0)
        bottled["is_bottled"] = True
        state.deck = [
            Card.model_validate(bottled),
            Card.model_validate(card("Strike_R", "Strike", index=1)),
        ]
        engine = PurgeChoosingEngine()
        agent = Harness(engine)

        agent._get_model_shop_decision(state)

        self.assertNotIn("Strike_R", engine.exclude_purge_ids)
        self.assertTrue(any(choice["action"] == "remove_card" for choice in engine.choices))

    def test_shop_omits_purge_when_no_legal_target(self):
        state = make_state(choice_list=["Purge (50 gold)", "Leave"])
        state.screen_type = "SHOP_SCREEN"
        state.deck = [Card.model_validate(card("AscendersBane"))]
        engine = FakeValueEngine()
        agent = Harness(engine)

        agent._get_model_shop_decision(state)

        self.assertEqual(["skip"], [choice["action"] for choice in engine.choices])

    def test_stale_pending_purge_retargets_live_grid_card(self):
        state = make_state(choice_list=["Defend"])
        state.screen_type = "GRID"
        state.grid_purpose = "purge"
        state.grid_num_cards = 1
        state.deck = [
            Card.model_validate(card("AscendersBane", "Ascender's Bane")),
            Card.model_validate(card("Defend_R", "Defend", index=1)),
        ]
        state.grid_cards = [
            Card.model_validate(card("Defend_R", "Defend", index=1, choice_index=0)),
        ]
        engine = GridRankingEngine()
        agent = Harness(engine)
        agent._pending_grid = {
            "purpose": "purge", "target_ids": ["AscendersBane"],
            "num_to_select": 1, "selected_count": 0,
        }

        action = agent._handle_grid_selection(state)

        self.assertEqual(ActionType.CHOOSE, action.type)
        self.assertEqual(0, action.choice_index)
        self.assertEqual(["Defend_R"], agent._pending_grid["target_ids"])
        self.assertEqual([["Defend_R"]], engine.ranked_decks)

    def test_stale_pending_purge_cancels_when_grid_has_no_legal_card(self):
        state = make_state(choice_list=["cancel"])
        state.screen_type = "GRID"
        state.grid_purpose = "purge"
        state.grid_num_cards = 1
        state.can_cancel = True
        state.deck = [Card.model_validate(card("AscendersBane"))]
        state.grid_cards = []
        engine = GridRankingEngine()
        agent = Harness(engine)
        agent._pending_grid = {
            "purpose": "purge", "target_ids": ["AscendersBane"],
            "num_to_select": 1, "selected_count": 0,
        }

        action = agent._handle_grid_selection(state)

        self.assertEqual(ActionType.CANCEL, action.type)
        self.assertIsNone(agent._pending_grid)

    def test_act_three_rest_recalls_when_ruby_key_is_missing(self):
        state = make_state(choice_list=["RestOption", "SmithOption", "RecallOption"])
        state.screen_type = "REST"
        state.act = 3
        action = Harness(FakeValueEngine())._handle_rest_room(state)
        self.assertEqual(ActionType.CHOOSE, action.type)
        self.assertEqual(2, action.choice_index)

    def test_boss_value_model_cannot_choose_synthetic_skip(self):
        state = make_state(choice_list=["Black Star"])
        state.screen_type = "BOSS_REWARD"
        action = Harness(FakeValueEngine())._get_model_boss_reward_decision(state)
        self.assertEqual(ActionType.CHOOSE, action.type)
        self.assertEqual(0, action.choice_index)

    def test_structured_combat_key_beats_proceed_and_linked_relic(self):
        state = make_state(choice_list=["relic", "sapphire key"])
        state.screen_type = "COMBAT_REWARD"
        state.act = 3
        state.can_proceed = True
        state.reward_choices = [
            RewardChoice(choice_index=0, type="RELIC", relic_id="Anchor"),
            RewardChoice(choice_index=1, type="SAPPHIRE_KEY", linked_reward_index=0),
        ]
        action = Harness()._handle_combat_reward(state)
        self.assertEqual(ActionType.CHOOSE, action.type)
        self.assertEqual(1, action.choice_index)

    def test_act_one_strong_linked_relic_can_beat_sapphire_rule_value(self):
        state = make_state(choice_list=["relic", "sapphire key"])
        state.screen_type = "COMBAT_REWARD"
        state.reward_choices = [
            RewardChoice(choice_index=0, type="RELIC", relic_id="Anchor"),
            RewardChoice(choice_index=1, type="SAPPHIRE_KEY", linked_reward_index=0),
        ]
        action = Harness(SapphireValueEngine(relic_gain=0.08))._handle_combat_reward(state)
        self.assertEqual(ActionType.CHOOSE, action.type)
        self.assertEqual(0, action.choice_index)

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

    def test_cursed_tome_intro_prefers_reading_when_books_are_valuable(self):
        state = make_state(cursed_tome_event("INTRO", [
            {
                "button_index": 0,
                "action_index": 0,
                "enabled": True,
                "label": "read",
                "kind": "EFFECT",
                "followup": "EVENT",
                "outcomes": [{"probability": 1.0, "effects": [
                    {
                        "type": "commit_reading",
                        "unavoidable_hp_loss": 6,
                        "final_dmg": 10,
                        "book_relics": ["Circlet", "Necronomicon"],
                    },
                ]}],
            },
            {
                "button_index": 1,
                "action_index": 1,
                "enabled": True,
                "label": "leave",
                "kind": "LEAVE",
                "followup": "MAP",
                "outcomes": [{"probability": 1.0, "effects": []}],
            },
        ]), hp=40)
        engine = CursedTomeValueEngine()
        agent = Harness(engine)
        action = agent._get_structured_event_decision(state)
        self.assertEqual(0, action.choice_index)
        self.assertIsNotNone(agent._pending_event)
        self.assertEqual(True, agent._pending_event.get("cursed_tome_book_path"))
        self.assertEqual(10, agent._pending_event.get("cursed_tome_final_dmg"))
        self.assertEqual(["Circlet", "Necronomicon"], agent._pending_event.get("cursed_tome_book_relics"))

    def test_cursed_tome_local_read_path_survives_all_pages_until_last(self):
        engine = CursedTomeValueEngine()
        agent = Harness(engine)
        intro_state = make_state(cursed_tome_event("INTRO", [
            {
                "button_index": 0,
                "action_index": 0,
                "enabled": True,
                "label": "read",
                "kind": "EFFECT",
                "followup": "EVENT",
                "outcomes": [{"probability": 1.0, "effects": [
                    {
                        "type": "commit_reading",
                        "unavoidable_hp_loss": 6,
                        "final_dmg": 10,
                        "book_relics": ["Circlet"],
                    },
                ]}],
            },
            {
                "button_index": 1,
                "action_index": 1,
                "enabled": True,
                "label": "leave",
                "kind": "LEAVE",
                "followup": "MAP",
                "outcomes": [{"probability": 1.0, "effects": []},
                ],
            },
        ]), hp=40)
        self.assertEqual(0, agent._get_structured_event_decision(intro_state).choice_index)
        self.assertTrue(agent._pending_event.get("cursed_tome_book_path"))

        for phase in ("PAGE_1", "PAGE_2", "PAGE_3"):
            page_state = make_state(cursed_tome_event(phase, [
                {
                    "button_index": 0,
                    "action_index": 0,
                    "enabled": True,
                    "label": "continue",
                    "kind": "CONTINUE",
                    "followup": "EVENT",
                    "outcomes": [{"probability": 1.0, "effects": [{"type": "lose_hp", "amount": 1}]},
                    ],
                },
            ]), hp=40)
            self.assertEqual(0, agent._get_structured_event_decision(page_state).choice_index)
            self.assertTrue(agent._pending_event.get("cursed_tome_book_path"))
            self.assertEqual(phase, agent._pending_event.get("phase"))

        last_state = make_state(cursed_tome_event("LAST_PAGE", [
            {
                "button_index": 0,
                "action_index": 0,
                "enabled": True,
                "label": "continue",
                "kind": "RANDOM_REWARD",
                "followup": "NONE",
                "outcomes": [{"probability": 1.0, "effects": [
                    {"type": "lose_hp", "amount": 18},
                    {"type": "random_relic", "pool": "BOOK"},
                ]}],
            },
            {
                "button_index": 1,
                "action_index": 1,
                "enabled": True,
                "label": "leave",
                "kind": "LEAVE",
                "followup": "NONE",
                "outcomes": [{"probability": 1.0, "effects": [{"type": "lose_hp", "amount": 3}]},
                ],
            },
        ]), hp=40)
        self.assertEqual(0, agent._get_structured_event_decision(last_state).choice_index)
        self.assertTrue(agent._pending_event.get("cursed_tome_book_path"))

    def test_cursed_tome_last_page_with_no_pending_prefers_leaving_when_read_is_impossible(self):
        state = make_state(cursed_tome_event("LAST_PAGE", [
            {
                "button_index": 0,
                "action_index": 0,
                "enabled": True,
                "label": "continue",
                "kind": "RANDOM_REWARD",
                "followup": "NONE",
                "outcomes": [{"probability": 1.0, "effects": [
                    {"type": "lose_hp", "amount": 40},
                    {"type": "random_relic", "pool": "BOOK"},
                ]}],
            },
            {
                "button_index": 1,
                "action_index": 1,
                "enabled": True,
                "label": "leave",
                "kind": "LEAVE",
                "followup": "NONE",
                "outcomes": [{"probability": 1.0, "effects": [{"type": "lose_hp", "amount": 3}]},
                ],
            },
        ]), hp=40)
        state.event.class_name = "CursedTome"
        engine = CursedTomeValueEngine()
        action = Harness(engine)._get_structured_event_decision(state)
        self.assertEqual(ActionType.CHOOSE, action.type)
        self.assertEqual(1, action.choice_index)

    def test_cursed_tome_last_page_prefers_committed_read_path(self):
        state = make_state(cursed_tome_event("LAST_PAGE", [
            {
                "button_index": 0,
                "action_index": 0,
                "enabled": True,
                "label": "continue",
                "kind": "RANDOM_REWARD",
                "followup": "NONE",
                "outcomes": [{"probability": 1.0, "effects": [
                    {"type": "lose_hp", "amount": 18},
                    {"type": "random_relic", "pool": "BOOK"},
                ]}],
            },
            {
                "button_index": 1,
                "action_index": 1,
                "enabled": True,
                "label": "leave",
                "kind": "LEAVE",
                "followup": "NONE",
                "outcomes": [{"probability": 1.0, "effects": [{"type": "lose_hp", "amount": 3}]},
                ],
            },
        ]), hp=40)
        state.event.class_name = "CursedTome"
        state.floor = 10
        engine = CursedTomeValueEngine()
        agent = Harness(engine)
        agent._pending_event = {
            "event_id": "Cursed Tome",
            "floor": 10,
            "cursed_tome_book_path": True,
            "cursed_tome_final_dmg": 18,
            "cursed_tome_book_relics": ["Circlet"],
        }
        action = agent._get_structured_event_decision(state)
        self.assertEqual(ActionType.CHOOSE, action.type)
        self.assertEqual(0, action.choice_index)


class EventPoolsTests(unittest.TestCase):
    def test_any_pool_excludes_owned_and_normalizes_weights(self):
        from sts_ai_framework.event_pools import relic_pool
        pool = relic_pool("ANY", "DEFECT", {"Vajra", "Anchor"})
        self.assertTrue(pool)
        self.assertAlmostEqual(1.0, sum(weight for _, weight in pool), places=6)
        self.assertNotIn("Vajra", [relic_id for relic_id, _ in pool])
        self.assertNotIn("Anchor", [relic_id for relic_id, _ in pool])

    def test_any_pool_merges_shared_and_class_relics(self):
        from sts_ai_framework.event_pools import relic_pool
        ids = [relic_id for relic_id, _ in relic_pool("ANY", "IRONCLAD", set())]
        self.assertIn("Akabeko", ids)          # shared common
        self.assertIn("Red Skull", ids)        # ironclad common

    def test_rare_pool_only_contains_rare_tier(self):
        from sts_ai_framework.event_pools import relic_pool
        ids = [relic_id for relic_id, _ in relic_pool("RARE", "IRONCLAD", set())]
        self.assertIn("Dead Branch", ids)      # shared rare
        self.assertIn("Champion Belt", ids)    # ironclad rare
        self.assertNotIn("Akabeko", ids)       # common 不应在 RARE 池

    def test_book_pool_falls_back_to_circlet_when_all_owned(self):
        from sts_ai_framework.event_pools import relic_pool
        owned = {"Necronomicon", "Enchiridion", "Nilry's Codex"}
        self.assertEqual([("Circlet", 1.0)], relic_pool("BOOK", "DEFECT", owned))

    def test_unknown_pool_returns_empty(self):
        from sts_ai_framework.event_pools import relic_pool
        self.assertEqual([], relic_pool("MYSTERY", "DEFECT", set()))


if __name__ == "__main__":
    unittest.main()
