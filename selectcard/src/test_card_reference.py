"""Parity between the framework's ``Card.reference`` and the model's card input.

The framework builds the deck strings the value network consumes, while
``normalize_card_reference`` is the documented entry point for externally
supplied card payloads.  Both must produce the one spelling the tokenizer
understands, so the two implementations are pinned together here rather than
being linked by an import (the framework must not depend on the trainer to
build its input).
"""

import os
import sys
import unittest


sys.path.insert(0, os.path.dirname(os.path.dirname(os.path.dirname(__file__))))

from selectcard.src.card_input import normalize_card_reference
from selectcard.src.encoding import parse_item_name
from sts_ai_framework.models import Card


def make_card(card_id, upgrades=0):
    return Card(
        index=0,
        uuid="test-uuid",
        id=card_id,
        name=card_id,
        cost=1,
        cost_for_turn=1,
        upgrades=upgrades,
    )


CASES = [
    ("Bash", 0, "Bash"),
    ("Bash", 1, "Bash+1"),
    ("Bash", 2, "Bash+2"),
    ("Strike_R", 0, "Strike_R"),
    ("Strike_R", 1, "Strike_R+1"),
    ("Searing Blow", 12, "Searing Blow+12"),
    ("AscendersBane", 0, "AscendersBane"),
]


class CardReferenceTest(unittest.TestCase):
    def test_reference_matches_selectcard_normalizer(self):
        for card_id, upgrades, expected in CASES:
            reference = make_card(card_id, upgrades).reference
            self.assertEqual(reference, expected)
            self.assertEqual(
                reference,
                normalize_card_reference({"id": card_id, "upgrades": upgrades}),
            )

    def test_reference_is_tokenizer_stable(self):
        for card_id, upgrades, _ in CASES:
            reference = make_card(card_id, upgrades).reference
            self.assertEqual(parse_item_name(reference), (card_id, upgrades))

    def test_zero_and_negative_upgrades_are_bare(self):
        self.assertEqual(make_card("Bash", 0).reference, "Bash")
        self.assertEqual(make_card("Bash", -1).reference, "Bash")

    def test_reference_is_not_a_pydantic_field(self):
        self.assertNotIn("reference", Card.model_fields)
        self.assertNotIn("reference", make_card("Bash", 1).model_dump())


if __name__ == "__main__":
    unittest.main()
