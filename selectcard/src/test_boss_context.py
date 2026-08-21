import unittest

from boss_context import (
    BOSS_SCHEMA_VERSION, BOSS_VOCABULARY, NO_BOSS, UNKNOWN_BOSS, boss_id,
    boss_name, canonicalize_boss_name,
)
from enrich_boss_context import visible_boss_for_sample


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


if __name__ == "__main__":
    unittest.main()
