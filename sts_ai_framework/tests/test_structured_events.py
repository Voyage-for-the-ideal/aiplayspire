import copy
import os
import sys
import unittest
from types import SimpleNamespace
from unittest.mock import Mock

from sts_ai_framework.llm_agent_parts.action_mixin import ActionMixin
from sts_ai_framework.llm_agent_parts.choice_mixin import ChoiceMixin
from sts_ai_framework.llm_agent_parts.decision_mixin import DecisionMixin
from sts_ai_framework.llm_agent_parts.event_expectimax import decide_event, event_state_payload, score_choice
from sts_ai_framework.llm_agent_parts.info_prompt_mixin import InfoPromptMixin
from sts_ai_framework.models import ActionType, Card, GameState, RelicState, RewardChoice
from sts_ai_framework.policy_runtime import PolicyAgent
from sts_ai_framework.__main__ import _is_action_effective


def make_state(event=None, hp=50, choice_list=None):
    return GameState(
        player={"current_hp": hp, "max_hp": 80, "block": 0, "energy": 0, "gold": 100},
        deck=[], hand=[], draw_pile=[], discard_pile=[], exhaust_pile=[],
        draw_pile_size=0, discard_pile_size=0, exhaust_pile_size=0,
        monsters=[], floor=10, act=1, room_phase="EVENT", screen_type="EVENT",
        choice_list=choice_list or [], event=event, character="IRONCLAD",
    )


