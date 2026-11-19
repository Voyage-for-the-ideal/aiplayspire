"""Candidate policy, room budgets, and paired acceptance semantics."""
from collections import defaultdict
import copy
import json
from pathlib import Path
import tempfile
import unittest
from unittest.mock import Mock, patch

from battle_engine.profiles import benchmark_budget, deployment_budget
from battle_engine.tools.balanced_ab import (
    ROLES, ROOMS, execute_case, replay_snapshot_key, replay_snapshots_match, summarize, terminal,
)
from battle_engine.tools.a10_corpus import freeze
from battle_engine.tools.oracle import freeze_oracle_preferences
from battle_engine.server import execute_worker
from battle_engine.tests import test_character_strategy as fixtures
from battle_engine.worker import Worker


class CandidatePolicyTests(unittest.TestCase):
    def test_boss_fight_budget_doubles_without_changing_segment_caps(self):
        self.assertEqual(benchmark_budget("BOSS","deployment"),((10000,500),(400000,20000)))
        self.assertEqual(benchmark_budget("BOSS","complete"),((15000,90000),(600000,3600000)))
        self.assertEqual(benchmark_budget("ELITE","deployment"),((10000,500),(200000,10000)))
        self.assertEqual(benchmark_budget("MONSTER","deployment"),((5000,250),(100000,5000)))

    def test_terminal_cleanup_does_not_require_reward_phase(self):
        state = dict(player_state=dict(creature=dict(current_health=10)),
                     cur_map_node_state=dict(is_battle_over=True, phase_name="COMBAT",
                                             monster_data=[dict(creature=dict(is_dead=True,current_health=0))]))
        self.assertEqual(terminal(state), "win")
        state["cur_map_node_state"]["monster_data"][0]["creature"]["half_dead"] = True
        self.assertIsNone(terminal(state))
        state["player_state"]["creature"]["current_health"] = 0
        self.assertEqual(terminal(state), "loss")

    def test_oracle_preferences_do_not_modify_reference(self):
        with tempfile.TemporaryDirectory() as tmp:
            base=Path(tmp)
            source=base/"source"
            source.mkdir()
            tips=source/"2_STSTips"
            original='{"SHUFFLE_TIP": "false", "NEOW_SKIP": "true"}'
            tips.write_text(original)
            freeze_oracle_preferences(source,base/"isolated")
            self.assertEqual(tips.read_text(),original)
            isolated=json.loads((base/"isolated"/tips.name).read_text())
            self.assertEqual(isolated["SHUFFLE_TIP"],"true")
            self.assertEqual(isolated["NO_FTUE"],"true")
            self.assertEqual(isolated["NEOW_SKIP"],"true")

    def test_java_replay_uses_same_directory_as_relative_guard_paths(self):
        with tempfile.TemporaryDirectory() as tmp:
            base=Path(tmp)
            source=base/"fixture.json"
            state=dict(player_state=dict(creature=dict(current_health=10,max_health=10)),
                       cur_map_node_state=dict(is_battle_over=False, phase_name="COMBAT",monster_data=[]))
            source.write_text(json.dumps(state))
            reply=dict(commands=[{"command":'{"type":"END"}',"state":"savestates/plan/1.txt"}],
                       metrics=dict(expanded_nodes=1,elapsed_ms=3),final_state_key="end")
            def fake_search(port,initial,directory,cap,timeout):
                self.assertEqual(initial,source)
                (directory/"start.json").write_bytes(initial.read_bytes())
                return reply
            def fake_replay(port,initial,commands,after):
                self.assertEqual(initial,after.with_name("start.json"))
                self.assertEqual(commands,reply["commands"])
                state["cur_map_node_state"]["is_battle_over"]=True
                after.write_text(json.dumps(state))
                return {"final_state_key":"end"}
            with patch("battle_engine.tools.balanced_ab.request_java",side_effect=fake_search), \
                 patch("battle_engine.tools.balanced_ab.replay",side_effect=fake_replay):
                result=execute_case(15129,Mock(),dict(path=source,room="MONSTER"),
                                    "deployment","java",base/"run")
            self.assertEqual(result["outcome"],"win")

    def test_manifest_keeps_seed_groups_disjoint(self):
        groups=defaultdict(dict)
        with tempfile.TemporaryDirectory() as tmp:
            path=Path(tmp)/"capture.txt"
            path.write_text("fixture")
            for index,role in enumerate(ROLES):
                for room in ROOMS:
                    groups[role,room]={100*index+seed:path for seed in range(23)}
            cases=freeze(groups)["cases"]
        seed_sets={split:{c["seed"] for c in cases if c["split"]==split} for split in ("pilot","holdout")}
        self.assertFalse(seed_sets["pilot"] & seed_sets["holdout"])
        self.assertEqual(len(cases),276)

    def test_only_known_java_snapshot_bookkeeping_is_normalized(self):
        def snapshot(geometry, empty, occupied, rng, intent):
            return dict(player_state=dict(potions=[dict(id="Potion Slot",slot=empty),
                                                   dict(id="Dexterity Potion",slot=occupied)]),
                        cur_map_node_state=dict(monster_data=[dict(next_move=intent,creature=dict(
                            draw_y=geometry,hb_h=geometry,hb=dict(c_y=geometry,height=geometry,clicked=False)))]),
                        rng_state=dict(ai_rng_counter=rng))
        base=replay_snapshot_key(snapshot(3,0,1,70,2))
        self.assertEqual(base,replay_snapshot_key(snapshot(4,-1,1,70,2)))
        self.assertNotEqual(base,replay_snapshot_key(snapshot(4,-1,0,70,2)))
        self.assertNotEqual(base,replay_snapshot_key(snapshot(4,-1,1,71,2)))
        self.assertNotEqual(base,replay_snapshot_key(snapshot(4,-1,1,70,3)))

    def test_generated_card_ids_are_aliased_without_losing_reference_topology(self):
        def snapshot(ids, ref):
            return dict(player_state=dict(hand=[dict(uuid=identity,card_id="Shiv") for identity in ids],
                                          reference=ref),cur_map_node_state=dict(monster_data=[]))
        source=snapshot(["known-card"],"known-card")
        expected=snapshot(["known-card","generated-1","generated-2"],"generated-1")
        actual=snapshot(["known-card","replayed-1","replayed-2"],"replayed-1")
        self.assertTrue(replay_snapshots_match(source,expected,actual))
        self.assertFalse(replay_snapshots_match(
            source,expected,snapshot(["known-card","replayed-1","replayed-2"],"replayed-2")))
        self.assertFalse(replay_snapshots_match(
            source,expected,snapshot(["new-wrong-source-id","replayed-1","replayed-2"],"replayed-1")))
    def test_only_elite_and_boss_get_double(self):
        for room in ("MonsterRoomElite", "MonsterRoomBoss", "ELITE", "BOSS"):
            self.assertEqual(deployment_budget(room), (10000, 500))
            self.assertEqual(deployment_budget(room, "legacy"), (5000, 250))
        for room in ("MonsterRoom", "EventRoom", None, "BurningEliteMapSymbol"):
            self.assertEqual(deployment_budget(room), (5000, 250))

    def test_incomplete_results_are_not_pruned(self):
        pairs = [dict(role=role, seed=i, cpp=dict(outcome="win", hp_lost=1, potions_used=0),
                      java=dict(outcome="loss")) for i, role in enumerate(ROLES)]
        self.assertTrue(summarize(pairs)["passed"])
        pairs[0]["cpp"] = dict(outcome="incomplete", reason="prediction mismatch")
        report = summarize(pairs)
        self.assertEqual(report["wins_losses_incomplete"]["cpp_incomplete"], 1)
        self.assertFalse(report["passed"])


