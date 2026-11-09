import json
import hashlib
from pathlib import Path
import tempfile
import unittest
import uuid
from unittest.mock import patch

import torch

from .contracts import observable_state, split_for
from .encoding import build_vocabulary, encode_batch
from .from_runs import catalog, export_runs
from .mixed_data import export_mixed, mixed_row
from .profiles import MIXED_PROFILE
from .model import SetPolicy
from .run_replay import CausalRunReplay
from .run_training import train_shards
from .shards import ShardDataset, verify_dataset
from .test_run_policy import arguments, raw_run, write_corpus
from .tests import example


class MixedPolicyTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        torch.set_num_threads(2)

    def test_historical_unknown_and_live_values(self):
        row = example()
        row["pre_state"]["shop_inventory"] = [dict(kind="card", id="Bash", price=50)]
        row["pre_state"]["keys"] = dict(ruby=True, emerald=None, sapphire=False)
        row["pre_state"]["known"].update(shop_inventory=True, keys=True)
        live = mixed_row(row)
        self.assertTrue(live["pre_state"]["known"]["shop_inventory"])
        self.assertTrue(live["pre_state"]["keys"]["ruby"])
        historical = example()
        historical["seed_group"] = "1"
        historical["split"] = split_for("1")
        historical["pre_state"]["potions"] = []
        historical = mixed_row(historical, historical=True)
        self.assertFalse(historical["pre_state"]["known"]["potions"])
        self.assertEqual(live["pre_state"]["input_profile"], MIXED_PROFILE)
        vocab = build_vocabulary([live, historical])
        batch = encode_batch([live, historical], vocab)
        self.assertEqual(batch["labels"].tolist(), [0, 0])
        self.assertIn("context:potions:unknown", vocab)

    def test_mixed_shards_train_and_source_unchanged(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            write_corpus(root / "runs")
            export_runs(root / "runs", root / "historical", stage="confirm")
            old_manifest = (root / "historical" / "manifest.json").read_bytes()
            trace = root / "traces"
            trace.mkdir()
            seed = next(str(uuid.UUID(bytes=hashlib.md5(f"seed:{i}".encode()).digest(), version=3))
                        for i in range(10000) if split_for(str(uuid.UUID(bytes=hashlib.md5(f"seed:{i}".encode()).digest(), version=3))) == "train")
            row = example()
            row["decision_type"] = "SHOP_SCREEN"
            row["pre_state"]["screen_type"] = "SHOP_SCREEN"
            row["candidates"] = [dict(action_id="leave", kind="leave", legal=True)]
            row["chosen_action_id"] = "leave"
            records = [dict(record_type="run_start", run_id="live", seed_group=seed, run_mode=row["run_mode"]),
                       dict(record_type="decision", confirmed=True, run_id="live", decision_id="1",
                            **{k: row[k] for k in ("decision_type", "pre_state", "candidates", "chosen_action_id", "provenance")}),
                       dict(record_type="run_end", run_id="live", outcome=row["outcome"])]
            (trace / "run.jsonl").write_text("\n".join(json.dumps(r) for r in records), encoding="utf-8")
            with patch("selectcard.policy.mixed_data.check_coverage"):
                export_mixed(root / "historical", trace, root / "mixed", stage="confirm")
            manifest = verify_dataset(root / "mixed")
            self.assertEqual(manifest["input_profile"], MIXED_PROFILE)
            self.assertEqual((root / "historical" / "manifest.json").read_bytes(), old_manifest)
            dataset = ShardDataset(root / "mixed", "train")
            try:
                self.assertIn("SHOP_SCREEN", {row["decision_type"] for row in dataset})
            finally:
                dataset.close()
            with patch("selectcard.policy.run_training.check_coverage"):
                train_shards(arguments(root / "mixed", root / "model", epochs=1))
            artifact = torch.load(root / "model" / "best.pt", map_location="cpu", weights_only=True)
            self.assertEqual(artifact["input_profile"], MIXED_PROFILE)
            self.assertIn("SHOP_SCREEN", artifact["supported_decisions"])
            self.assertNotIn("EVENT", artifact["supported_decisions"])

    def test_map_ambiguous_or_winged_is_excluded(self):
        run = raw_run()
        replay = CausalRunReplay(run, catalog())
        replay.relics.append("WingedGreaves")
        self.assertIsNone(replay.map_sample(2, 1))
        replay.relics.remove("WingedGreaves")
        replay.paths = ["B", "M"]
        self.assertIsNone(replay.map_sample(1, 1))

    def test_real_map_label_uses_only_one_step_of_future(self):
        run = raw_run(seed=1)
        run["path_per_floor"] = ["M", "M", "$", "M", "M", "R", "?", "?", "T", "?", "M"]
        first = CausalRunReplay(run, catalog()).map_sample(9, 1)
        self.assertIsNotNone(first)
        self.assertEqual(first["chosen_action_id"], "map:3:9")
        run["path_per_floor"][-1] = "E"
        second = CausalRunReplay(run, catalog()).map_sample(9, 1)
        self.assertEqual(first["pre_state"], second["pre_state"])
        self.assertEqual(first["candidates"], second["candidates"])
        self.assertEqual(first["chosen_action_id"], second["chosen_action_id"])

    def test_historical_seed_matches_bridge_uuid(self):
        row = example()
        row["seed_group"] = "1"
        row["split"] = split_for("1")
        mixed = mixed_row(row, historical=True)
        expected = str(uuid.UUID(bytes=hashlib.md5(b"seed:1").digest(), version=3))
        self.assertEqual(mixed["seed_group"], expected)
        self.assertEqual(mixed["split"], split_for(expected))

    def test_live_shop_uses_mixed_checkpoint_and_masks_illegal(self):
        from sts_ai_framework.policy_runtime import PolicyAgent, candidates_for
        from sts_ai_framework.tests.test_policy_trace import state
        current = state()
        candidates = candidates_for(current)
        row = dict(input_profile=MIXED_PROFILE, pre_state=observable_state(current), candidates=candidates)
        vocabulary = build_vocabulary([row])
        policy = object.__new__(PolicyAgent)
        policy.artifact = dict(supported_decisions=["SHOP_SCREEN"], input_profile=MIXED_PROFILE, vocabulary=vocabulary)
        policy.model = SetPolicy(len(vocabulary), d_model=16, layers=1, heads=4, dropout=0.)
        action, actual = policy._score(current)
        selected = next(c for c in actual if c.get("command", {}).get("choice_index") == action.choice_index) if action.choice_index is not None else next(c for c in actual if c["command"]["type"] == action.type.value)
        self.assertTrue(selected["legal"])


if __name__ == "__main__":
    unittest.main()
