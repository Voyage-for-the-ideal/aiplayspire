"""State-faithfulness regressions; no checkpoint, game process or training needed."""

import copy
import unittest
from unittest.mock import patch

from selectcard.src.inference import STSInferenceEngine
from sts_ai_framework.llm_agent_parts.event_expectimax import (
    SCOREABLE, UNSUPPORTED, classify_choice, guaranteed_hp_loss, score_choice,
)
from sts_ai_framework.models import Card, EventChoiceState, RelicState
from sts_ai_framework.tests.test_structured_events import (
    FakeValueEngine, Harness, card, choice, event, make_state,
)


class LegacyTextTests(unittest.TestCase):
    def setUp(self):
        self.agent = Harness(FakeValueEngine())

    def effects(self, text):
        return self.agent._parse_event_effects(text)[1]

    def test_gold_does_not_invent_a_relic(self):
        self.assertEqual(self.effects("[ Obtain 100 Gold ]"),
                         [{"type": "gain_gold", "amount": 100}])

    def test_random_card_has_no_phantom_card_or_anchor(self):
        self.assertEqual(self.effects("[ Obtain a random rare Card ]"),
                         [{"type": "random_card"}])
        state = make_state(choice_list=["[ Obtain a random rare Card ]", "[ Obtain 100 Gold ]"])
        self.assertEqual(self.agent._get_model_event_decision(state).choice_index, 1)

    def test_random_relic_keeps_tier_and_uses_pool(self):
        self.assertEqual(self.effects("[ Take 18 damage Obtain a random rare Relic ]"),
                         [{"type": "lose_hp", "amount": 18},
                          {"type": "random_relic", "pool": "RARE"}])
        state = make_state(choice_list=["[ Obtain a random common Relic ]", "Leave"])
        with patch("sts_ai_framework.llm_agent_parts.event_expectimax.chest_relic_pool",
                   return_value=[("Pear", 1.0)]) as pool:
            self.assertEqual(self.agent._get_model_event_decision(state).choice_index, 0)
        self.assertEqual(pool.call_args.args[0], "COMMON")

    def test_boss_swap_keeps_loss_and_unresolved_boss_pool(self):
        state = make_state()
        state.relics = [RelicState(id="Burning Blood", name="Burning Blood")]
        effects = self.agent._parse_event_effects(
            "[ Lose your starting Relic Obtain a random boss Relic ]", state)[1]
        self.assertEqual(effects, [{"type": "lose_relic", "relic_id": "Burning Blood"},
                                   {"type": "random_relic", "pool": "BOSS"}])

    def test_explicit_named_entities_are_preserved(self):
        self.assertEqual(self.effects("Obtain Relic: [Anchor]"),
                         [{"type": "obtain_relic", "relic_id": "Anchor"}])
        self.assertEqual(self.effects("Obtain Card: [Anger]"),
                         [{"type": "add_card", "card_id": "Anger"}])
        self.assertEqual(self.effects("[ Obtain Anchor ]"),
                         [{"type": "obtain_relic", "relic_id": "Anchor"}])

    def test_vanilla_neow_max_hp_and_lament_labels(self):
        self.assertEqual(self.effects("[ #gMax #gHP #g+7 ]"),
                         [{"type": "gain_max_hp", "amount": 7}])
        self.assertEqual(self.effects("[ #rLose #r7 #rMax #rHP #gObtain #g250 #gGold ]"),
                         [{"type": "lose_max_hp", "amount": 7},
                          {"type": "gain_gold", "amount": 250}])
        self.assertEqual(self.effects("[ Enemies in your next three combats have 1 HP ]"),
                         [{"type": "obtain_relic", "relic_id": "NeowsLament"}])

    def test_numbers_belong_to_their_own_effects(self):
        self.assertEqual(self.effects("[ Take 18 damage Obtain 100 Gold ]"),
                         [{"type": "lose_hp", "amount": 18},
                          {"type": "gain_gold", "amount": 100}])
        self.assertEqual(self.effects("[ Lose all Gold Remove 2 Cards ]"),
                         [{"type": "lose_all_gold"},
                          {"type": "select_cards", "purpose": "purge", "count": 2}])

    def test_max_hp_gain_heals_only_once(self):
        effects = self.effects("[ Gain 7 Max HP ]")
        engine = STSInferenceEngine.__new__(STSInferenceEngine)
        result = engine._apply_choice(
            {"hp": 20, "max_hp": 40, "relics": []},
            {"action": "composite_event", "effects": effects})
        self.assertEqual((result["hp"], result["max_hp"]), (27, 47))