@unittest.skipUnless(fixtures.EXE.is_file(), "Build the native worker first")
class LayeredNativeTests(unittest.TestCase):
    state = fixtures.CharacterStrategyTests.state
    card = fixtures.CharacterStrategyTests.card

    def test_verified_same_turn_suffix_and_cache_misses(self):
        state=self.state(role="IRONCLAD",hand=[
            self.card("Strike_R",uuid="first"),self.card("Strike_R",uuid="second")])
        state["cur_map_node_state"]["monster_data"][0]["creature"]["current_health"]=12
        for change in (None,"rng","uuid","turn","room","session","disabled"):
            with self.subTest(change=change):
                worker=Worker(fixtures.EXE)
                try:
                    args=dict(search_policy="layered-v1",session_id="fixture",max_expansions=300,timeout_ms=500)
                    first=worker.execute(dict(snapshot=state,**args),500)
                    self.assertTrue(first["supported"],first)
                    cmd=json.loads(first["commands"][0]["command"])
                    self.assertEqual(cmd["type"],"CARD")
                    self.assertGreater(first["metrics"]["cached_actions_remaining"],0)
                    after=copy.deepcopy(state)
                    p=after["player_state"]
                    card=p["hand"].pop(cmd["card_index"])
                    p["discard_pile"]=[card]
                    p["energy_panel_total_energy"]=2
                    after["cards_played_this_turn_backup"]=[card]
                    after["cur_map_node_state"]["monster_data"][0]["creature"]["current_health"]=6
                    if change=="rng":after["rng_state"]["ai_rng_counter"]+=1
                    if change=="uuid":p["hand"][0]["uuid"]="other"
                    if change=="turn":after.update(turn=2,cards_played_this_turn=[])
                    if change=="room":after["cur_map_node_state"]["room_type"]="ELITE"
                    if change=="session":args["session_id"]="other"
                    if change=="disabled":args["reuse_path"]=False
                    second=worker.execute(dict(snapshot=after,**args),500)
                    self.assertTrue(second["supported"],second)
                    self.assertEqual(second["metrics"]["cache_hit"],change is None,second)
                    if change is None:
                        self.assertEqual(second["metrics"]["expanded_nodes"],0)
                        self.assertEqual(second["metrics"]["planner_us"],0)
                finally:
                    worker.close()

    def search(self, state, cap=500, **metadata):
        result = execute_worker(fixtures.EXE, state, cap, 500, "search",
                                metadata={"search_policy": "layered-v1", **metadata})
        self.assertTrue(result["supported"], result)
        self.assertLessEqual(result["metrics"]["expanded_nodes"], cap)
        return result

    def test_budget_and_depth_telemetry(self):
        state = self.state(role="IRONCLAD", hand=[self.card("Strike_R"), self.card("Defend_R")])
        reply = self.search(state)
        self.assertEqual(len(reply["commands"]), 1)
        metrics = reply["metrics"]
        self.assertEqual(metrics["requested_expansions"], 500)
        self.assertEqual(metrics["requested_timeout_ms"], 500)
        self.assertGreater(metrics["max_completed_turns"], 0)
        self.assertLessEqual(metrics["selected_completed_turns"], metrics["max_completed_turns"])
        self.assertLessEqual(metrics["max_completed_turns"], 6)
        short = self.search(state, 1)
        self.assertEqual(short["metrics"]["expanded_nodes"], 1)

    def ranks(self, state, candidate):
        result = execute_worker(fixtures.EXE, state, 500, 500, "strategy",
                                metadata={"search_policy": "layered-v1" if candidate else "legacy"})
        self.assertTrue(result["supported"], result)
        return {(a["type"], a["source"], a["target"]):a["priority"] for a in result["actions"]}

    def test_ironclad_preserves_candidates_and_prepares_block(self):
        state = self.state(role="IRONCLAD", hand=[self.card("Body Slam"), self.card("Defend_R"),
                                                  self.card("Dark Embrace")])
        before, after = self.ranks(state, False), self.ranks(state, True)
        self.assertEqual(before.keys(), after.keys())
        for key, score in before.items():
            if key[1] == 1 and key[0] == 0:
                self.assertGreater(after[key], score)
        self.assertNotEqual(before, after)

    def test_defect_prioritizes_turbo_when_energy_is_short(self):
        hands = [[self.card("Turbo", cost=0), self.card("Strike_B")],
                 [self.card("Turbo", cost=0), self.card("Glacier", cost=2), self.card("Glacier", cost=2)]]
        values = []
        for hand in hands:
            state = self.state(role="DEFECT", hand=hand)
            ranks = self.ranks(state, True)
            values.append([v for (kind, source, _),v in ranks.items() if kind == 0 and source == 0][0])
        self.assertLess(values[0], values[1])
