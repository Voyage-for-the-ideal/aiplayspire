import copy
import unittest

from sts_ai_framework.models import (Card, CurrentMapNodeState, EventRoomChances,
    GameState, KeyState, MapChoiceState, MapNodeState, PlayerState, RelicState)
from sts_ai_framework.routing.context import RouteContext
from sts_ai_framework.routing.map_graph import MapGraph
from sts_ai_framework.routing.planner import RoutePlanner
from sts_ai_framework.routing.policy import RoutePolicyConfig
from sts_ai_framework.routing.resource import ResourceEvaluator, ResourceRoutePlanner
from sts_ai_framework.routing.strength import deck_profile
from sts_ai_framework.routing.telemetry import RouteForecastTracker


def card(cid="Strike_R", damage=6, block=None, kind="ATTACK", cost=1, **kwargs):
    return Card(index=0, uuid=cid, id=cid, name=cid, cost=cost, cost_for_turn=cost,
                type=kind, damage=damage, block=block, **kwargs)


def state(rooms=("M", "R", "E"), hp=60, maximum=80, **updates):
    nodes = [MapNodeState(x=0, y=i+1, symbol=room, children=(
        [{"x": 0, "y": i+2}] if i+1 < len(rooms) else [])) for i, room in enumerate(rooms)]
    result = GameState(player=PlayerState(current_hp=hp, max_hp=maximum, block=0,
        energy=3, gold=120), screen_type="MAP", room_phase="COMPLETE", floor=1,
        act=1, character="IRONCLAD", ascension_level=20, seed_group="test",
        deck=[card() for _ in range(5)] + [card("Defend_R", damage=None, block=5,
             kind="SKILL") for _ in range(4)] + [card("Bash", damage=8, cost=2)],
        keys=KeyState(), map_nodes=nodes,
        current_map_choices=[MapChoiceState(choice_index=0, x=0, y=1, symbol=rooms[0])],
        current_map_node=CurrentMapNodeState(x=0, y=0, symbol="M"), choice_list=[rooms[0]])
    return result.model_copy(update=updates)


def evaluator(value):
    return ResourceEvaluator(MapGraph(value.map_nodes), RoutePolicyConfig(), value)