def card(card_id="Bash", name=None, index=0, choice_index=None, upgrades=0,
         can_upgrade=True):
    return {
        "index": index,
        "choice_index": choice_index,
        "uuid": f"test-{index}-{card_id}",
        "id": card_id,
        "name": name or card_id,
        "cost": 1,
        "cost_for_turn": 1,
        "upgrades": upgrades,
        "can_upgrade": can_upgrade,
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


def library_event(offers):
    return {
        "id": "The Library", "class_name": "TheLibrary", "phase": "0",
        "semantics_status": "KNOWN", "decision_kind": "COMPLEX",
        "choices": [
            choice(0, [{"type": "choose_generated_card", "count": 1, "card_ids": offers}],
                   "GENERATED_CARD_GRID"),
            choice(1, [{"type": "gain_hp", "amount": 20}]),
        ],
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


class LearnedKeyValueEngine(SapphireValueEngine):
    supports_key_value = True

    def __init__(self, relic_gain=0.0, sapphire_gain=0.0, ruby_gain=0.0):
        super().__init__(relic_gain)
        self.sapphire_gain = sapphire_gain
        self.ruby_gain = ruby_gain
        self.choices = None

    def evaluate_state(self, state):
        keys = state.get("keys") or {}
        return (
            super().evaluate_state(state)
            + self.sapphire_gain * bool(keys.get("sapphire"))
            + self.ruby_gain * bool(keys.get("ruby"))
        )

    def _apply_choice(self, state, choice):
        if choice.get("action") == "take_key":
            result = copy.deepcopy(state)
            result.setdefault("keys", {})[choice["target"]] = True
            return result
        return super()._apply_choice(state, choice)

    def recommend_choice(self, state, choices, exclude_purge_ids=None):
        self.choices = choices
        return max(choices, key=lambda choice: self.evaluate_state(
            self._apply_choice(state, choice)
        ))

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


class Harness(DecisionMixin, ChoiceMixin, ActionMixin, InfoPromptMixin):
    def __init__(self, value_engine=None):
        self.value_engine = value_engine
        self._pending_event = None
        self._pending_grid = None
        self.last_screen_type = "NONE"
        self.knowledge_base = SimpleNamespace(cards={})
        self.game_client = None


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

    def test_knowing_skull_compares_hp_and_the_exact_card_with_gold(self):
        from selectcard.src.inference import STSInferenceEngine

        options = event("COMPLEX", [
            choice(0, [{"type": "lose_hp", "amount": 7}, {"type": "random_potion"}], "EVENT"),
            choice(1, [{"type": "lose_hp", "amount": 7}, {"type": "gain_gold", "amount": 90}], "EVENT"),
            choice(2, [{"type": "lose_hp", "amount": 8}, {
                "type": "random_card", "pool": "COLORLESS_UNCOMMON", "card_id": "Panacea",
            }], "EVENT"),
            choice(3, [{"type": "lose_hp", "amount": 7}]),
        ])
        options.update(id="Knowing Skull", class_name="KnowingSkull", phase="ASK")
        state = make_state(options)
        state.deck = [Card(**card("Bash"))]
        state.relics = [RelicState(id=rid, name=rid) for rid in ("Toxic Egg 2", "CeramicFish")]
        current = event_state_payload(state)
        engine = STSInferenceEngine.__new__(STSInferenceEngine)
        engine.evaluate_state = lambda after: (
            after["hp"] + 0.1 * after["gold"] + 20 * ("Panacea+1" in after["deck"])
        )
        card_choice = state.event.choices[2]
        score, _, effects = score_choice(state, card_choice, engine, current)
        self.assertAlmostEqual(72.9, score)  # 42 HP + 10.9 gold value + 20 for upgraded Panacea.
        self.assertEqual(["lose_hp", "add_card", "gain_gold"], [effect["type"] for effect in effects])
        self.assertEqual(["Bash"], current["deck"])
        self.assertEqual(50, current["hp"])
        self.assertEqual(2, Harness(engine)._get_structured_event_decision(state).choice_index)
        for preview in ("Trip", None):
            with self.subTest(preview=preview):
                card_choice.outcomes[0].effects[1].card_id = preview
                self.assertEqual(1, Harness(engine)._get_structured_event_decision(state).choice_index)

    def test_library_compares_best_single_card_with_capped_heal(self):
        offers = [f"card-{i}" for i in range(20)]
        state = make_state(library_event(offers), hp=50, choice_list=["read", "sleep"])
        observed_decks = []

        class Engine(FakeValueEngine):
            def evaluate_state(self, state):
                observed_decks.append(tuple(state["deck"]))
                return state["hp"] + (15 if state["deck"] == ["card-17"] else 0)

        engine = Engine()
        self.assertEqual(1, Harness(engine).choose_action(state).choice_index)
        self.assertEqual([deck for deck in observed_decks if deck], [(c,) for c in offers])
        self.assertEqual([], state.deck)

        state.player.current_hp = 70  # Sleeping is now worth only 10 HP.
        action, best = decide_event(state, engine)
        self.assertEqual(0, action.choice_index)
        self.assertEqual(85, best["_score"])
        self.assertEqual([{"type": "add_card", "card_id": "card-17", "amount": 1}], best["effects"])
        agent = Harness(engine)
        self.assertEqual(0, agent.choose_action(state).choice_index)
        self.assertEqual("GENERATED_CARD_GRID", agent._pending_event["followup"])

    def test_library_draft_has_no_skip_candidate_and_requires_full_preview(self):
        offers = [f"card-{i}" for i in range(20)]
        state = make_state(library_event(offers), hp=50)

        class Engine(FakeValueEngine):
            def evaluate_state(self, state):
                return state["hp"] - len(state["deck"])

        engine = Engine()
        read = state.event.choices[0]
        score, _, _ = score_choice(state, read, engine, event_state_payload(state))
        self.assertEqual(49, score)
        for invalid in ([], offers[:-1], [offers[0]] * 20):
            state.event = type(state.event).model_validate(library_event(invalid))
            current = event_state_payload(state)
            self.assertIsNone(score_choice(state, state.event.choices[0], engine, current))
            self.assertEqual(1, decide_event(state, engine)[0].choice_index)

    def test_library_simulated_card_obtain_uses_eggs_and_ceramic_fish(self):
        from selectcard.src.inference import STSInferenceEngine

        offers = ["Anger", "Inflame", "Shrug It Off"] + [f"card-{i}" for i in range(17)]
        state = make_state(library_event(offers))
        current = event_state_payload(state)
        current["relics"] = ["Molten Egg 2", "CeramicFish"]
        engine = STSInferenceEngine.__new__(STSInferenceEngine)
        engine.evaluate_state = lambda payload: payload["gold"] + 10 * ("Anger+1" in payload["deck"])
        score, _, effects = score_choice(state, state.event.choices[0], engine, current)
        self.assertEqual(119, score)
        self.assertEqual({"type": "add_card", "card_id": "Anger", "amount": 1}, effects[0])
        self.assertEqual({"type": "gain_gold", "amount": 9}, effects[1])

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
        # Other events with this shape still use the generic scorer.
        agent = Harness(FakeValueEngine())
        state = make_state(event("COMPLEX", [
            choice(0, [{"type": "lose_all_gold"}]),
            choice(1, [{"type": "start_combat", "encounter": "Masked Bandits"}], "COMBAT"),
        ]), choice_list=["give gold", "fight"])
        action = agent.choose_action(state)
        self.assertEqual(ActionType.CHOOSE, action.type)
        self.assertEqual(0, action.choice_index)

    def test_named_event_combat_threshold_precedes_both_models(self):
        bandits = event("COMPLEX", [
            choice(0, [{"type": "lose_all_gold"}]),
            dict(choice(1, [{"type": "start_combat", "encounter": "Masked Bandits"}], "COMBAT"), kind="COMBAT"),
        ])
        bandits.update(id="Masked Bandits", class_name="MaskedBandits")
        sphere = event("COMPLEX", [
            dict(choice(0, [{"type": "start_combat", "encounter": "2 Orb Walkers"},
                            {"type": "random_relic", "pool": "RARE"}], "COMBAT"), kind="COMBAT"),
            dict(choice(1), kind="LEAVE"),
        ])
        sphere.update(id="Mysterious Sphere", class_name="MysteriousSphere")
        for menu, combat, noncombat in [(bandits, 1, 0), (sphere, 0, 1)]:
            for hp, max_hp, expected in [
                (7, 80, noncombat), (8, 80, combat), (9, 80, combat), (8, 81, noncombat),
            ]:
                with self.subTest(event=menu["id"], hp=hp, max_hp=max_hp):
                    state = make_state(menu, hp=hp, choice_list=["甲", "乙"])
                    state.player.max_hp = max_hp
                    value_engine = Mock()
                    agent = Harness(value_engine)
                    policy = object.__new__(PolicyAgent)
                    policy.baseline = agent
                    policy.mode = "policy"
                    policy.enabled_types = {"EVENT"}
                    policy._score = Mock()
                    action = policy.choose_action(state)
                    self.assertEqual(ActionType.CHOOSE, action.type)
                    self.assertEqual(expected, action.choice_index)
                    self.assertEqual("combat_hp_threshold", policy.last_behavior["reason"])
                    value_engine.evaluate_state.assert_not_called()
                    policy._score.assert_not_called()

    def test_sphere_fight_confirmation_remains_forced(self):
        confirm = event("FORCED", [dict(choice(0, followup="COMBAT"), kind="CONTINUE")])
        confirm.update(id="Mysterious Sphere", class_name="MysteriousSphere", phase="PRE_COMBAT")
        agent = Harness()
        action = agent.choose_action(make_state(confirm, hp=7, choice_list=["继续"]))
        self.assertEqual(ActionType.CHOOSE, action.type)
        self.assertEqual(0, action.choice_index)
        self.assertEqual("COMBAT", agent._pending_event["followup"])

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

    def test_grid_choice_matches_suffixed_target(self):
        state = make_state(choice_list=["Defend+"])
        state.screen_type = "GRID"
        state.grid_purpose = "upgrade"
        state.grid_cards = [Card.model_validate(card(
            "Defend_R", "Defend+", choice_index=0, upgrades=1))]
        agent = Harness()
        agent._pending_grid = {
            "purpose": "upgrade", "target_ids": ["Defend_R+1"],
            "num_to_select": 1, "selected_count": 0,
        }

        action = agent._handle_grid_selection(state)

        self.assertEqual(ActionType.CHOOSE, action.type)
        self.assertEqual(0, action.choice_index)
        self.assertIsNotNone(agent._pending_grid)
        self.assertEqual(["Defend_R+1"], agent._pending_grid["target_ids"])

    def test_astrolabe_grid_selects_three_distinct_duplicate_instances(self):
        state = make_state(choice_list=["strike", "strike", "strike", "defend"])
        state.screen_type = "GRID"
        state.grid_purpose = "transform"
        state.grid_num_cards = 3
        state.deck = [Card.model_validate(card("Strike_P", index=i, choice_index=i))
                      for i in range(3)] + [Card.model_validate(card("Defend_P", index=3, choice_index=3))]
        state.grid_cards = list(state.deck)

        class UniqueRanking(GridRankingEngine):
            def rank_cards_for_purpose(self, state, purpose, count, exclude_ids=None):
                return ["Strike_P", "Defend_P"]

        agent = Harness(UniqueRanking())
        for index in range(3):
            state.grid_selected_count = index
            state.grid_selected_cards = state.grid_cards[:index]
            action = agent._handle_grid_selection(state)
            self.assertEqual(ActionType.CHOOSE, action.type)
            self.assertEqual(index, action.choice_index)
        self.assertEqual(["Strike_P"] * 3, agent._pending_grid["target_ids"])

    def test_transform_fills_mandatory_slots_with_legal_curse(self):
        state = make_state(choice_list=["strike", "defend", "curse", "curse"])
        state.screen_type = "GRID"
        state.grid_purpose = "transform"
        state.grid_num_cards = 3
        cards = [card("Strike_P", index=0, choice_index=0),
                 card("Defend_P", index=1, choice_index=1),
                 card("Regret", index=2, choice_index=2),
                 card("Doubt", index=3, choice_index=3)]
        cards[2]["type"] = cards[3]["type"] = "CURSE"
        state.deck = [Card.model_validate(c) for c in cards]
        state.grid_cards = list(state.deck)
        agent = Harness(GridRankingEngine())
        self.assertEqual(0, agent._handle_grid_selection(state).choice_index)
        self.assertEqual(["Strike_P", "Defend_P", "Regret"], agent._pending_grid["target_ids"])
        state.grid_selected_count = 2
        state.grid_selected_cards = state.grid_cards[:2]
        self.assertEqual(2, agent._handle_grid_selection(state).choice_index)

    def test_generated_card_grid_uses_value_model_not_llm(self):
        state = make_state(choice_list=["cloak and dagger", "cleave"])
        state.screen_type = "GRID"
        state.grid_purpose = "generated_card_reward"
        state.can_cancel = True
        state.grid_cards = [
            Card.model_validate(card("CloakAndDagger", "Cloak and Dagger", choice_index=0)),
            Card.model_validate(card("Cleave", "Cleave", index=1, choice_index=1)),
        ]
        agent = Harness(FakeValueEngine())
        action = agent.choose_action(state)
        self.assertEqual(ActionType.CHOOSE, action.type)
        self.assertEqual(1, action.choice_index)
        self.assertEqual(["CloakAndDagger", "Cleave"],
                         [c["effects"][0]["card_id"] for c in agent.value_engine.choices])

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

    def test_shop_purge_exclusion_survives_suffix(self):
        state = make_state(choice_list=["Purge (50 gold)", "Leave"])
        state.screen_type = "SHOP_SCREEN"
        bottled = card("Strike_R", "Strike+", index=0, upgrades=1)
        bottled["is_bottled"] = True
        state.deck = [Card.model_validate(bottled),
                      Card.model_validate(card("Strike_R", "Strike", index=1))]
        engine = PurgeChoosingEngine()

        Harness(engine)._get_model_shop_decision(state)

        self.assertIn("Strike_R+1", engine.exclude_purge_ids)
        self.assertNotIn("Strike_R", engine.exclude_purge_ids)

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

    def test_learned_ruby_key_is_a_rest_candidate_before_act_three(self):
        state = make_state(choice_list=["RestOption", "RecallOption"])
        state.screen_type = "REST"
        state.act = 1
        engine = LearnedKeyValueEngine(ruby_gain=10.0)
        action = Harness(engine)._handle_rest_room(state)
        self.assertEqual(ActionType.CHOOSE, action.type)
        self.assertEqual(1, action.choice_index)
        self.assertIn("take_key", [choice["action"] for choice in engine.choices])

    def test_smith_intent_is_a_suffixed_reference(self):
        state = make_state(choice_list=["SmithOption"])
        state.screen_type = "REST"
        state.deck = [Card.model_validate(card(
            "Searing Blow", "Searing Blow+2", upgrades=2))]
        agent = Harness(FakeValueEngine())

        action = agent._handle_rest_room(state)

        self.assertEqual(ActionType.CHOOSE, action.type)
        self.assertEqual("Searing Blow+2", agent.intended_smith_card)
        self.assertEqual(["Searing Blow+2"], agent._pending_grid["target_ids"])

    def test_smith_skips_upgraded_copy_and_keeps_unupgraded(self):
        state = make_state(choice_list=["SmithOption"])
        state.screen_type = "REST"
        state.deck = [
            Card.model_validate(card("Bash", index=0)),
            Card.model_validate(card("Bash", "Bash+", index=1,
                                     upgrades=1, can_upgrade=False)),
        ]
        engine = FakeValueEngine()

        Harness(engine)._handle_rest_room(state)

        self.assertEqual(["Bash"], [choice["target"] for choice in engine.choices
                                    if choice["action"] == "upgrade_card"])

    def test_smith_keeps_upgraded_searing_blow_without_can_upgrade(self):
        state = make_state(choice_list=["SmithOption"])
        state.screen_type = "REST"
        legacy_card = card("Searing Blow", "Searing Blow+2", upgrades=2)
        del legacy_card["can_upgrade"]
        state.deck = [Card.model_validate(legacy_card)]
        engine = FakeValueEngine()

        Harness(engine)._handle_rest_room(state)

        self.assertEqual(["Searing Blow+2"],
                         [choice["target"] for choice in engine.choices
                          if choice["action"] == "upgrade_card"])

    def test_card_reward_prefers_structured_upgrades(self):
        state = make_state(choice_list=["[Bash+] Cost: 2", "[Bash] Cost: 2"])
        state.screen_type = "CARD_REWARD"
        state.card_reward_cards = [
            Card.model_validate(card("Bash", "Bash+", index=0, upgrades=1)),
            Card.model_validate(card("Bash", "Bash", index=1)),
        ]
        state.reward_card_ids = ["Bash", "Bash"]
        engine = FakeValueEngine()

        Harness(engine)._get_model_card_decision(state)

        self.assertEqual(["Bash+1", "Bash"],
                         [choice["target"] for choice in engine.choices[:2]])
        self.assertEqual({"action": "skip", "target": None, "index": -1},
                         engine.choices[-1])

    def test_card_reward_falls_back_to_reward_card_ids(self):
        state = make_state(choice_list=["[Bash] Cost: 2"])
        state.screen_type = "CARD_REWARD"
        state.reward_card_ids = ["Bash"]
        engine = FakeValueEngine()

        Harness(engine)._get_model_card_decision(state)

        self.assertEqual("Bash", engine.choices[0]["target"])

    def test_card_reward_falls_back_to_visible_card(self):
        state = make_state(choice_list=["[Defend+] Cost: 1"])
        state.screen_type = "CARD_REWARD"
        state.card_reward_cards = []
        state.reward_card_ids = []
        state.hand = [Card.model_validate(card(
            "Defend_R", "Defend+", upgrades=1))]
        engine = FakeValueEngine()

        Harness(engine)._get_model_card_decision(state)

        self.assertEqual("Defend_R+1", engine.choices[0]["target"])

    def test_iter_visible_cards_includes_reward_and_grid(self):
        state = make_state()
        state.hand = [Card.model_validate(card("Bash"))]
        state.card_reward_cards = [Card.model_validate(card("Defend_R"))]
        state.grid_cards = [Card.model_validate(card(
            "Strike_R", upgrades=1))]

        visible = Harness()._iter_visible_cards(state)

        self.assertEqual(["Bash", "Defend_R", "Strike_R"],
                         [entry.id for entry in visible])

    def test_visible_card_info_resolves_reward_card(self):
        state = make_state(choice_list=["[Defend] Cost: 1"])
        state.screen_type = "CARD_REWARD"
        state.card_reward_cards = [Card.model_validate(card("Defend_R", "Defend"))]
        agent = Harness()
        agent.knowledge_base.cards["Defend_R"] = "Gain 5 Block."

        self.assertEqual("Gain 5 Block.",
                         agent._get_choice_card_info(state, state.choice_list[0]))

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

    def test_key_aware_checkpoint_compares_complete_post_states(self):
        state = make_state(choice_list=["relic", "sapphire key"])
        state.screen_type = "COMBAT_REWARD"
        state.reward_choices = [
            RewardChoice(choice_index=0, type="RELIC", relic_id="Anchor"),
            RewardChoice(choice_index=1, type="SAPPHIRE_KEY", linked_reward_index=0),
        ]
        action = Harness(LearnedKeyValueEngine(
            relic_gain=0.08, sapphire_gain=0.10
        ))._handle_combat_reward(state)
        self.assertEqual(ActionType.CHOOSE, action.type)
        self.assertEqual(1, action.choice_index)

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
