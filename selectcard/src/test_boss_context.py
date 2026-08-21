import json
import os
import tempfile
import unittest

import pandas as pd

from boss_context import (
    BOSS_SCHEMA_VERSION, BOSS_VOCABULARY, NO_BOSS, UNKNOWN_BOSS, boss_id,
    boss_name, canonicalize_boss_name,
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
        self.assertEqual(visible_boss_for_sample(8, "card_reward", 0, context), "NO_BOSS")

    def test_enrichment_writes_new_dataset_and_rejects_unknowns(self):
        with tempfile.TemporaryDirectory() as directory:
            source, output = os.path.join(directory, "source"), os.path.join(directory, "output")
            os.mkdir(source)
            pd.DataFrame([{"run_id": "r", "floor": 8, "decision_type": "card_reward", "ascension": 20}]).to_parquet(os.path.join(source, "train_valid_chunk_00000.parquet"), index=False)
            sidecar = os.path.join(directory, "bosses.parquet")
            pd.DataFrame([{"run_id": "r", "act1_boss": "Hexaghost", "act2_boss": "Champ", "act3_boss": "Time Eater", "resolver_status": "resolved"}]).to_parquet(sidecar, index=False)
            with open(os.path.join(source, "dataset_manifest.json"), "w", encoding="utf-8") as handle:
                json.dump({}, handle)
            enrich_processed_dataset(source, sidecar, output)
            self.assertEqual(pd.read_parquet(os.path.join(output, "train_valid_chunk_00000.parquet")).iloc[0]["visible_boss"], "Hexaghost")


if __name__ == "__main__":
    unittest.main()
