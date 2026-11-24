"""Archive-independent live telemetry/export/CPU training regressions."""
import copy
import json
from pathlib import Path
import shutil
import sys
import tempfile
import unittest
from unittest.mock import patch

ROOT = Path(__file__).resolve().parents[2]
if str(ROOT) not in sys.path:
    sys.path.insert(0, str(ROOT))

import numpy as np
import pandas as pd
import torch

from selectcard.src.live_smoke import fixture_seed, fixture_state, record_fixture, synthetic_pipeline
from selectcard.src.live_value_data import export_live, read_run
from selectcard.src.live_contract import file_sha256, fingerprint
from selectcard.src.dataset import build_training_artifacts, STSDataset
from selectcard.src.checkpointing import load_checkpoint, save_checkpoint
from selectcard.src.finetune import cpu_smoke, verify_export
from selectcard.src.inference import STSInferenceEngine
from selectcard.src.data_contract import MASK_COLUMNS
from selectcard.src.config import Config
from selectcard.routing_calibration import load_model, predict, cpu_smoke as routing_smoke
from sts_ai_framework.learning_trace import LearningRecorder
from sts_ai_framework.llm_agent_parts.value_payload import value_state_payload
from sts_ai_framework.models import ActionType, GameAction


def rewrite(path, rows):
    path.write_text("".join(json.dumps(r) + "\n" for r in rows), encoding="utf-8")