class RelicAcquisitionTests(unittest.TestCase):
    def setUp(self):
        self.engine = STSInferenceEngine.__new__(STSInferenceEngine)
        self.state = {"hp": 20, "max_hp": 40, "gold": 500,
                      "deck": ["Strike_R"], "relics": []}

    def obtain(self, state, relic):
        return self.engine._apply_choice(state, {
            "action": "composite_event", "effects": [{"type": "obtain_relic", "relic_id": relic}]})

    def test_scalar_on_equip_effects_match_shop_and_event(self):
        for relic, hp, maximum, gain in (
            ("Strawberry", 27, 47, 0), ("Pear", 30, 50, 0),
            ("Mango", 34, 54, 0), ("Lee's Waffle", 47, 47, 0),
            ("Old Coin", 20, 40, 300), ("Tiny House", 25, 45, 0),
        ):
            with self.subTest(relic=relic):
                before = copy.deepcopy(self.state)
                obtained = self.obtain(before, relic)
                bought = self.engine._apply_choice(before, {
                    "action": "buy_relic", "target": relic, "cost": 100})
                self.assertEqual((obtained["hp"], obtained["max_hp"], obtained["gold"]),
                                 (hp, maximum, 500 + gain))
                self.assertEqual(bought, dict(obtained, gold=400 + gain))
                self.assertEqual(before, self.state)

    def test_existing_relic_does_not_trigger_on_equip_again(self):
        once = self.obtain(self.state, "Pear")
        self.assertEqual(self.obtain(once, "Pear"), once)

    def test_bloom_blocks_healing_but_not_max_hp_gain(self):
        state = dict(self.state, relics=["Mark of the Bloom"])
        for relic, maximum in (("Strawberry", 47), ("Lee's Waffle", 47)):
            with self.subTest(relic=relic):
                obtained = self.obtain(state, relic)
                self.assertEqual((obtained["hp"], obtained["max_hp"]), (20, maximum))

    def test_old_coin_respects_ectoplasm_and_bloody_idol(self):
        blocked = self.obtain(dict(self.state, relics=["Ectoplasm", "Bloody Idol"]), "Old Coin")
        self.assertEqual((blocked["gold"], blocked["hp"]), (500, 20))
        healed = self.obtain(dict(self.state, relics=["Bloody Idol"]), "Old Coin")
        self.assertEqual((healed["gold"], healed["hp"]), (800, 25))

    def test_shop_uses_existing_starter_replacement_logic(self):
        result = self.engine._apply_choice(dict(self.state, relics=["Burning Blood"]),
                                          {"action": "buy_relic", "target": "Black Blood", "cost": 100})
        self.assertEqual(result["relics"], ["Black Blood"])


class MultiPurgeTests(unittest.TestCase):
    def setUp(self):
        self.engine = STSInferenceEngine.__new__(STSInferenceEngine)
        self.engine.evaluate_state = lambda state: 1.0 / (len(state["deck"]) + 1)

    def test_explicit_repeated_and_distinct_instances(self):
        before = {"deck": ["Strike_R", "Strike_R", "Defend_R", "Bash"], "relics": []}
        for effect in (
            {"type": "remove_card", "card_id": "Strike_R", "amount": 2},
            {"type": "remove_card", "card_ids": ["Strike_R", "Strike_R"]},
        ):
            with self.subTest(effect=effect):
                result = self.engine._apply_choice(before, {"action": "composite_event", "effects": [effect]})
                self.assertEqual(result["deck"], ["Defend_R", "Bash"])
        self.assertEqual(len(before["deck"]), 4)

    def test_rank_multi_purge_keeps_duplicates_and_exclusions(self):
        before = {"deck": ["Strike_R", "Strike_R", "Defend_R", "AscendersBane"], "relics": []}
        self.assertEqual(self.engine.rank_cards_for_purpose(before, "purge", 2),
                         ["Strike_R", "Strike_R"])
        self.assertEqual(self.engine.rank_cards_for_purpose(before, "purge", 2, {"Strike_R"}),
                         ["Defend_R"])

    def test_unresolved_multi_purge_scores_the_full_selection(self):
        before = {"deck": ["Strike_R", "Defend_R", "Bash"], "relics": []}
        chosen = self.engine.recommend_choice(before, [
            {"action": "skip"}, {"action": "composite_event", "effects": [
                {"type": "remove_card", "card_id": "unknown_card", "amount": 2}]}])
        self.assertEqual(chosen["_purge_intent_ids"], ["Strike_R", "Defend_R"])
        self.assertEqual(self.engine._apply_choice(before, chosen)["deck"], ["Bash"])

    def test_structured_multi_purge_preserves_full_grid_plan(self):
        state = make_state(event("DETERMINISTIC", [choice(0, [
            {"type": "select_cards", "purpose": "purge", "count": 2}], "GRID")]), choice_list=["purge"])
        state.deck = [Card.model_validate(card("Strike_R", index=0)),
                      Card.model_validate(card("Strike_R", index=1)),
                      Card.model_validate(card("AscendersBane", index=2))]
        agent = Harness(self.engine)
        self.assertEqual(agent._get_structured_event_decision(state).choice_index, 0)
        self.assertEqual(agent._pending_grid["target_ids"], ["Strike_R", "Strike_R"])
        self.assertEqual(agent._pending_grid["num_to_select"], 2)
        grid = state.model_copy(update={"event": None, "screen_type": "GRID", "grid_purpose": "purge",
                                        "grid_num_cards": 2, "grid_cards": [
                                            c.model_copy(update={"choice_index": i}) for i, c in enumerate(state.deck[:2])],
                                        "choice_list": ["Strike", "Strike"]})
        self.assertEqual(agent._handle_grid_selection(grid).choice_index, 0)
        grid.grid_selected_count = 1
        grid.grid_selected_cards = [grid.grid_cards[0]]
        self.assertEqual(agent._handle_grid_selection(grid).choice_index, 1)

    def test_incomplete_selection_does_not_score_a_partial_removal(self):
        state = make_state()
        state.deck = [Card.model_validate(card("Strike_R", index=0)),
                      Card.model_validate(card("AscendersBane", index=1))]
        current = {"deck": ["Strike_R", "AscendersBane"], "relics": []}
        value = EventChoiceState(button_index=0, action_index=0, enabled=True,
                                 label="remove two", outcomes=[{"effects": [
                                     {"type": "select_cards", "purpose": "purge", "count": 2}]}])
        self.assertIsNone(score_choice(state, value, self.engine, current))
        self.assertEqual(current["deck"], ["Strike_R", "AscendersBane"])

    def test_bottled_card_is_not_selected(self):
        state = make_state()
        state.deck = [Card.model_validate(dict(card("Strike_R", index=0), is_bottled=True)),
                      Card.model_validate(card("Defend_R", index=1)),
                      Card.model_validate(card("Bash", index=2))]
        current = {"deck": ["Strike_R", "Defend_R", "Bash"], "relics": []}
        value = EventChoiceState(button_index=0, action_index=0, enabled=True,
                                 label="remove two", outcomes=[{"effects": [
                                     {"type": "select_cards", "purpose": "purge", "count": 2}]}])
        _, intent, effects = score_choice(state, value, self.engine, current)
        self.assertEqual(intent["_purge_intent_ids"], ["Defend_R", "Bash"])
        self.assertEqual(self.engine._apply_choice(current, {
            "action": "composite_event", "effects": effects})["deck"], ["Strike_R"])


