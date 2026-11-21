import json
from pathlib import Path
import os
import tempfile
import unittest

from battle_engine.state import load
from battle_engine.worker import Worker
from sts_ai_framework.headless.agent import read_combat_search

ROOT = Path(__file__).resolve().parents[1]
WORKER = Path(os.environ.get("BATTLE_ENGINE_WORKER", ROOT / "bin" /
                             ("battle-engine-worker.exe" if os.name == "nt" else "battle-engine-worker")))


class UnlimitedSearchTest(unittest.TestCase):
    def test_pinned_server_policy(self):
        config = ROOT.parent / "deploy/headless/combat-search.local-deep.json"
        self.assertEqual(read_combat_search(config), dict(search_policy="legacy", search_profile="DEEP",
                                                         max_expansions=15000, timeout_ms=0))
        with tempfile.TemporaryDirectory() as d:
            path = Path(d) / "combat-search.json"
            path.write_text(json.dumps(dict(read_combat_search(config), search_policy="layered-v1")))
            with self.assertRaises(ValueError): read_combat_search(path)

    @unittest.skipUnless(WORKER.exists(), "Build the native worker")
    def test_worker_uses_expansion_budget_without_clock(self):
        snapshot = load(ROOT / "tests/fixtures/ironclad.json")
        w = Worker(WORKER)
        self.addCleanup(w.close)
        base = dict(snapshot=snapshot, search_profile="DEEP", search_policy="legacy", timeout_ms=0, max_expansions=30)
        r = w.execute(base, 0)
        self.assertTrue(r["supported"], r)
        self.assertEqual(r["metrics"]["requested_timeout_ms"], 0)
        self.assertEqual(r["metrics"]["requested_expansions"], 30)
        self.assertLessEqual(r["metrics"]["expanded_nodes"], 30)
        self.assertNotEqual(r["metrics"]["search_stop_reason"], "TIMEOUT")
        self.assertFalse(w.execute(dict(base, search_policy="layered-v1"), 2000)["supported"])

    @unittest.skipUnless(WORKER.exists(), "Build the native worker")
    def test_supervisor_can_cancel_an_unlimited_request(self):
        def closed():
            raise ValueError("Collection stop requested")
        w = Worker(WORKER)
        self.addCleanup(w.close)
        with self.assertRaisesRegex(ValueError, "Collection stop requested"):
            w.execute({}, 0, check_cancel=closed)
        self.assertIsNone(w.process)
