import os
import tempfile
import unittest

from sts_ai_framework.value_checkpoint import resolve_value_checkpoint


class ValueCheckpointTests(unittest.TestCase):
    def test_defaults_to_general_checkpoint(self):
        with tempfile.TemporaryDirectory() as directory:
            path = resolve_value_checkpoint(directory, environ={})

        self.assertEqual(
            path,
            os.path.join(
                os.path.realpath(directory),
                "checkpoints",
                "best_sts_value_model_final.pth",
            ),
        )

    def test_environment_variable_selects_explicit_checkpoint(self):
        with tempfile.TemporaryDirectory() as directory:
            selected = os.path.join(directory, "best_sts_value_model_a10plus_v1.pth")
            path = resolve_value_checkpoint(
                ".", environ={"STS_VALUE_CHECKPOINT": selected}
            )

        self.assertEqual(path, os.path.realpath(selected))


if __name__ == "__main__":
    unittest.main()
