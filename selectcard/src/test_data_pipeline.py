import glob
import gzip
import json
import os
import tempfile
import unittest
from unittest import mock

import pandas as pd
import torch

from content_catalog import VanillaContentCatalog, canonical_card_id
from data_contract import (
    FILTER_VERSION,
    HAZARD_ENDPOINTS,
    MASK_COLUMNS,
    PREPROCESSING_VERSION,
    TARGET_COLUMNS,
    VALUE_TARGET_SCHEMA,
    ascension_band,
)
from data_pipeline import (
    RunRejected,
    assign_split,
    build_dataset,
    process_file,
    process_run,
    stable_run_id,
    validate_raw_run,
)
from dataset import ChunkShuffleSampler, STSDataset, build_training_artifacts
from train import dataloader_worker_count, difficulty_weights, weighted_hazard_loss


STARTER_DECK = ["Strike_R"] * 5 + ["Defend_R"] * 4 + ["Bash"]


def standard_run(**overrides):
    run = {
        "is_daily": False,
        "is_trial": False,
        "is_endless": False,
        "chose_seed": False,
        "is_beta": False,
        "special_seed": 0,
        "is_ascension_mode": False,
        "ascension_level": 0,
        "local_time": "20200801000000",
        "build_version": "2020-07-30",
        "character_chosen": "IRONCLAD",
        "floor_reached": 1,
        "victory": False,
        "killed_by": "Cultist",
        "master_deck": list(STARTER_DECK),
        "relics": ["Burning Blood"],
        "card_choices": [
            {"floor": 1, "picked": "SKIP", "not_picked": ["Anger", "Cleave"]}
        ],
        "items_purged": [],
        "items_purged_floors": [],
        "items_purchased": [],
        "item_purchase_floors": [],
        "relics_obtained": [],
        "boss_relics": [],
        "event_choices": [],
        "campfire_choices": [],
        "potions_obtained": [],
        "damage_taken": [
            {"floor": 1, "enemies": "Cultist", "damage": 10, "turns": 3}
        ],
        "current_hp_per_floor": [70],
        "max_hp_per_floor": [80],
        "gold_per_floor": [99],
        "path_per_floor": ["M"],
        "path_taken": ["M"],
    }
    run.update(overrides)
    return run


class ContentCatalogTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.catalog = VanillaContentCatalog.from_repo()

    def test_catalog_contains_known_vanilla_content(self):
        self.assertIn("Bash", self.catalog.cards)
        self.assertIn("Burning Blood", self.catalog.relics)
        self.assertIn("PowerPotion", self.catalog.potions)
        self.assertIn("Cultist", self.catalog.enemies)
        self.assertEqual(canonical_card_id("SearingBlow+12"), "SearingBlow")
        self.assertEqual(self.catalog.classify_item("Snecko Skull"), "relic")

    def test_unknown_content_is_rejected(self):
        run = standard_run(master_deck=STARTER_DECK + ["ExampleMod:Card"])
        with self.assertRaisesRegex(RunRejected, "unknown_card"):
            validate_raw_run(run, self.catalog)

        run = standard_run(
            damage_taken=[
                {"floor": 1, "enemies": "ExampleMod:Enemy", "damage": 1, "turns": 1}
            ],
            killed_by="ExampleMod:Enemy",
        )
        with self.assertRaisesRegex(RunRejected, "unknown_enemy"):
            validate_raw_run(run, self.catalog)


class FilteringTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.catalog = VanillaContentCatalog.from_repo()

    def test_standard_modes_and_versions_are_required(self):
        for field in ("is_daily", "is_trial", "is_endless", "chose_seed", "is_beta"):
            with self.subTest(field=field):
                with self.assertRaisesRegex(RunRejected, f"nonstandard_{field}"):
                    validate_raw_run(standard_run(**{field: True}), self.catalog)
        with self.assertRaisesRegex(RunRejected, "old_build_version"):
            validate_raw_run(standard_run(build_version="2019-12-31"), self.catalog)

    def test_ascension_modes_cover_a0_through_a20(self):
        self.assertEqual(validate_raw_run(standard_run(), self.catalog)["ascension"], 0)
        a10 = standard_run(is_ascension_mode=True, ascension_level=10)
        self.assertEqual(validate_raw_run(a10, self.catalog)["ascension_band"], 2)
        a20 = standard_run(is_ascension_mode=True, ascension_level=20)
        self.assertEqual(validate_raw_run(a20, self.catalog)["ascension_band"], 5)
        with self.assertRaisesRegex(RunRejected, "inconsistent_ascension_mode"):
            validate_raw_run(standard_run(ascension_level=1), self.catalog)

    def test_shop_cards_relics_and_potions_are_classified(self):
        run = standard_run(
            items_purchased=["Anger", "PowerPotion", "Vajra"],
            item_purchase_floors=[1, 1, 1],
            master_deck=STARTER_DECK + ["Anger"],
            relics=["Burning Blood", "Vajra"],
        )
        result = process_run(run, self.catalog)
        self.assertEqual(len(result["rows"]), 1)
        deck = result["rows"][0]["deck"].split(",")
        relics = result["rows"][0]["relics"].split(",")
        self.assertIn("Anger", deck)
        self.assertNotIn("PowerPotion", deck)
        self.assertIn("Vajra", relics)

    def test_boss_relic_snapshots_use_next_act_floors(self):
        floors = 34
        run = standard_run(
            floor_reached=floors,
            boss_relics=[
                {"picked": "Coffee Dripper", "not_picked": ["Sozu", "Ectoplasm"]},
                {"picked": "Sozu", "not_picked": ["Ectoplasm", "Black Star"]},
            ],
            relics=["Burning Blood", "Coffee Dripper", "Sozu"],
            current_hp_per_floor=[70] * floors,
            max_hp_per_floor=[80] * floors,
            gold_per_floor=[99] * floors,
            path_per_floor=["M"] * floors,
            path_taken=["M"] * floors,
        )
        result = process_run(run, self.catalog)
        boss_rows = [row for row in result["rows"] if row["decision_type"] == "boss_relic"]
        self.assertEqual([row["floor"] for row in boss_rows], [17, 34])
        self.assertIn("Coffee Dripper", boss_rows[0]["relics"].split(","))
        self.assertIn("Sozu", boss_rows[1]["relics"].split(","))

    def test_final_deck_must_match_exactly(self):
        run = standard_run(master_deck=STARTER_DECK + ["Anger"])
        with self.assertRaisesRegex(RunRejected, "deck_reconstruction_mismatch"):
            process_run(run, self.catalog)

    def test_choice_requires_matching_resource_history(self):
        choice = {"floor": 2, "picked": "SKIP", "not_picked": ["Anger", "Cleave"]}
        run = standard_run(
            floor_reached=2,
            card_choices=[choice],
            path_per_floor=["M", "M"],
        )
        with self.assertRaisesRegex(RunRejected, "incomplete_resource_history"):
            validate_raw_run(run, self.catalog)

    def test_bad_run_does_not_discard_valid_run_from_same_archive(self):
        with tempfile.TemporaryDirectory() as directory:
            path = os.path.join(directory, "runs.json.gz")
            invalid = standard_run(master_deck=STARTER_DECK + ["ExampleMod:Card"])
            with gzip.open(path, "wt", encoding="utf-8") as handle:
                json.dump([{"event": standard_run()}, {"event": invalid}], handle)
            result = process_file(path, self.catalog)
        self.assertEqual(len(result["accepted"]), 1)
        self.assertEqual(result["stats"]["raw_runs"], 2)
        self.assertEqual(result["stats"]["rejections"]["unknown_card"], 1)


