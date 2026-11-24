"""Reduced parity fixtures for aborts in the first repaired 160-case run."""
import copy
import os
from pathlib import Path
import unittest
from uuid import UUID

from battle_engine.worker import Worker
from sts_ai_framework.headless.combat import _private_match
from sts_ai_framework.headless.state import HeadlessContractError
from sts_ai_framework.tests.test_headless_combat import combat_raw


EXE = Path(os.environ.get("BATTLE_ENGINE_WORKER", Path(__file__).resolve().parents[1] / "bin/battle-engine-worker.exe"))


def card(card_id, uid, cost=1, **extra):
    return dict(card_id=card_id, uuid=str(UUID(int=uid)), cost=cost, cost_for_turn=cost,
                times_upgraded=0) | extra


class ShieldGremlinReceiptTest(unittest.TestCase):
    def test_birth_checks_constructor_constants_without_freezing_roster(self):
        s = combat_raw()["combat"]["snapshot"]
        s["ascension_level"] = 10
        m = s["cur_map_node_state"]["monster_data"][0]
        m["creature"]["id"] = "GremlinTsundere"
        m["_private"] = {"GremlinTsundere.bashDmg": 8, "GremlinTsundere.blockAmt": 8}
        self.assertTrue(_private_match({}, s))
        m["_private"]["GremlinTsundere.blockAmt"] = 7
        with self.assertRaisesRegex(HeadlessContractError, "Shield Gremlin"):
            _private_match({}, s)

    def test_hovering_kite_latch_resets_only_at_end_turn(self):
        s = combat_raw(role='THE_SILENT')["combat"]["snapshot"]
        relic={'relic_id':'HoveringKite','counter':-1,
               '_private':{'HoveringKite.triggeredThisTurn':False}}
        s['player_state']['relics'].append(relic)
        before={'relic:HoveringKite':{'HoveringKite.triggeredThisTurn':True}}
        self.assertTrue(_private_match(before,s,{'type':'END'}))
        self.assertFalse(_private_match(before,s,{'type':'CARD'}))
        relic['_private']['HoveringKite.triggeredThisTurn']=True
        self.assertFalse(_private_match(before,s,{'type':'END'}))


