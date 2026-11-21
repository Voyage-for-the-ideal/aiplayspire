import copy
import json
from pathlib import Path
import subprocess
import sys
import tempfile
import unittest
import os
import queue
from unittest.mock import patch

from battle_engine.profiles import budgets
from battle_engine.native_api import execute_worker
from battle_engine.state import load, projection
from battle_engine.worker import Worker

ROOT = Path(__file__).resolve().parents[1]
EXE = Path(os.environ.get("BATTLE_ENGINE_WORKER", str(ROOT / "bin/battle-engine-worker.exe")))
FIXTURE = ROOT / "tests/fixtures/ironclad.json"


class ProfileTests(unittest.TestCase):
    def test_coverage_separates_dispatch_from_simulation_accuracy(self):
        from battle_engine.tools.coverage import inventory
        report = inventory()
        self.assertEqual(report["complete_dispatch"], all(not r["missing"] for r in report["roles"].values()))
        self.assertFalse(report["real_engine_checked"])
        self.assertEqual(report["roles"]["IRONCLAD"]["missing"], [])

    def test_all_three_profiles(self):
        for name, nodes, ms in (("FAST", 5000, 250), ("BALANCED", 15000, 2000), ("DEEP", 50000, 10000)):
            self.assertEqual(budgets(dict(search_profile=name)), (name, nodes, ms))

    def test_overrides_and_ceiling(self):
        self.assertEqual(budgets(dict(search_profile="DEEP", timeout_ms=1234, max_expansions=321), 1000), ("DEEP", 321, 1000))
        with self.assertRaises(ValueError):
            budgets(dict(search_profile="typo"))