class ProbabilityTests(unittest.TestCase):
    def setUp(self):
        self.engine = FakeValueEngine()
        self.state = make_state()
        self.current = {"hp": 50, "max_hp": 80, "gold": 100, "deck": [], "relics": []}

    def scored(self, outcomes):
        value = EventChoiceState(button_index=0, action_index=0, enabled=True,
                                 label="probability", outcomes=outcomes)
        return value, score_choice(self.state, value, self.engine, self.current)

    def test_zero_branch_is_not_expanded_or_classified(self):
        value, score = self.scored([
            {"probability": 0.0, "effects": [{"type": "mini_game"}]},
            {"probability": 1.0, "effects": [{"type": "gain_gold", "amount": 100}]},
        ])
        self.assertEqual(classify_choice(value), SCOREABLE)
        self.assertAlmostEqual(score[0], 92.0)
        self.assertNotIn("mini_game", self.engine.applied_effects)

    def test_valid_distribution_stays_probability_weighted(self):
        _, score = self.scored([
            {"probability": 0.25, "effects": [{"type": "gain_gold", "amount": 100}]},
            {"probability": 0.75, "effects": []},
        ])
        self.assertAlmostEqual(score[0], 91.25)

    def test_zero_branch_does_not_hide_guaranteed_hp_loss(self):
        value, _ = self.scored([
            {"probability": 0.0, "effects": []},
            {"probability": 1.0, "effects": [{"type": "lose_hp", "amount": 50}]},
        ])
        self.assertEqual(guaranteed_hp_loss(value), 50)

    def test_zero_branch_does_not_look_up_a_relic_pool(self):
        with patch("sts_ai_framework.llm_agent_parts.event_expectimax.relic_pool",
                   side_effect=AssertionError("impossible reward expanded")):
            value, score = self.scored([
                {"probability": 0.0, "effects": [{"type": "random_relic", "pool": "BOSS"}]},
                {"probability": 1.0, "effects": []},
            ])
        self.assertEqual(classify_choice(value), SCOREABLE)
        self.assertAlmostEqual(score[0], 91.0)

    def test_invalid_or_unknown_distribution_is_unscored(self):
        for outcomes in (
            [{"probability": 0.0}], [{"probability": -0.1}], [{"probability": 1.1}],
            [{"probability": float("nan")}], [{"probability": float("inf")}],
            [{"probability": 0.2}, {"probability": 0.3}], [{}, {}],
        ):
            with self.subTest(outcomes=outcomes):
                value, score = self.scored(outcomes)
                self.assertEqual(classify_choice(value), UNSUPPORTED)
                self.assertIsNone(score)

    def test_single_unspecified_probability_is_deterministic(self):
        _, score = self.scored([{"effects": []}])
        self.assertAlmostEqual(score[0], 91.0)


if __name__ == "__main__":
    unittest.main()