class LiveExportTests(unittest.TestCase):
    def make_traces(self, root, *, censored=False):
        recorder = LearningRecorder(root / "trace", metadata={"synthetic": True})
        for split in ("train", "val", "test"):
            record_fixture(recorder, fixture_seed(split), censored=censored)
        recorder.close()
        return recorder.directory

    def test_exports_only_actual_post_state_signed_loss_and_masks(self):
        with tempfile.TemporaryDirectory() as root:
            root = Path(root)
            trace = self.make_traces(root, censored=True)
            report = export_live(trace, root / "data", chunk_size=1)
            self.assertEqual(report["samples"]["value:train"], 1)
            for split in ("train", "val", "test"):
                frame = pd.read_parquet(next((root / "data").glob(split + "_valid_*.parquet")))
                self.assertEqual(frame.iloc[0]["deck"].tolist(), ["Strike_R", "Strike_R"])
                self.assertFalse(frame.iloc[0]["terminal_known"])
                self.assertFalse(frame.iloc[0]["valid_heart_win"])
                self.assertEqual(frame.iloc[0]["terminal_floor"], 6)
                self.assertTrue(frame.iloc[0]["valid_stop_before_06"])
                self.assertFalse(frame.iloc[0]["valid_stop_before_09"])
            rooms = [json.loads(l) for l in (root / "data" / "rooms.jsonl").read_text().splitlines()]
            self.assertTrue(all(r["net_hp_loss"] == -5 and r["regression_valid"] for r in rooms))
            self.assertEqual({r["split"] for r in rooms}, {"train", "val", "test"})
            verify_export(root / "data")

    def test_exact_run_duplicates_deduplicate_conflicting_copies_fail(self):
        with tempfile.TemporaryDirectory() as root:
            root = Path(root)
            trace = self.make_traces(root)
            source = next(trace.glob("*.jsonl"))
            duplicate = trace / "copy.jsonl"
            shutil.copyfile(source, duplicate)
            report = export_live(trace, root / "dedup")
            self.assertEqual(report["runs"], 3)
            self.assertEqual(report["exclusions"]["duplicate_run"], 1)
            rows = read_run(duplicate)
            rows[0]["started_at"] += 1
            rewrite(duplicate, rows)
            with self.assertRaisesRegex(ValueError, "conflicting copies"):
                export_live(trace, root / "conflict")

    def test_forged_matches_hidden_fields_and_sequences_are_rejected(self):
        for corruption in ("matched_state", "hidden", "sequence", "submitted"):
            with self.subTest(corruption=corruption), tempfile.TemporaryDirectory() as root:
                root = Path(root)
                trace = self.make_traces(root)
                path = next(trace.glob("*.jsonl"))
                rows = read_run(path)
                if corruption == "matched_state":
                    query = next(r for r in rows if r["record_type"] == "value_query")
                    query["state"]["hp"] = 1
                    decision = next(r for r in rows if r["record_type"] == "decision")
                    decision["matched_query_ids"] = [query["query_id"]]
                elif corruption == "hidden":
                    next(r for r in rows if r["record_type"] == "value_query")["state"]["seed_group"] = "leak"
                elif corruption == "sequence":
                    rows[1]["sequence"] += 1
                else:
                    next(r for r in rows if r["record_type"] == "decision")["command"] = {"type": "cancel"}
                rewrite(path, rows)
                with self.assertRaises(ValueError):
                    export_live(trace, root / "data")

    def test_unclosed_log_is_censored_and_output_is_exclusive(self):
        with tempfile.TemporaryDirectory() as root:
            root = Path(root)
            trace = self.make_traces(root)
            for path in trace.glob("*.jsonl"):
                rewrite(path, read_run(path)[:-1])
            export_live(trace, root / "data")
            frame = pd.read_parquet(next((root / "data").glob("train_valid_*.parquet")))
            self.assertFalse(frame.iloc[0]["valid_heart_win"])
            original = {p.name: file_sha256(p) for p in (root / "data").iterdir()}
            with self.assertRaises(FileExistsError):
                export_live(trace, root / "data")
            self.assertEqual(original, {p.name: file_sha256(p) for p in (root / "data").iterdir()})

    def test_truncated_final_record_preserves_only_censored_prefix(self):
        with tempfile.TemporaryDirectory() as root:
            root = Path(root)
            trace = self.make_traces(root)
            for path in trace.glob("*.jsonl"):
                rewrite(path, read_run(path)[:-1])
                with path.open("a", encoding="utf-8") as handle:
                    handle.write('{"schema": "live-learning-v1", "record_type": "run_end"')
            export_live(trace, root / "data")
            frame = pd.read_parquet(next((root / "data").glob("train_valid_*.parquet")))
            self.assertFalse(frame.iloc[0]["terminal_known"])
            self.assertFalse(frame.iloc[0]["valid_heart_win"])

    def test_death_evidence_survives_missing_run_footer(self):
        with tempfile.TemporaryDirectory() as root:
            root = Path(root)
            recorder = LearningRecorder(root / "trace")
            record_fixture(recorder, fixture_seed("train"), died=True)
            path = next((root / "trace").glob("*.jsonl"))
            rewrite(path, read_run(path)[:-1])
            export_live(root / "trace", root / "data")
            rooms = [json.loads(line) for line in (root / "data" / "rooms.jsonl").read_text().splitlines()]
            self.assertEqual(len(rooms), 1)
            self.assertTrue(rooms[0]["died"])
            self.assertFalse(rooms[0]["regression_valid"])
            frame = pd.read_parquet(next((root / "data").glob("train_valid_*.parquet")))
            self.assertFalse(frame.iloc[0]["valid_heart_win"])

    def test_grid_intent_preserves_root_queries_until_actual_upgrade(self):
        with tempfile.TemporaryDirectory() as root:
            root = Path(root)
            recorder = LearningRecorder(root / "trace")
            before = fixture_state(screen_type="REST", choice_list=["smith"], seed_group=fixture_seed("train"))
            recorder.observe(before, 0)
            after = before.model_copy(deep=True)
            after.deck[0].upgrades = 1
            after.screen_type = "MAP"
            recorder.value_query(value_state_payload(after), dict(expected_floor=20., heart_probability=.1, value=.3))
            action = GameAction(type=ActionType.CHOOSE, choice_index=0)
            recorder.begin(before, action, 1)
            grid = before.model_copy(update={"screen_type": "GRID", "grid_purpose": "upgrade", "grid_num_cards": 1,
                "grid_cards": before.deck, "choice_list": ["Strike"]})
            recorder.observe(grid, 2)
            recorder.begin(grid, action, 3)
            recorder.observe(after, 4)
            terminal = after.model_copy(update={"screen_type": "GAME_OVER", "game_over_reason": "defeat", "seed_group": None})
            recorder.observe(terminal, 5)
            export_live(root / "trace", root / "data")
            frame = pd.read_parquet(next((root / "data").glob("train_valid_*.parquet")))
            self.assertEqual(len(frame), 1)
            self.assertEqual(frame.iloc[0]["intent_id"], "1")
            self.assertEqual(frame.iloc[0]["deck"].tolist(), ["Strike_R+1"])


class SyntheticTrainingTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.temporary = tempfile.TemporaryDirectory()
        cls.root = Path(cls.temporary.name) / "smoke"
        with patch("socket.create_connection", side_effect=AssertionError("synthetic workflow contacted a socket")):
            cls.report = synthetic_pipeline(cls.root)

    @classmethod
    def tearDownClass(cls):
        cls.temporary.cleanup()

    def test_cpu_end_to_end_reload_and_parent_preservation(self):
        self.assertEqual(self.report["device"], "cpu")
        self.assertEqual(self.report["real_games_started"], 0)
        self.assertFalse(self.report["promotable"])
        parent, vocab, encoder = load_checkpoint(self.root / "synthetic-parent.pth")
        child, child_vocab, child_encoder = load_checkpoint(self.root / "value" / "smoke.pth")
        self.assertEqual(vocab.to_dict(), child_vocab.to_dict())
        self.assertEqual(encoder.to_dict(), child_encoder.to_dict())
        self.assertEqual(parent["metadata"]["baseline_statistics"], child["metadata"]["baseline_statistics"])
        self.assertEqual(file_sha256(self.root / "synthetic-parent.pth"), self.report["value"]["parent_sha256"])
        changed = [n for n, p in parent["model_state_dict"].items() if not torch.equal(p, child["model_state_dict"][n])]
        self.assertTrue(changed)
        self.assertTrue(all(n.startswith("value_head.") for n in changed))
        self.assertEqual(child["metadata"]["experiment"]["stage"], "cpu-smoke")
        with self.assertRaises(FileExistsError):
            cpu_smoke(self.root / "data", self.root / "synthetic-parent.pth", self.root / "value")

    def test_dataset_and_online_encoder_tensor_parity_with_observer(self):
        path = self.root / "value" / "smoke.pth"
        child, vocabulary, encoder = load_checkpoint(path)
        dataset = STSDataset(self.root / "data", vocabulary, encoder, "train", max_seq_len=73, use_map_context=True)
        row = pd.read_parquet(next((self.root / "data").glob("train_valid_*.parquet"))).iloc[0]
        actual = None
        for p in (self.root / "traces").glob("*.jsonl"):
            records = read_run(p)
            if records[0]["run_id"] == row["run_id"]:
                actual = next(r for r in records if r["record_type"] == "decision")["after"]["value_state"]
        engine = STSInferenceEngine(path)
        item = dataset[0]
        for expected, observed in zip(item[:3], engine.tokenizer.encode(actual["deck"], actual["relics"])):
            torch.testing.assert_close(expected, observed[0])
        torch.testing.assert_close(item[3], engine._global_features(actual)[0])
        for expected, observed in zip((item[9], item[10], item[11], item[12], item[13], item[16]), engine._context_tensors(actual)):
            torch.testing.assert_close(expected, observed[0])
        captured = []
        engine.trace_observer = lambda state, result: captured.append((copy.deepcopy(state), copy.deepcopy(result)))
        prediction = engine.evaluate_state_distribution(actual)
        self.assertEqual(len(captured), 1)
        self.assertEqual(captured[0][1], prediction)
        self.assertTrue(0 <= prediction["value"] <= 1)

    def test_last2_updates_keep_embeddings_and_key_head_frozen(self):
        report = cpu_smoke(self.root / "data", self.root / "synthetic-parent.pth", self.root / "last2", steps=1, scope="last2")
        parent, _, _ = load_checkpoint(self.root / "synthetic-parent.pth")
        child, _, _ = load_checkpoint(self.root / "last2" / "smoke.pth")
        changed = [n for n, p in parent["model_state_dict"].items() if not torch.equal(p, child["model_state_dict"][n])]
        self.assertTrue(any(n.startswith("layers.") for n in changed))
        self.assertTrue(all(n.startswith(("layers.", "final_norm.", "value_head.")) for n in changed))
        self.assertEqual(report["steps"], 1)

    def test_tampered_export_rejected_and_global_loss_settings_restored(self):
        copy_dir = self.root / "tampered"
        shutil.copytree(self.root / "data", copy_dir)
        with (copy_dir / "rooms.jsonl").open("a") as handle:
            handle.write("{}\n")
        with self.assertRaisesRegex(ValueError, "fingerprint"):
            verify_export(copy_dir)
        self.assertEqual(Config.RESOLUTION_MODE, "multiscale")

    def test_map_death_masks_reload_and_test_rows_never_fit(self):
        rows_path = self.root / "data" / "rooms.jsonl"
        rows = [json.loads(l) for l in rows_path.read_text().splitlines()]
        self.assertTrue(any(r["died"] and r["net_hp_loss"] is None and not r["regression_valid"] for r in rows))
        model = load_model(self.root / "routing" / "calibration.json")
        self.assertEqual(model["train_samples"], 2)
        self.assertEqual(model["train_deaths"], 1)
        self.assertTrue(0 <= predict(model, rows[0])["death_probability"] <= 1)
        for row in rows:
            if row["split"] == "test":
                row["before"]["resources"]["hp"] = 999999
                row["net_hp_loss"] = -999999
        altered = self.root / "test-extremes.jsonl"
        rewrite(altered, rows)
        routing_smoke(altered, self.root / "routing-no-test", steps=20)
        second = load_model(self.root / "routing-no-test" / "calibration.json")
        for key in ("mean", "scale", "weight", "bias"):
            self.assertEqual(model[key], second[key])

    def test_value_test_extremes_do_not_change_training_weights(self):
        copy_dir = self.root / "value-test-extremes"
        shutil.copytree(self.root / "data", copy_dir)
        test_shard = next(copy_dir.glob("test_valid_*.parquet"))
        frame = pd.read_parquet(test_shard)
        frame["gold"] = 999999
        frame["deck"] = [list(items) + ["TEST_ONLY_ITEM"] for items in frame["deck"]]
        frame.to_parquet(test_shard, index=False)
        manifest_path = copy_dir / "dataset_manifest.json"
        manifest = json.loads(manifest_path.read_text(encoding="utf-8"))
        manifest["files"][test_shard.name] = file_sha256(test_shard)
        manifest_path.write_text(json.dumps(manifest), encoding="utf-8")
        cpu_smoke(copy_dir, self.root / "synthetic-parent.pth", self.root / "value-no-test", steps=2)
        first, _, _ = load_checkpoint(self.root / "value" / "smoke.pth")
        second, _, _ = load_checkpoint(self.root / "value-no-test" / "smoke.pth")
        for name, weight in first["model_state_dict"].items():
            torch.testing.assert_close(weight, second["model_state_dict"][name], rtol=0, atol=0)

    def test_parent_zero_weight_new_band_is_rejected(self):
        parent, vocabulary, encoder = load_checkpoint(self.root / "synthetic-parent.pth")
        parent["metadata"]["dataset"]["difficulty_weights"] = [1.] + [0.] * 5
        alternate = self.root / "unsupported-band-parent.pth"
        torch.save(parent, alternate)
        with self.assertRaisesRegex(ValueError, "zero weight"):
            cpu_smoke(self.root / "data", alternate, self.root / "unsupported-band")
        self.assertFalse((self.root / "unsupported-band").exists())


if __name__ == "__main__":
    unittest.main()