class ResourceRoutingTests(unittest.TestCase):
    def projection(self, value):
        result = ResourceRoutePlanner().choose(value)
        return result, result.candidates[0].diagnostics

    def test_numeric_fields_are_optional_and_keep_zero(self):
        value = card(damage=0)
        self.assertEqual(value.damage, 0)
        self.assertIsNone(value.base_damage)
        self.assertIsNone(card(damage=None).damage)

    def test_same_ratio_lower_absolute_hp_has_greater_risk(self):
        low, _ = self.projection(state(("E",), hp=35, maximum=35))
        high, _ = self.projection(state(("E",), hp=75, maximum=75))
        self.assertLess(low.score, high.score)

    def test_strength_uses_damage_not_rarity(self):
        weak = state(("E",))
        strong = weak.model_copy(deep=True)
        for c in strong.deck:
            if c.damage:
                c.damage *= 3
        self.assertGreater(deck_profile(strong).frontload, deck_profile(weak).frontload)
        a, _ = self.projection(weak)
        b, _ = self.projection(strong)
        self.assertGreater(b.score, a.score)
        other = weak.model_copy(deep=True)
        for c in other.deck:
            c.rarity = "RARE"
        self.assertEqual(deck_profile(weak), deck_profile(other))

    def test_catalyst_requires_poison_and_focus_requires_orbs(self):
        a = state(deck=[card("Catalyst", damage=None, kind="SKILL")])
        b = state(deck=a.deck + [card("Deadly Poison", damage=None, kind="SKILL")])
        self.assertGreater(deck_profile(b).scaling, deck_profile(a).scaling)
        a = state(deck=[card("Defragment", damage=None, kind="POWER")])
        b = state(deck=a.deck + [card("Zap", damage=None, kind="SKILL")])
        self.assertGreater(deck_profile(b).scaling, deck_profile(a).scaling)

    def test_curses_dilute_strength(self):
        a = state()
        b = a.model_copy(update={"deck": a.deck + [card("Injury", damage=None, kind="CURSE")] * 5})
        self.assertLess(deck_profile(b).frontload, deck_profile(a).frontload)

    def test_conditional_powers_do_not_invent_an_engine(self):
        for cid in ("Rupture", "Loop", "Consume", "MentalFortress", "Like Water"):
            with self.subTest(cid=cid):
                value = state(deck=[card(cid, damage=None, kind="POWER")])
                self.assertEqual(deck_profile(value).scaling, 0)

    def test_boss_changes_reserve_without_reading_next_boss_or_seed(self):
        value = state(("R",), visible_boss="Champ")
        _, champ = self.projection(value)
        _, collector = self.projection(value.model_copy(update={"visible_boss": "The Collector"}))
        self.assertNotEqual(champ["boss_reserve"], collector["boss_reserve"])
        self.assertEqual(self.projection(value)[0], self.projection(value.model_copy(update={
            "next_boss": "Corrupt Heart", "seed_group": "other"}))[0])

    def test_each_fight_uses_updated_hp_and_gold(self):
        _, info = self.projection(state(("M", "M")))
        first, second = info["trajectory"]
        self.assertEqual(first["after"], second["before"])
        self.assertGreater(second["risk"], first["risk"])
        self.assertGreater(second["after"]["gold"], first["after"]["gold"])

    def test_camp_heal_is_capped_and_mutually_exclusive(self):
        value = state(("R",), hp=79)
        ev = evaluator(value)
        ctx = RouteContext.from_game_state(value)
        options = ev.transitions("R", False, ctx, ev.initial(ctx))
        for after, parts, action, _ in options:
            if action == "rest":
                self.assertEqual(after.hp, 80)
                self.assertEqual(after.upgrades, 0)
                self.assertEqual(after.ruby, 0)
                self.assertEqual(parts.key, 0)
            if action == "recall":
                self.assertEqual(after.hp, 79)
                self.assertEqual(after.upgrades, 0)

    def test_coffee_hammer_and_bloom(self):
        value = state(("R",), relics=[RelicState(id=r, name=r) for r in (
            "Coffee Dripper", "Fusion Hammer")])
        ev = evaluator(value)
        ctx = RouteContext.from_game_state(value)
        options = ev.transitions("R", False, ctx, ev.initial(ctx))
        self.assertEqual([o[2] for o in options], ["recall"])
        value = state(("R",), relics=[RelicState(id="Mark of the Bloom", name="Mark of the Bloom")])
        ev, ctx = evaluator(value), RouteContext.from_game_state(value)
        rest = next(o for o in ev.transitions("R", False, ctx, ev.initial(ctx)) if o[2] == "rest")
        self.assertEqual(rest[0].hp, value.player.current_hp)

    def test_shops_spend_gold_and_second_shop_is_less_valuable(self):
        _, info = self.projection(state(("$", "$")))
        first, second = info["trajectory"]
        self.assertEqual(first["after"]["gold"], 0)
        self.assertLess(second["value"], first["value"])

    def test_key_reward_is_one_time(self):
        value = state(("E", "E"))
        for node in value.map_nodes:
            node.burning = True
        _, info = self.projection(value)
        a, b = info["trajectory"]
        self.assertGreater(a["key_value"], 0)
        self.assertEqual(b["key_value"], 0)

    def test_zero_question_branch_is_not_applied(self):
        value = state(("?",), event_chances=EventRoomChances(
            event=1, monster=0, elite=0, treasure=0, shop=0))
        _, info = self.projection(value)
        self.assertEqual(info["trajectory"][0]["after"]["hp"], 60)
        self.assertEqual(info["trajectory"][0]["after"]["gold"], 120)

    def test_question_probability_changes_resource_loss(self):
        a = state(("?",), event_chances=EventRoomChances(event=1, monster=0, elite=0, treasure=0, shop=0))
        b = a.model_copy(update={"event_chances": EventRoomChances(event=0, monster=1, elite=0, treasure=0, shop=0)})
        self.assertGreater(self.projection(a)[1]["terminal"]["hp"], self.projection(b)[1]["terminal"]["hp"])

    def test_prudent_death_stays_unsafe_after_rest(self):
        result, info = self.projection(state(("E", "R"), hp=5))
        self.assertFalse(result.candidates[0].safe)
        self.assertLess(info["trajectory"][0]["after"]["prudent_hp"], 0)

    def test_deterministic_and_no_input_mutation(self):
        value = state()
        original = copy.deepcopy(value)
        self.assertEqual(self.projection(value), self.projection(value))
        self.assertEqual(original, value)

    def test_missing_graph_degrades_and_missing_fields_report_coverage(self):
        value = state(map_nodes=[])
        decision = ResourceRoutePlanner().choose(value)
        self.assertEqual(decision.mode, "IMMEDIATE_ONLY")
        self.assertEqual(decision.choice_index, 0)
        profile = deck_profile(state(deck=[card("UnknownPower", damage=None, kind="POWER")]))
        self.assertEqual(profile.coverage, 0)
        self.assertEqual(profile.unknown_ids, ("UnknownPower",))

    def test_baseline_does_not_acquire_resource_diagnostics(self):
        value = state()
        self.assertFalse(RoutePlanner().choose(value).candidates[0].diagnostics)

    def test_wing_charge_consumed_before_and_inside_path(self):
        value = state(("M", "M"), relics=[RelicState(id="WingedGreaves", name="Wing Boots", counter=2)])
        value.current_map_choices[0].winged = True
        value.map_nodes[0].children[0].winged = True
        _, info = self.projection(value)
        self.assertEqual(info["trajectory"][0]["before"]["wings"], 1)
        self.assertEqual(info["terminal"]["wings"], 0)

    def test_tracker_waits_for_next_map_and_reports_prediction_error(self):
        value = state(("M",))
        _, info = self.projection(value)
        tracker = RouteForecastTracker()
        tracker.begin(value, info)
        self.assertIsNone(tracker.observe(value))
        fighting = value.model_copy(update={"screen_type": "NONE", "floor": 2})
        self.assertIsNone(tracker.observe(fighting))
        after = value.model_copy(update={"floor": 2})
        result = tracker.observe(after)
        self.assertEqual(result["status"], "matched")
        self.assertGreater(result["hp_error"], 0)
        self.assertIsNone(tracker.observe(after))


if __name__ == "__main__":
    unittest.main()