class DatasetBuildTests(unittest.TestCase):
    def test_synthetic_gzip_builds_versioned_dataset_and_manifest(self):
        with tempfile.TemporaryDirectory() as directory:
            input_dir = os.path.join(directory, "input")
            output_dir = os.path.join(directory, "output")
            os.makedirs(input_dir)
            path = os.path.join(input_dir, "runs.json.gz")
            run = standard_run()
            suffix = 0
            while assign_split(stable_run_id(run)) != "train":
                suffix += 1
                run["play_id"] = f"synthetic-{suffix}"
            with gzip.open(path, "wt", encoding="utf-8") as handle:
                json.dump([{"event": run}], handle)

            manifest = build_dataset(input_dir, output_dir, workers=1)
            valid_files = glob.glob(os.path.join(output_dir, "*_valid_chunk_*.parquet"))
            self.assertEqual(len(valid_files), 1)
            frame = pd.read_parquet(valid_files[0])
            with open(
                os.path.join(output_dir, "dataset_manifest.json"),
                "r",
                encoding="utf-8",
            ) as handle:
                stored_manifest = json.load(handle)
            vocabulary, encoder = build_training_artifacts(output_dir)
            dataset = STSDataset(output_dir, vocabulary, encoder, split="train")
            shuffled_indices = list(
                ChunkShuffleSampler(dataset, torch.Generator().manual_seed(7))
            )

        self.assertEqual(manifest["output"]["accepted_runs"], 1)
        self.assertEqual(stored_manifest["filter_version"], FILTER_VERSION)
        self.assertEqual(stored_manifest["value_target_schema"], VALUE_TARGET_SCHEMA)
        self.assertEqual(stored_manifest["hazard_endpoints"], list(HAZARD_ENDPOINTS))
        self.assertTrue(stored_manifest["distributions"]["at_risk_samples_by_hazard"])
        self.assertTrue(stored_manifest["distributions"]["stop_samples_by_hazard"])
        self.assertEqual(set(frame["preprocessing_version"]), {PREPROCESSING_VERSION})
        self.assertEqual(set(frame["filter_version"]), {FILTER_VERSION})
        self.assertEqual(set(frame["ascension_band"]), {0})
        self.assertEqual(len(dataset), 1)
        self.assertEqual(shuffled_indices, [0])

    def test_legacy_parquet_schema_is_rejected(self):
        with tempfile.TemporaryDirectory() as directory:
            frame = pd.DataFrame(
                [
                    {
                        "run_id": "legacy",
                        "split": "train",
                        "target_valid": True,
                        "preprocessing_version": "set-transformer-v2",
                    }
                ]
            )
            frame.to_parquet(
                os.path.join(directory, "train_valid_chunk_00000.parquet"), index=False
            )
            with self.assertRaisesRegex(ValueError, "missing columns"):
                build_training_artifacts(directory)


class DifficultyWeightingTests(unittest.TestCase):
    def test_dataloader_workers_leave_one_slurm_cpu_for_training(self):
        with mock.patch.dict(
            os.environ, {"SLURM_CPUS_PER_TASK": "16"}, clear=True
        ):
            self.assertEqual(dataloader_worker_count(), 15)

    def test_dataloader_worker_override_can_disable_multiprocessing(self):
        with mock.patch.dict(
            os.environ,
            {"SLURM_CPUS_PER_TASK": "16", "STS_DATALOADER_WORKERS": "0"},
            clear=True,
        ):
            self.assertEqual(dataloader_worker_count(), 0)

    def test_band_boundaries(self):
        self.assertEqual([ascension_band(x) for x in (0, 1, 5, 6, 10, 11, 15, 16, 19, 20)], [0, 1, 1, 2, 2, 3, 3, 4, 4, 5])

    def test_inverse_frequency_weights_equalize_total_contribution(self):
        counts = [100, 50, 25, 20, 10, 5]
        manifest = {
            "distributions": {
                "train_valid_samples_by_ascension_band": {
                    str(index): count for index, count in enumerate(counts)
                }
            }
        }
        weights = difficulty_weights(manifest)
        contributions = [count * weight for count, weight in zip(counts, weights)]
        for contribution in contributions[1:]:
            self.assertAlmostEqual(contribution, contributions[0])

        logits = torch.zeros((6, 21))
        labels = torch.zeros((6, 21))
        masks = torch.ones((6, 21))
        floors = torch.ones(6)
        globals_ = torch.zeros((6, 9))
        globals_[:, 8] = torch.tensor([0, 1, 6, 11, 16, 20]) / 20.0
        loss = weighted_hazard_loss(
            logits, labels, masks, floors, globals_, torch.tensor(weights)
        )
        self.assertTrue(torch.isfinite(loss))


if __name__ == "__main__":
    unittest.main()
