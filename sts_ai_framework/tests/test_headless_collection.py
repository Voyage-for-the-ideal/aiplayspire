import copy
from dataclasses import replace
import gzip
import json
from pathlib import Path
import tempfile
import unittest

from sts_ai_framework.headless.collection import EpisodeWriter, validate_episode
from sts_ai_framework.headless.interaction import IroncladInteractionBridge
from sts_ai_framework.models import GameAction
from sts_ai_framework.tests.test_headless_flow import terminal


class CollectionContractTest(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory(); self.addCleanup(self.temp.cleanup)
        self.root = Path(self.temp.name); (self.root / "ALLOW_COLLECTION").write_text("explicit test permit")

    def writer(self): return EpisodeWriter(self.root, role="IRONCLAD", seed=111, policy={"mode": "fixture"})

    def replies(self):
        final = terminal(); final["seed"] = 111
        before = copy.deepcopy(final); before["phase"] = "PROCEED"; before["player"]["hp"] = 1
        before["interaction"]["flow"].update(terminal=None, player_dead=False, result_screen="NONE")
        return {"sequence": 1, "state_token": "before", "state": before}, {"sequence": 2, "state_token": "after", "state": final}

    def complete(self):
        w = self.writer(); before, after = self.replies(); w.observation(before)
        after["state"]["driver_receipt"] = {"source_state_token": "before", "sequence": 2, "method": "endTurn", "args": []}
        w.transition(before_sequence=1, after_sequence=2, method="endTurn", args=[], effect="APPLIED", policy={})
        w.observation(after)
        b = IroncladInteractionBridge(); frame = b.observe(after["state"], run_id=w.episode_id, sequence=2)
        receipt = b.acknowledge_terminal(b.prepare(frame, GameAction(type="proceed")), frame)
        return w.finish(terminal=receipt)

    def test_complete_hash_chained_episode_and_terminal_receipt(self):
        result = self.complete(); checked = validate_episode(result["path"])
        self.assertEqual((checked["status"], checked["transitions"]), ("complete", 1))
        self.assertEqual(result["terminal"]["outcome"], "defeat")

    def test_abort_is_censored_not_a_defeat(self):
        w = self.writer(); w.observation(self.replies()[0]); r = w.finish(error="worker lost")
        self.assertTrue(r["censored"]); self.assertIsNone(r["terminal"])
        with self.assertRaises(ValueError): validate_episode(r["path"])
        self.assertEqual(validate_episode(r["path"], require_complete=False)["status"], "aborted")

    def test_byte_budget_preserves_a_closed_observed_prefix(self):
        w = self.writer(); before, after = self.replies(); w.observation(before)
        w.max_bytes = w.bytes+1
        w.transition(before_sequence=1, after_sequence=2, method="endTurn", args=[], effect="APPLIED", policy={})
        with self.assertRaises(ValueError): w.observation(after)
        result = w.finish(error="size limit")
        self.assertEqual(result["transitions"], 0)
        self.assertEqual(validate_episode(result["path"], require_complete=False)["transitions"], 0)

    def test_terminal_receipt_binds_the_actual_last_snapshot(self):
        w = self.writer(); before, after = self.replies(); w.observation(before)
        after["state"]["driver_receipt"] = {"source_state_token": "before", "sequence": 2, "method": "endTurn", "args": []}
        w.transition(before_sequence=1, after_sequence=2, method="endTurn", args=[], effect="APPLIED", policy={}); w.observation(after)
        b = IroncladInteractionBridge(); frame = b.observe(after["state"], run_id=w.episode_id, sequence=2)
        receipt = b.acknowledge_terminal(b.prepare(frame, GameAction(type="proceed")), frame)
        result = w.finish(terminal=replace(receipt, snapshot_token="wrong"))
        with self.assertRaisesRegex(ValueError, "fingerprint"): validate_episode(result["path"])

    def test_missing_collection_gate_prevents_writer(self):
        (self.root / "ALLOW_COLLECTION").unlink()
        with self.assertRaises(ValueError): self.writer()

    def test_unverified_transition_and_sequence_gap_are_rejected(self):
        w = self.writer(); w.observation(self.replies()[0])
        for effect, after in (("UNVERIFIED", 2), ("APPLIED", 3)):
            with self.assertRaises(ValueError): w.transition(before_sequence=1, after_sequence=after, method="endTurn", args=[], effect=effect, policy={})
        w.finish(error="test close")

    def test_wrong_role_seed_and_repeated_observation_are_rejected(self):
        w = self.writer(); before, _ = self.replies(); w.observation(before)
        with self.assertRaises(ValueError): w.observation(before)
        for field, value in (("seed", 999),):
            wrong = copy.deepcopy(before); wrong["sequence"] = 2; wrong["state"][field] = value
            with self.assertRaises(ValueError): w.observation(wrong)
        w.finish(error="test close")

    def test_byte_tampering_is_detected(self):
        result = self.complete(); path = Path(result["path"])
        with gzip.open(path, "rt", encoding="utf-8") as stream: rows = [json.loads(line) for line in stream]
        rows[1]["record"]["state"]["player"]["gold"] += 1
        with gzip.open(path, "wt", encoding="utf-8") as stream:
            for row in rows: stream.write(json.dumps(row) + "\n")
        with self.assertRaises(ValueError): validate_episode(path)


if __name__ == "__main__": unittest.main()