@unittest.skipUnless(EXE.is_file(), "Build the native worker with battle_engine/build.ps1")
class RuntimeTests(unittest.TestCase):
    def setUp(self):
        self.worker = Worker(EXE)
        self.addCleanup(self.worker.close)
        self.state = load(FIXTURE)

    def request(self, state=None, **extra):
        request = dict(snapshot=state or self.state, operation="search", session_id="test-session",
                       max_expansions=1000, timeout_ms=100)
        request.update(extra)
        return self.worker.execute(request, 100)

    def test_jsonl_reuses_process_and_identifies_responses(self):
        a = self.request()
        pid = self.worker.process.pid
        b = self.request()
        self.assertTrue(a["supported"], a)
        self.assertEqual(self.worker.process.pid, pid)
        self.assertEqual((a["request_id"], b["request_id"]), (1, 2))
        self.assertEqual(b["metrics"]["search_mode"], "turn")

    def test_restarts_after_error_and_releases_process(self):
        bad = copy.deepcopy(self.state)
        bad["player_state"]["chosen_class_name"] = "UNKNOWN"
        failed = self.request(bad)
        self.assertFalse(failed["supported"])
        self.assertIsNone(self.worker.process)
        succeeded = self.request()
        self.assertTrue(succeeded["supported"], succeeded)
        self.assertEqual(succeeded["metrics"]["worker_generation"], 2)

    def test_restarts_after_process_crash(self):
        self.request()
        self.worker.process.kill()
        self.worker.process.wait(timeout=5)
        result = self.request()
        self.assertTrue(result["supported"], result)
        self.assertEqual(result["metrics"]["worker_generation"], 2)

    def test_timeout_destroys_worker_without_retry(self):
        self.request()
        with patch.object(self.worker.replies, "get", side_effect=queue.Empty):
            with self.assertRaises(subprocess.TimeoutExpired):
                self.request()
        self.assertIsNone(self.worker.process)
        self.assertTrue(self.request()["supported"])

    def test_response_id_mismatch_destroys_worker(self):
        self.request()
        with patch.object(self.worker.replies, "get", return_value=b'{"supported":true,"request_id":-1}\n'):
            with self.assertRaises(ValueError):
                self.request()
        self.assertIsNone(self.worker.process)

    def test_one_shot_and_mcts_are_available(self):
        result = execute_worker(EXE, self.state, 30, 20, metadata=dict(search_mode="mcts"))
        self.assertTrue(result["supported"], result)
        self.assertEqual(result["metrics"]["search_mode"], "mcts")

    def test_small_budget_still_has_completed_action(self):
        result = execute_worker(EXE, self.state, 1, 10)
        self.assertTrue(result["supported"], result)
        self.assertEqual(result["metrics"]["expanded_nodes"], 1)

    def test_forced_end_does_not_spend_search_budget(self):
        p = self.state["player_state"]
        p["energy_panel_total_energy"] = 0
        p["potions"] = [dict(id="Potion Slot", slot=-1)] * 3
        result = self.request()
        self.assertTrue(result["supported"], result)
        self.assertEqual(json.loads(result["commands"][0]["command"])["type"], "END")
        self.assertEqual(result["metrics"]["expanded_nodes"], 0)
        self.assertEqual(result["metrics"]["search_stop_reason"], "FORCED_ROOT_ACTION")

    def test_evaluation_exposes_components(self):
        result = execute_worker(EXE, self.state, 1, 10, "evaluate")
        self.assertTrue(result["supported"], result)
        score = result["evaluation"]
        self.assertEqual(score["total"], sum(v for k, v in score.items() if k != "total"))
        self.assertEqual(score["survival"], 7000)
        self.assertEqual(score["resource"], 2400)

    def test_cached_second_action_uses_real_snapshot_and_card_identity(self):
        # A deterministic two-Strike lethal has no powers/turn-boundary cleanup.
        # This builds the next snapshot independently of the simulator's output.
        p = self.state["player_state"]
        p["potions"] = [dict(id="Potion Slot", slot=-1)] * 3
        p["hand"] = [dict(card_id="Strike_R", cost=1, cost_for_turn=1, times_upgraded=0, uuid=str(i)) for i in range(2)]
        p["draw_pile"] = []
        monster = self.state["cur_map_node_state"]["monster_data"][0]
        monster["creature"]["current_health"] = 12
        monster["creature"]["max_health"] = 12
        first = self.request()
        self.assertTrue(first["supported"], first)
        command = json.loads(first["commands"][0]["command"])
        self.assertEqual(command["type"], "CARD")
        played = p["hand"].pop(command["card_index"])
        p["discard_pile"].append(played)
        p["energy_panel_total_energy"] -= 1
        monster["creature"]["current_health"] -= 6
        self.state["cards_played_this_turn_backup"].append(played)
        second = self.request(debug_keys=True)
        self.assertTrue(second["supported"], second)
        self.assertTrue(second["metrics"]["cache_hit"], second)
        self.assertEqual(second["metrics"]["expanded_nodes"], 0)

    def test_segments_carry_bound_live_state_and_never_cross_lethal(self):
        p = self.state["player_state"]
        p["potions"] = [dict(id="Potion Slot", slot=-1)] * 3
        p["hand"] = [dict(card_id="Strike_R", cost=1, cost_for_turn=1, times_upgraded=0, uuid=str(i)) for i in range(3)]
        p["draw_pile"] = []
        monster = self.state["cur_map_node_state"]["monster_data"][0]["creature"]
        monster["current_health"] = monster["max_health"] = 18
        for length in (1, 2, 4):
            first = self.request(segment_length=length, session_id=str(length), timeout_ms=500)
            self.assertTrue(first["supported"], first)
            self.assertEqual(len(first.get("continuations", [])), min(length-1, 1))
        second = first["continuations"][0]
        self.assertEqual(second["metrics"]["expanded_nodes"], 0)
        played = p["hand"].pop(json.loads(first["commands"][0]["command"])["card_index"])
        p["discard_pile"].append(played)
        self.state["cards_played_this_turn_backup"].append(played)
        p["energy_panel_total_energy"] -= 1
        monster["current_health"] -= 6
        proof = self.request(operation="fingerprint")
        self.assertEqual(proof["native_key"], second["required_native_key"])
        self.assertEqual(proof["projection"], second["commands"][0]["expected_before"])
        from battle_engine.state import context
        self.assertEqual(context(self.state), second["commands"][0]["expected_context"])
        p["hand"][0]["uuid"] = "replacement"
        self.assertNotEqual(context(self.state), second["commands"][0]["expected_context"])

    def test_boundary_dedup_is_opt_in(self):
        reference = self.request()
        candidate = self.request(dedup_mode="new_turn")
        self.assertEqual(reference["metrics"]["dedup_mode"], "default")
        self.assertEqual(candidate["metrics"]["dedup_mode"], "new_turn")
        self.assertGreater(reference["metrics"]["dedup_checks"], candidate["metrics"]["dedup_checks"])
        self.assertFalse(self.request(segment_length=3)["supported"])

    def test_search_targets_live_monster_with_inactive_slots(self):
        for inactive in ("dead", "escaped", "is_escaping"):
            for live_index in (0, 1):
                for action in ("CARD", "POTION"):
                    with self.subTest(inactive=inactive, live_index=live_index, action=action):
                        state = load(FIXTURE)
                        p = state["player_state"]
                        p["draw_pile"] = []
                        p["hand"] = [dict(card_id="Strike_R", cost=1, cost_for_turn=1,
                                          times_upgraded=0)]
                        p["energy_panel_total_energy"] = 1 if action == "CARD" else 0
                        p["potions"] = [dict(id="Fire Potion", slot=0)] if action == "POTION" else []
                        live = state["cur_map_node_state"]["monster_data"][0]
                        gone = copy.deepcopy(live)
                        live["creature"]["current_health"] = 6 if action == "CARD" else 15
                        if inactive == "dead":
                            gone["creature"].update(current_health=0, is_dead=True)
                        elif inactive == "escaped":
                            gone["escaped"] = True
                        else:
                            gone["creature"]["is_escaping"] = True
                        state["cur_map_node_state"]["monster_data"] = [live, gone] if live_index == 0 else [gone, live]
                        result = self.request(state)
                        self.assertTrue(result["supported"], result)
                        command = json.loads(result["commands"][0]["command"])
                        self.assertEqual(command["type"], action)
                        self.assertEqual(command["monster_index"], live_index)
                        self.assertEqual(result["commands"][0]["expected_before"], projection(state))

    def test_cached_path_continues_after_monster_death(self):
        p = self.state["player_state"]
        p["potions"] = []
        p["draw_pile"] = []
        p["hand"] = [dict(card_id="Strike_R", cost=1, cost_for_turn=1,
                          times_upgraded=0, uuid=str(i)) for i in range(2)]
        monsters = self.state["cur_map_node_state"]["monster_data"]
        monsters[0]["creature"].update(current_health=6, max_health=6, is_dead=False)
        monsters.append(copy.deepcopy(monsters[0]))
        first = self.request()
        self.assertTrue(first["supported"], first)
        pid = self.worker.process.pid
        command = json.loads(first["commands"][0]["command"])
        self.assertEqual(command["type"], "CARD")
        killed_index = command["monster_index"]
        # Independently reproduce the real post-action snapshot, including the death flags.
        played = p["hand"].pop(command["card_index"])
        p["discard_pile"].append(played)
        p["energy_panel_total_energy"] -= 1
        self.state["cards_played_this_turn_backup"].append(played)
        monsters[killed_index]["creature"].update(current_health=0, is_dead=True, is_dying=True)
        self.assertEqual(first["expected_after"], projection(self.state))
        second = self.request(debug_keys=True)
        self.assertTrue(second["supported"], second)
        self.assertEqual(self.worker.process.pid, pid)
        self.assertTrue(second["metrics"]["cache_hit"], second)
        command = json.loads(second["commands"][0]["command"])
        self.assertEqual(command["type"], "CARD")
        self.assertEqual(command["monster_index"], 1 - killed_index)
        self.assertEqual(second["commands"][0]["expected_before"], projection(self.state))

    def test_other_session_cannot_reuse_path(self):
        self.request()
        result = self.worker.execute(dict(snapshot=self.state, session_id="other", max_expansions=50, timeout_ms=20), 20)
        self.assertFalse(result["metrics"]["cache_hit"])

    def test_release_session_does_not_restart_worker(self):
        self.request()
        pid = self.worker.process.pid
        self.assertTrue(self.worker.execute(dict(operation="release_session", session_id="test-session"), 100)["supported"])
        self.assertEqual(self.worker.process.pid, pid)

    def test_cache_invalidates_for_profile_budget_identity_and_card_attributes(self):
        for change in ("profile", "budget", "identity", "attributes", "power", "floor"):
            with self.subTest(change=change):
                self.worker.close()
                self.state = load(FIXTURE)
                p = self.state["player_state"]
                p["potions"] = [dict(id="Potion Slot", slot=-1)] * 3
                p["draw_pile"] = []
                p["hand"] = [dict(card_id="Strike_R", cost=1, cost_for_turn=1, times_upgraded=0, uuid=str(i)) for i in range(2)]
                monster = self.state["cur_map_node_state"]["monster_data"][0]["creature"]
                monster["current_health"] = monster["max_health"] = 12
                first = self.request()
                played = p["hand"].pop(json.loads(first["commands"][0]["command"])["card_index"])
                p["discard_pile"].append(played)
                p["energy_panel_total_energy"] -= 1
                monster["current_health"] -= 6
                self.state["cards_played_this_turn_backup"].append(played)
                extra = {}
                if change == "profile": extra["search_profile"] = "FAST"
                if change == "budget": extra["max_expansions"] = 500
                if change == "identity": p["hand"][0]["uuid"] = "new"
                if change == "attributes": p["hand"][0]["base_damage"] = 6
                if change == "power": p["creature"]["powers"] = [dict(power_id="Strength", amount=1)]
                if change == "floor": self.state["floor_num"] = 2
                result = self.request(**extra)
                self.assertTrue(result["supported"], result)
                self.assertFalse(result["metrics"]["cache_hit"], result)


if __name__ == "__main__":
    unittest.main()
