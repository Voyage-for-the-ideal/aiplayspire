import json
import os
import tempfile
import unittest

import pandas as pd

from boss_context import (
    BOSS_SCHEMA_VERSION, BOSS_VOCABULARY, NO_BOSS, UNKNOWN_BOSS, BossContextResolver,
    boss_id, boss_name, canonicalize_boss_name,
)
from enrich_boss_context import enrich_processed_dataset, visible_boss_for_sample


class BossContextTests(unittest.TestCase):
    def test_vocabulary_is_fixed_and_unknown_is_distinct(self):
        self.assertEqual(BOSS_SCHEMA_VERSION, "visible-boss-v1")
        self.assertEqual(len(BOSS_VOCABULARY), len(set(BOSS_VOCABULARY.values())))
        self.assertNotEqual(NO_BOSS, UNKNOWN_BOSS)
        self.assertEqual(boss_id("new modded boss"), UNKNOWN_BOSS)
        self.assertEqual(boss_name(NO_BOSS), "NO_BOSS")

    def test_encounter_aliases_are_canonical(self):
        self.assertEqual(canonicalize_boss_name("TheGuardian"), "The Guardian")
        self.assertEqual(canonicalize_boss_name("Donu&Deca"), "Donu and Deca")
        self.assertEqual(canonicalize_boss_name("The Corrupt Heart"), "Corrupt Heart")

    def test_snapshot_rules_do_not_use_outcomes(self):
        context = {"act1_boss": "Hexaghost", "act2_boss": "Champ", "act3_boss": "Time Eater"}
        self.assertEqual(visible_boss_for_sample(8, "card_reward", 20, context), "Hexaghost")
        self.assertEqual(visible_boss_for_sample(16, "boss_card_reward", 20, context), "NO_BOSS")
        self.assertEqual(visible_boss_for_sample(17, "boss_relic", 20, context), "NO_BOSS")
        self.assertEqual(visible_boss_for_sample(17, "card_reward", 20, context), "Champ")
        # A0 never uses a real boss identity; A1+ resolves from the seed.
        self.assertEqual(visible_boss_for_sample(8, "card_reward", 0, context), "NO_BOSS")
        self.assertEqual(visible_boss_for_sample(8, "card_reward", 1, context), "Hexaghost")
        self.assertEqual(visible_boss_for_sample(8, "card_reward", 14, context), "Hexaghost")
        self.assertEqual(visible_boss_for_sample(8, "card_reward", 15, context), "Hexaghost")

    def test_resolution_never_reads_run_outcomes(self):
        # Plan section 31: two runs with the same seed must resolve to the same
        # visible boss even when one dies early and the other reaches the boss.
        # The feature path may never consult damage_taken, victory, killed_by,
        # or floor_reached to decide boss identity.
        base = {"seed_played": "42", "ascension_level": 20, "is_ascension_mode": True}
        dead_run = {
            **base, "floor_reached": 8,
            "damage_taken": [{"floor": 4, "enemies": "Gremlin Nob", "damage": 12}],
            "victory": False, "killed_by": "Gremlin Nob",
        }
        boss_run = {
            **base, "floor_reached": 16,
            "damage_taken": [{"floor": 16, "enemies": "Hexaghost", "damage": 0}],
            "victory": False, "killed_by": "Hexaghost",
        }
        dead_context = BossContextResolver().resolve_run(dead_run)
        boss_context = BossContextResolver().resolve_run(boss_run)
        self.assertEqual(dead_context, boss_context)
        self.assertEqual(
            visible_boss_for_sample(8, "card_reward", 20, dead_context),
            visible_boss_for_sample(8, "card_reward", 20, boss_context),
        )
        # And the rule applied to the survived run is identical.
        self.assertEqual(
            visible_boss_for_sample(15, "card_reward", 20, boss_context),
            dead_context["act1_boss"],
        )

    def test_resolution_is_deterministic_per_seed(self):
        # Frozen corpus anchors: seed 42 resolves to these bosses.  Guards the
        # resolver against RNG-port regressions and against silently returning
        # a constant.
        resolved = BossContextResolver().resolve_run(
            {"seed_played": "42", "ascension_level": 20, "is_ascension_mode": True}
        )
        self.assertEqual(resolved["act1_boss"], "Hexaghost")
        self.assertEqual(resolved["act2_boss"], "Automaton")
        self.assertEqual(resolved["act3_boss"], "Donu and Deca")
        self.assertEqual(
            BossContextResolver().resolve_run(
                {"seed_played": "43", "ascension_level": 20, "is_ascension_mode": True}
            )["act1_boss"],
            "Slime Boss",
        )
        # A real corpus run (2020-01-27 build) whose observed Act I boss is
        # The Guardian; regression anchor for the vanilla RNG consumption order.
        self.assertEqual(
            BossContextResolver().resolve_run(
                {"seed_played": "4537055902605411270", "ascension_level": 2, "is_ascension_mode": True}
            )["act1_boss"],
            "The Guardian",
        )

    def test_enrichment_writes_new_dataset_and_rejects_unknowns(self):
        with tempfile.TemporaryDirectory() as directory:
            source, output = os.path.join(directory, "source"), os.path.join(directory, "output")
            os.mkdir(source)
            pd.DataFrame([
                {"run_id": "r", "floor": 8, "decision_type": "card_reward", "ascension": 20, "split": "train", "ascension_band": 5},
                {"run_id": "a0", "floor": 8, "decision_type": "card_reward", "ascension": 0, "split": "train", "ascension_band": 0},
            ]).to_parquet(os.path.join(source, "train_valid_chunk_00000.parquet"), index=False)
            sidecar = os.path.join(directory, "bosses.parquet")
            pd.DataFrame([
                {"run_id": "r", "act1_boss": "Hexaghost", "act2_boss": "Champ", "act3_boss": "Time Eater", "resolver_status": "resolved"},
                {"run_id": "a0", "act1_boss": "NO_BOSS", "act2_boss": "NO_BOSS", "act3_boss": "NO_BOSS", "resolver_status": "a0_no_boss"},
            ]).to_parquet(sidecar, index=False)
            with open(os.path.join(source, "dataset_manifest.json"), "w", encoding="utf-8") as handle:
                json.dump({}, handle)
            enrich_processed_dataset(source, sidecar, output, resolver_mismatch_count=3)
            frame = pd.read_parquet(os.path.join(output, "train_valid_chunk_00000.parquet"))
            self.assertEqual(frame.iloc[0]["visible_boss"], "Hexaghost")
            self.assertEqual(frame.iloc[1]["visible_boss"], "NO_BOSS")
            with open(os.path.join(output, "dataset_manifest.json"), "r", encoding="utf-8") as handle:
                stored = json.load(handle)
            self.assertEqual(stored["boss_context"]["a0_policy"], "NO_BOSS")
            self.assertEqual(stored["boss_context"]["audit"]["resolver_mismatch_count"], 3)
            self.assertEqual(stored["boss_context"]["distributions"]["no_boss_count"], 1)
            self.assertEqual(stored["boss_context"]["distributions"]["unknown_boss_count"], 0)

    def test_enrichment_rejects_unresolved_a1_plus(self):
        with tempfile.TemporaryDirectory() as directory:
            source = os.path.join(directory, "source")
            os.mkdir(source)
            pd.DataFrame([{"run_id": "r", "floor": 8, "decision_type": "card_reward", "ascension": 20}]).to_parquet(os.path.join(source, "train_valid_chunk_00000.parquet"), index=False)
            sidecar = os.path.join(directory, "bosses.parquet")
            pd.DataFrame([{"run_id": "r", "act1_boss": "UNKNOWN_BOSS", "act2_boss": "UNKNOWN_BOSS", "act3_boss": "UNKNOWN_BOSS", "resolver_status": "resolved"}]).to_parquet(sidecar, index=False)
            with open(os.path.join(source, "dataset_manifest.json"), "w", encoding="utf-8") as handle:
                json.dump({}, handle)
            with self.assertRaisesRegex(ValueError, "UNKNOWN_BOSS"):
                enrich_processed_dataset(source, sidecar, os.path.join(directory, "output"))

    def test_enrichment_rejects_unsupported_resolver_status(self):
        with tempfile.TemporaryDirectory() as directory:
            source = os.path.join(directory, "source")
            os.mkdir(source)
            pd.DataFrame([{"run_id": "r", "floor": 8, "decision_type": "card_reward", "ascension": 20}]).to_parquet(os.path.join(source, "train_valid_chunk_00000.parquet"), index=False)
            sidecar = os.path.join(directory, "bosses.parquet")
            pd.DataFrame([{"run_id": "r", "act1_boss": "Hexaghost", "act2_boss": "Champ", "act3_boss": "Time Eater", "resolver_status": "missing_seed"}]).to_parquet(sidecar, index=False)
            with open(os.path.join(source, "dataset_manifest.json"), "w", encoding="utf-8") as handle:
                json.dump({}, handle)
            with self.assertRaisesRegex(ValueError, "unsupported resolver statuses"):
                enrich_processed_dataset(source, sidecar, os.path.join(directory, "output"))


if __name__ == "__main__":
    unittest.main()
