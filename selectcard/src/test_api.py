import os
import sys
import unittest


sys.path.insert(0, os.path.dirname(os.path.dirname(__file__)))
from src.card_input import normalize_card_reference


class CardInputNormalizationTest(unittest.TestCase):
    def test_uses_internal_id_and_explicit_upgrade_level(self):
        self.assertEqual(
            normalize_card_reference({"id": "Searing Blow", "upgrades": 12}),
            "Searing Blow+12",
        )
        self.assertEqual(
            normalize_card_reference({"id": "Bash", "upgrades": 0}),
            "Bash",
        )

    def test_rejects_missing_or_invalid_upgrade_data(self):
        with self.assertRaises(ValueError):
            normalize_card_reference({"name": "Bash", "upgrades": 1})
        with self.assertRaises(ValueError):
            normalize_card_reference({"id": "Bash", "upgrades": -1})


if __name__ == "__main__":
    unittest.main()
