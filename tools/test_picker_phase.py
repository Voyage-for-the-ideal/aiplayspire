import unittest

from sts_ai_framework.headless.picker import PickerPhase


class PickerPhaseTest(unittest.TestCase):
    def test_smith_preview_has_no_selected_cards_but_is_ready(self):
        selected, count, preview = (), 1, "card-instance-17"
        self.assertNotEqual(len(selected), count)
        phase = PickerPhase.from_controls(locked=True, can_confirm=True)
        self.assertIs(phase, PickerPhase.CONFIRM)
        self.assertFalse(phase.can_select)
        self.assertEqual(phase.confirmation_targets(selected, preview), (preview,))
        self.assertIs(PickerPhase.from_controls(locked=True, can_confirm=False), PickerPhase.SETTLING)
        with self.assertRaisesRegex(ValueError, "not ready"):
            PickerPhase.SETTLING.confirmation_targets((), preview)


if __name__ == "__main__":
    unittest.main()