@unittest.skipUnless(EXE.is_file(), "Build the native worker")
class HeadlessRelease2CombatTests(unittest.TestCase):
    def setUp(self):
        self.worker = Worker(EXE)
        self.addCleanup(self.worker.close)
        self.s = combat_raw()["combat"]["snapshot"]
        self.p = self.s["player_state"]
        self.ms = self.s["cur_map_node_state"]["monster_data"]

    def request(self, operation, **kwargs):
        result = self.worker.execute({"operation": operation, "snapshot": self.s, **kwargs}, 1000)
        self.assertTrue(result["supported"], result)
        return result

    def predict(self, kind="CARD", **kwargs):
        if kind == "CARD":
            kwargs = {"card_index": 0, "monster_index": 0, **kwargs}
        return self.request("predict", command={"type": kind, **kwargs})

    def test_darkling_knockdown_keeps_nip_but_clears_powers_and_appends_regrow(self):
        for i in range(3):
            if i:
                self.ms.append(copy.deepcopy(self.ms[0]))
            m = self.ms[i]
            m["creature"].update(id="Darkling", current_health=1 if i == 0 else 50,
                                 powers=[{"power_id": "Life Link", "amount": -1},
                                         {"power_id": "PathToVictoryPower", "amount": 9}])
            m.update(next_move=2, move_history=[2], _private={
                "Darkling.chompDmg": 8, "Darkling.firstMove": False, "Darkling.nipDmg": 11})
        r = self.predict()
        m = next(x for x in r["expected_native_key"] if isinstance(x, list) and len(x) == 30)
        self.assertEqual((m[2], m[6], m[8], m[10], m[16], m[27]), (0, True, m[9], 0, 0, 11))
        self.assertEqual(r["expected_exact_rng"]["ai"], self.s["_headless_rng"]["streams"]["ai"])

    def test_gremlin_leader_victory_does_not_require_killing_minions(self):
        leader = copy.deepcopy(self.ms[0])
        leader["creature"].update(id="GremlinLeader", current_health=6, draw_x=1475)
        leader.update(next_move=2, move_history=[2], intent_name="UNKNOWN")
        self.ms[0]["creature"].update(id="GremlinFat", draw_x=1074,
                                      powers=[{"power_id": "Minion", "amount": -1}])
        self.ms[0].update(next_move=2, move_history=[2])
        self.ms.append(leader)
        self.assertEqual(self.predict(monster_index=1)["terminal_prediction"], "victory")

    def test_rally_inserts_wizard_before_a_corpse_at_its_shifted_position(self):
        leader = copy.deepcopy(self.ms[0])
        leader["creature"].update(id="GremlinLeader", draw_x=1475)
        leader.update(next_move=2, move_history=[2], intent_name="UNKNOWN")
        self.ms[0]["creature"].update(id="GremlinWizard", current_health=0, draw_x=1235)
        self.ms[0].update(next_move=2, move_history=[2])
        self.ms.append(leader)
        # Original seed's AI state before rally chooses Warrior then Wizard.
        self.s["_headless_rng"]["streams"]["ai"].update(
            state0="6016878a2e923a84", state1="1171877c3a7d174b")
        r = self.predict("END")
        roster = r["expected_after"]["monsters"]
        self.assertEqual([m["id"] for m in roster], ["GremlinWarrior", "GremlinWizard", "GremlinWizard", "GremlinLeader"])
        self.assertGreater(roster[1]["hp"], 0)
        self.assertEqual(roster[2]["hp"], 0)

    def test_flurry_moves_to_hand_in_source_discard_order(self):
        self.p["chosen_class_name"] = "WATCHER"
        self.p["stance"] = "Wrath"
        self.p["hand"] = [card("EmptyFist", 101)]
        self.p["discard_pile"] = [card("FlurryOfBlows", i, 0) for i in (102, 103)]
        r = self.predict()
        self.assertEqual(r["expected_card_uuids"]["hand"], [str(UUID(int=i)) for i in (102, 103)])

    def test_anger_copy_has_permanent_combat_cost_and_fresh_identity(self):
        self.p["hand"] = [card("Anger", 101, 1, cost_for_turn=0)]
        r = self.predict()
        self.assertEqual([(c["cost"], c["turn_cost"]) for c in r["expected_after"]["discard_pile"]], [(1, 1), (1, 1)])
        self.assertEqual(r["expected_card_uuids"]["discard_pile"], [None, str(UUID(int=101))])

    def test_medical_kit_sets_exhaust_flag_on_the_card(self):
        self.p["relics"].append({"relic_id": "Medical Kit", "counter": -1})
        self.p["hand"] = [card("Wound", 101, -2)]
        r = self.predict(monster_index=-1)
        self.assertTrue(r["expected_native_key"][-4][0][-1])
        self.assertEqual(r["expected_card_uuids"]["exhaust_pile"], [str(UUID(int=101))])

    def test_meditate_auto_moves_all_cards_at_threshold_then_ends_turn(self):
        self.p["chosen_class_name"] = "WATCHER"
        self.p["hand"] = [card("Meditate", 101, times_upgraded=1)]
        self.p["discard_pile"] = [card("Strike_P", 102), card("Defend_P", 103)]
        self.p["draw_pile"] = [card("Strike_P", i) for i in range(104, 110)]
        r = self.predict(monster_index=-1)
        self.assertEqual(r["expected_screen"], "NONE")
        self.assertEqual(r["expected_after"]["turn"], self.s["turn"] + 1)
        self.assertEqual(r["expected_card_uuids"]["hand"][:2], [str(UUID(int=i)) for i in (102, 103)])

    def test_wave_of_the_hand_double_weakness_expires_in_one_round(self):
        self.p["creature"]["powers"] = [{"power_id": "WaveOfTheHandPower", "amount": 2}]
        r = self.predict("END")
        # Debuff strength does not remain encoded in the next-turn player key.
        self.p["creature"]["powers"] = []
        control = self.predict("END")
        self.assertEqual(r["expected_native_key"], control["expected_native_key"])

    def test_ritual_dagger_updates_only_the_uuid_matched_master_card(self):
        played = card("RitualDagger", 101, times_upgraded=1, misc=15, base_damage=15)
        other = dict(played, uuid=str(UUID(int=102)))
        self.p["hand"] = [played]
        self.p["master_deck"] = [other, copy.deepcopy(played)]
        self.ms[0]["creature"]["current_health"] = 1
        self.ms.append(copy.deepcopy(combat_raw()["combat"]["snapshot"]["cur_map_node_state"]["monster_data"][0]))
        r = self.predict()
        self.assertEqual([c[1] for c in r["expected_native_key"][3]], [15, 20])

    def test_distilled_chaos_respects_velvet_choker_at_execution(self):
        self.p["chosen_class_name"] = "THE_SILENT"
        self.p["potions"][0].update(id="DistilledChaos")
        self.p["relics"].append({"relic_id": "Velvet Choker", "counter": 5})
        played = card("Defend_G", 100)
        self.s["cards_played_this_turn_backup"] = [played] * 5
        self.p["draw_pile"] = [card("Strike_G", i) for i in (101, 102, 103)]
        result = self.predict("POTION", potion_index=0, monster_index=-1)
        self.assertEqual(result["expected_after"]["monsters"][0]["hp"], 44)
        self.assertEqual(result["expected_card_uuids"]["discard_pile"], [str(UUID(int=i)) for i in (103, 102, 101)])

    def test_essence_of_darkness_evoke_uses_hp_after_previous_evoke(self):
        self.p["chosen_class_name"] = "DEFECT"
        self.p["potions"][0].update(id="EssenceOfDarkness")
        self.p.update(max_orbs=3, orbs=[
            {"lookup_key": "Lightning", "evoke_amount": 8},
            {"lookup_key": "Dark", "evoke_amount": 6},
            {"lookup_key": "EmptyOrbSlot", "evoke_amount": 0},
        ])
        self.ms.extend(copy.deepcopy(self.ms[0]) for _ in range(2))
        for m, hp in zip(self.ms, (27, 34, 32)):
            m["creature"]["current_health"] = hp
        self.s["_headless_rng"]["streams"]["card_random"].update(
            state0="2a08485594cdfca1", state1="57786ddd261abaf5")
        result = self.predict("POTION", potion_index=0, monster_index=-1)
        self.assertEqual([m["hp"] for m in result["expected_after"]["monsters"]], [27, 20, 32])
        self.assertEqual(result["expected_exact_rng"]["card_random"]["counter"], self.s["rng_state"]["card_random_rng_counter"] + 1)

    def test_shuffled_eviscerate_resets_cost_if_it_remains_in_draw(self):
        self.p["chosen_class_name"] = "THE_SILENT"
        self.p["hand"] = [card("Calculated Gamble", 101, 0), card("Defend_G", 102)]
        self.p["draw_pile"] = []
        self.p["discard_pile"] = [card("Eviscerate", 103, 3, cost_for_turn=2),
                                  *[card("Defend_G", i) for i in range(104, 110)]]
        result = self.predict(monster_index=-1)
        assert_card = next(c for c in result["expected_after"]["draw_pile"] if c["id"] == "Eviscerate")
        self.assertEqual(assert_card["turn_cost"], 3)
