"""Regression cases derived from historical Java fixes and the native audit."""
import copy
import json
from pathlib import Path
import socket
import tempfile
import threading
import types
import unittest
import os

from battle_engine import protocol
from battle_engine.native_api import execute_worker
from battle_engine.state import load, projection

ROOT = Path(__file__).resolve().parents[1]
EXE = Path(os.environ.get("BATTLE_ENGINE_WORKER", str(ROOT / "bin/battle-engine-worker.exe")))
BOSS_X = dict(GremlinLeader=35, Reptomancer=-20, BronzeAutomaton=-50, TheCollector=60, SlimeBoss=0)


def monster(id="Cultist", hp=50, move=3, x=None, **creature):
    c = dict(id=id, current_health=hp, max_health=max(hp, 50), current_block=0,
             is_dead=hp <= 0, is_escaping=False, half_dead=False, powers=[],
             draw_x=1440 + (BOSS_X.get(id, 0) if x is None else x))
    c.update(creature)
    return dict(creature=c, escaped=False, next_move=move, intent_name="BUFF", move_history=[str(move)])


@unittest.skipUnless(EXE.is_file(), "Build the native worker with battle_engine/build.ps1")
class HistoryRegressionTests(unittest.TestCase):
    def state(self):
        s = load(ROOT / "tests/fixtures/ironclad.json")
        s["player_state"]["potions"] = []
        s["player_state"]["relics"] = []
        s["player_state"]["creature"]["draw_x"] = 480
        return s

    def run_native(self, s, operation="import", **metadata):
        r = execute_worker(EXE, s, 1000, 100, operation, metadata=metadata)
        self.assertTrue(r["supported"], r)
        return r

    def end(self, s):
        return self.run_native(s, "step", action_type="END")

    def test_retaliation_cancels_dead_attackers_remaining_hits(self):
        for power in ("Thorns", "Flame Barrier"):
            with self.subTest(power=power):
                s = self.state()
                s["player_state"]["creature"]["powers"] = [dict(power_id=power, amount=4)]
                s["cur_map_node_state"]["monster_data"] = [monster("Byrd", hp=1, move=1), monster()]
                r = self.end(s)
                self.assertEqual(r["projection"]["hp"], 69)
                self.assertEqual(r["projection"]["monsters"][0]["hp"], 0)

    def test_rampage_restores_grown_damage(self):
        s = self.state()
        c = s["player_state"]["hand"][0]
        c.update(card_id="Rampage", base_damage=13, misc=0)
        r = self.run_native(s, "step", card_index=0)
        self.assertEqual(r["projection"]["monsters"][0]["hp"], 37)

    def test_different_rampage_instances_remain_separate_actions(self):
        s = self.state()
        p = s["player_state"]
        p["creature"]["current_health"] = 1
        p["energy_panel_total_energy"] = 1
        p["draw_pile"] = []
        p["hand"] = [dict(card_id="Rampage", cost=1, cost_for_turn=1, times_upgraded=0,
                          base_damage=damage, misc=0, uuid=str(damage)) for damage in (8, 13)]
        s["cur_map_node_state"]["monster_data"] = [monster(hp=10, move=1,
            powers=[dict(power_id="Strength", amount=100)])]
        command = json.loads(self.run_native(s, "search")["commands"][0]["command"])
        self.assertEqual((command["type"], command["card_index"]), ("CARD", 1))

    def test_rampage_missing_dynamic_damage_is_reported(self):
        s = self.state()
        s["player_state"]["hand"][0]["card_id"] = "Rampage"
        r = execute_worker(EXE, s, 1, 50, "import")
        self.assertFalse(r["supported"])
        self.assertIn("base_damage", r["reason"])

    def test_disarm_upgrade_and_artifact(self):
        for upgraded in (False, True):
            for artifact in (False, True):
                with self.subTest(upgraded=upgraded, artifact=artifact):
                    s = self.state()
                    s["player_state"]["hand"][0].update(card_id="Disarm", times_upgraded=int(upgraded))
                    powers = [dict(power_id="Strength", amount=1)]
                    if artifact:
                        powers.append(dict(power_id="Artifact", amount=1))
                    s["cur_map_node_state"]["monster_data"] = [monster(powers=powers)]
                    r = self.run_native(s, "step", card_index=0)
                    self.assertEqual(r["enemy_strength"], 1 if artifact else -2 if upgraded else -1)

    def test_bias_focus_loss_respects_artifact(self):
        for artifact in (False, True):
            with self.subTest(artifact=artifact):
                s = self.state()
                p = s["player_state"]
                p["chosen_class_name"] = "DEFECT"
                p["creature"]["powers"] = [dict(power_id="Focus", amount=4), dict(power_id="Bias", amount=1)]
                if artifact:
                    p["creature"]["powers"].append(dict(power_id="Artifact", amount=1))
                r = self.end(s)
                self.assertEqual(r["player_stats"]["focus"], 4 if artifact else 3)
                self.assertEqual(r["player_stats"]["artifact"], 0)

    def test_imported_wraith_form_uses_signed_stacks_and_artifact(self):
        for amount in (-1, -2):
            for artifact in (False, True):
                with self.subTest(amount=amount, artifact=artifact):
                    s = self.state()
                    s["player_state"]["chosen_class_name"] = "THE_SILENT"
                    powers = [dict(power_id="Wraith Form v2", amount=amount)]
                    if artifact:
                        powers.append(dict(power_id="Artifact", amount=1))
                    s["player_state"]["creature"]["powers"] = powers
                    r = self.end(s)
                    self.assertEqual(r["player_stats"]["dexterity"], 0 if artifact else amount)
                    self.assertEqual(r["player_stats"]["artifact"], 0)

    def test_wraith_card_capability_gate_accepts_implemented_effect(self):
        s = self.state()
        s["player_state"]["hand"][0]["card_id"] = "Wraith Form v2"
        r = execute_worker(EXE, s, 1, 50, "import")
        self.assertTrue(r["supported"], r)

    def test_empty_deck_can_use_lethal_potion(self):
        s = self.state()
        p = s["player_state"]
        p["hand"], p["draw_pile"], p["discard_pile"] = [], [], []
        p["potions"] = [dict(id="Fire Potion", slot=0)]
        s["cur_map_node_state"]["monster_data"][0]["creature"]["current_health"] = 15
        command = json.loads(self.run_native(s, "search")["commands"][0]["command"])
        self.assertEqual((command["type"], command["potion_index"], command["monster_index"]), ("POTION", 0, 0))

    def test_empty_deck_lightning_and_no_damage_states_remain_playable(self):
        for lightning in (False, True):
            with self.subTest(lightning=lightning):
                s = self.state()
                p = s["player_state"]
                p["hand"], p["draw_pile"], p["discard_pile"] = [], [], []
                if lightning:
                    p["chosen_class_name"] = "DEFECT"
                    p["max_orbs"] = 1
                    p["orbs"] = [dict(lookup_key="Lightning", evoke_amount=8)]
                self.run_native(s)
                r = self.end(s)
                self.assertEqual(r["projection"]["monsters"][0]["hp"], 47 if lightning else 50)

    def test_births_and_dead_split_parents_remain_in_wire_projection(self):
        cases = (("GremlinLeader", 2, 3), ("Reptomancer", 2, 2), ("BronzeAutomaton", 4, 3),
                 ("TheCollector", 1, 3), ("AcidSlime_L", 3, 3), ("SpikeSlime_L", 3, 3), ("SlimeBoss", 3, 3))
        for id, move, count in cases:
            with self.subTest(id=id):
                s = self.state()
                s["cur_map_node_state"]["monster_data"] = [monster(id, hp=30, move=move, max_health=100)]
                s["player_state"]["energy_panel_total_energy"] = 0
                r = self.run_native(s, "search")
                self.assertEqual(json.loads(r["commands"][0]["command"])["type"], "END")
                ms = r["expected_after"]["monsters"]
                self.assertEqual(len(ms), count)
                parent = next(m for m in ms if m["id"] == id)
                self.assertEqual(parent["hp"], 0 if "Slime" in id else 30)
                self.assertEqual(sum(m["hp"] > 0 for m in ms), 2 if "Slime" in id else count)

    def test_leader_reuses_native_slots_without_overwriting_history(self):
        s = self.state()
        s["cur_map_node_state"]["monster_data"] = [monster("GremlinWarrior", move=1, x=-366),
            monster("GremlinThief", hp=0, move=1, x=-170), monster("GremlinLeader", hp=140, move=2)]
        before = projection(s)["monsters"]
        ms = self.end(s)["projection"]["monsters"]
        self.assertEqual(len(ms), 5)
        self.assertEqual(sum(m["hp"] > 0 for m in ms), 4)
        self.assertEqual(ms[1], before[0])  # The empty far-left slot is filled too.
        self.assertEqual(ms[-2], before[1])  # Newborn at the same X is inserted before its corpse.
        self.assertEqual(ms[-1], before[2])

    def test_large_slime_split_consumes_only_vanilla_rolls(self):
        for id, rolls in (("AcidSlime_L", 2), ("SpikeSlime_L", 3)):
            with self.subTest(id=id):
                s = self.state()
                s["cur_map_node_state"]["monster_data"] = [monster(id, hp=15, move=3)]
                before = s["rng_state"]["ai_rng_counter"]
                self.assertEqual(self.end(s)["projection"]["rng"]["ai_rng_counter"], before + rolls)

    def test_historical_summons_over_five_entries_preserve_target_indices(self):
        s = self.state()
        s["player_state"]["hand"] = []
        s["player_state"]["potions"] = [dict(id="Fire Potion", slot=0)]
        ms = [monster("GremlinWarrior", hp=0, move=1, x=-366) for _ in range(7)]
        ms.append(monster("GremlinLeader", hp=6, move=2))
        s["cur_map_node_state"]["monster_data"] = ms
        self.assertEqual(self.run_native(s)["projection"], projection(s))
        command = json.loads(self.run_native(s, "search")["commands"][0]["command"])
        self.assertEqual((command["type"], command["monster_index"]), ("POTION", 7))

    def test_reimport_after_both_slime_children_split(self):
        s = self.state()
        s["cur_map_node_state"]["monster_data"] = [
            monster("SpikeSlime_L", hp=15, max_health=30, move=3, x=-385),
            monster("SlimeBoss", hp=0, max_health=140, move=3),
            monster("AcidSlime_L", hp=15, max_health=30, move=3, x=120)]
        result = self.end(s)["projection"]
        self.assertEqual(result["rng"]["ai_rng_counter"], s["rng_state"]["ai_rng_counter"] + 5)
        ms = result["monsters"]
        expected = ["SpikeSlime_M", "SpikeSlime_L", "SpikeSlime_M", "AcidSlime_M",
                    "SlimeBoss", "AcidSlime_L", "AcidSlime_M"]
        self.assertEqual([m["id"] for m in ms], expected)
        xs = [-519, -385, -251, -14, 0, 120, 254]
        next_state = copy.deepcopy(s)
        next_state["cur_map_node_state"]["monster_data"] = [
            monster(m["id"], hp=m["hp"], max_health=m["max_hp"], move=1, x=x)
            for m, x in zip(ms, xs)]
        self.assertEqual(self.run_native(next_state)["projection"], projection(next_state))

    def test_native_continues_on_next_snapshot_after_summoning(self):
        s = self.state()
        s["player_state"]["energy_panel_total_energy"] = 0
        s["cur_map_node_state"]["monster_data"] = [monster("GremlinWarrior", move=1, x=-366),
            monster("GremlinThief", hp=0, move=1, x=-170), monster("GremlinLeader", hp=140, move=2)]
        after = self.end(s)["projection"]
        next_state = copy.deepcopy(s)
        next_state.update(turn=after["turn"], cards_played_this_turn=[], cards_played_this_turn_backup=[])
        p = next_state["player_state"]
        p["creature"].update(current_health=after["hp"], current_block=after["block"])
        p["energy_panel_total_energy"] = after["energy"]
        for pile in ("hand", "draw_pile", "discard_pile", "exhaust_pile"):
            p[pile] = [dict(card_id=c["id"], times_upgraded=c["upgrades"], cost=c["cost"],
                           cost_for_turn=c["turn_cost"], retain=c["retain"],
                           free_to_play_once=c["free"], uuid=f"{pile}-{i}")
                       for i, c in enumerate(after[pile])]
        next_state["rng_state"].update(after["rng"])
        next_state["cur_map_node_state"]["monster_data"] = [
            monster(m["id"], hp=m["hp"], max_health=m["max_hp"], current_block=m["block"],
                    move=3 if m["id"] == "GremlinLeader" else 1, x=x)
            for m, x in zip(after["monsters"], (-532, -366, -170, -170, 35))]
        self.assertEqual(projection(next_state), after)
        for state in (s,next_state):
            result=execute_worker(EXE,state,1000,100)
            self.assertTrue(result["supported"],result)
            self.assertEqual(result["type"],"COMMAND_LIST")


if __name__ == "__main__":
    unittest.main()
