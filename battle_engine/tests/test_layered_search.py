"""Candidate policy, room budgets, and paired acceptance semantics."""
import unittest

from battle_engine.profiles import deployment_budget
from battle_engine.tools.balanced_ab import ROLES, summarize
from battle_engine.server import execute_worker
from battle_engine.tests import test_character_strategy as fixtures


class CandidatePolicyTests(unittest.TestCase):
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
