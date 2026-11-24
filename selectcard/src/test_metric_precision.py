"""Cross-platform MAE accumulation; checkpoint rejection remains strict."""
import unittest

import numpy as np

from train import checkpoint_eligibility
from value_metrics import terminal_floor_mae


class MetricPrecisionTests(unittest.TestCase):
    def test_balanced_endpoints_are_an_exact_tie_for_float32_predictions(self):
        terminal = np.array([0, 57] * 4, dtype=np.float32)
        for value in np.linspace(.01, 56.99, 101, dtype=np.float32):
            predicted = np.full(8, value, dtype=np.float32)
            mae = terminal_floor_mae(predicted, terminal)
            self.assertEqual(mae, 28.5)
            self.assertEqual(checkpoint_eligibility(True, False, mae, 28.5), (True, None))

    def test_guard_still_rejects_actual_regression_and_empty_metrics(self):
        self.assertFalse(checkpoint_eligibility(True, False, 28.50001, 28.5)[0])
        self.assertFalse(checkpoint_eligibility(True, True, 28.5, 28.5)[0])
        self.assertTrue(np.isnan(terminal_floor_mae([], [])))
        self.assertFalse(checkpoint_eligibility(True, False, terminal_floor_mae([], []), 28.5)[0])


if __name__ == "__main__":
    unittest.main()
