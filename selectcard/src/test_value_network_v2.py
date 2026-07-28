import os
import tempfile
import unittest

import pandas as pd
import torch

from checkpointing import load_checkpoint, save_checkpoint
from data_pipeline import act_target, assign_split
from dataset import GlobalFeatureEncoder, build_training_artifacts
from encoding import (
    PREPROCESSING_VERSION,
    ItemVocabulary,
    encode_items,
    parse_item_name,
)
from model import STSValueNetwork


class EncodingTests(unittest.TestCase):
    def test_canonical_upgrade_parsing(self):
        self.assertEqual(parse_item_name("Bash+"), ("Bash", 1))
        self.assertEqual(parse_item_name("Bash+1"), ("Bash", 1))
        self.assertEqual(parse_item_name("SearingBlow+3"), ("SearingBlow", 3))
        self.assertEqual(parse_item_name("Burning Blood"), ("Burning Blood", 0))

    def test_frozen_vocabulary_maps_unknown_without_growth(self):
        vocabulary = ItemVocabulary()
        vocabulary.add("Bash")
        vocabulary.freeze()
        original_size = len(vocabulary)
        self.assertEqual(vocabulary.get_id("Unknown Card"), 1)
        self.assertEqual(vocabulary.add("Unknown Card"), 1)
        self.assertEqual(len(vocabulary), original_size)

    def test_encoding_is_order_independent_and_canonical(self):
        vocabulary = ItemVocabulary()
        for item in ("Bash", "Strike_R", "Burning Blood"):
            vocabulary.add(item)
        vocabulary.freeze()
        first = encode_items(
            ["Strike_R", "Bash+", "Strike_R"],
            ["Burning Blood"],
            vocabulary,
            8,
            15,
            10,
        )
        second = encode_items(
            ["Bash+1", "Strike_R", "Strike_R"],
            ["Burning Blood"],
            vocabulary,
            8,
            15,
            10,
        )
        self.assertEqual(first, second)


class DataContractTests(unittest.TestCase):
    def test_split_is_stable_per_run(self):
        self.assertEqual(assign_split("same-run"), assign_split("same-run"))
        self.assertIn(assign_split("same-run"), {"train", "val", "test"})

    def test_censored_target_preserves_completed_acts(self):
        self.assertEqual(act_target(10, 20, False), (1, True))
        self.assertEqual(act_target(18, 20, False), (0, False))
        self.assertEqual(act_target(18, 20, True), (0, True))


class GlobalFeatureEncoderTests(unittest.TestCase):
    def setUp(self):
        self.encoder = GlobalFeatureEncoder(
            hp_q995=100.0,
            max_hp_q995=200.0,
            gold_q995=1000.0,
        )

    @staticmethod
    def _state(**overrides):
        state = {
            "floor": 10,
            "hp": 20,
            "max_hp": 80,
            "gold": 100,
            "ascension": 20,
        }
        state.update(overrides)
        return state

    def test_feature_schema_has_nine_features_without_floor_progress(self):
        self.assertEqual(len(self.encoder.feature_names), 9)
        self.assertNotIn("floor_progress", self.encoder.feature_names)
        self.assertEqual(len(self.encoder.transform_state(self._state())), 9)

    def test_act_boundaries_and_progress(self):
        cases = {
            0: ((1.0, 0.0, 0.0), 0.0),
            16: ((1.0, 0.0, 0.0), 1.0),
            17: ((0.0, 1.0, 0.0), 0.0),
            33: ((0.0, 1.0, 0.0), 1.0),
            34: ((0.0, 0.0, 1.0), 0.0),
            50: ((0.0, 0.0, 1.0), 1.0),
            51: ((0.0, 0.0, 1.0), 1.0),
        }
        for floor, (expected_act, expected_progress) in cases.items():
            with self.subTest(floor=floor):
                features = self.encoder.transform_state(self._state(floor=floor))
                self.assertEqual(tuple(features[:3]), expected_act)
                self.assertEqual(features[3], expected_progress)

    def test_hp_preserves_relative_and_absolute_information(self):
        low_ratio = self.encoder.transform_state(self._state(hp=20, max_hp=80))
        high_ratio = self.encoder.transform_state(self._state(hp=20, max_hp=25))
        self.assertEqual(low_ratio[5], high_ratio[5])
        self.assertNotEqual(low_ratio[4], high_ratio[4])
        self.assertNotEqual(low_ratio[6], high_ratio[6])

    def test_gold_is_log_scaled_and_bounded(self):
        zero = self.encoder.transform_state(self._state(gold=0))[7]
        normal = self.encoder.transform_state(self._state(gold=100))[7]
        extreme = self.encoder.transform_state(self._state(gold=10**12))[7]
        self.assertEqual(zero, 0.0)
        self.assertGreater(normal, zero)
        self.assertLess(normal, 1.0)
        self.assertEqual(extreme, 1.0)

    def test_ascension_maps_a15_to_zero_and_a20_to_one(self):
        self.assertEqual(
            self.encoder.transform_state(self._state(ascension=15))[8], 0.0
        )
        self.assertEqual(
            self.encoder.transform_state(self._state(ascension=20))[8], 1.0
        )

    def test_invalid_values_are_rejected(self):
        for overrides in (
            {"hp": -1},
            {"max_hp": 0},
            {"gold": -1},
            {"floor": float("inf")},
        ):
            with self.subTest(overrides=overrides):
                with self.assertRaises(ValueError):
                    self.encoder.transform_state(self._state(**overrides))

    def test_training_artifacts_fit_caps_from_valid_train_rows_only(self):
        rows = [
            ("train", True, 10.0),
            ("train", True, 20.0),
            ("train", False, 999999.0),
            ("val", True, 999999.0),
            ("test", True, 999999.0),
        ]
        frame = pd.DataFrame(
            [
                {
                    "run_id": f"run-{index}",
                    "split": split,
                    "target_valid": target_valid,
                    "preprocessing_version": PREPROCESSING_VERSION,
                    "floor": 10,
                    "hp": 50.0,
                    "max_hp": 80.0,
                    "gold": gold,
                    "ascension": 20,
                    "deck": "Bash",
                    "relics": "Burning Blood",
                }
                for index, (split, target_valid, gold) in enumerate(rows)
            ]
        )
        with tempfile.TemporaryDirectory() as directory:
            frame.to_parquet(os.path.join(directory, "data.parquet"))
            _, encoder = build_training_artifacts(directory)
        self.assertAlmostEqual(encoder.caps["gold_q995"], 19.95)


class ModelTests(unittest.TestCase):
    def _model(self, global_conditioning="token", norm_position="pre"):
        model = STSValueNetwork(
            vocab_size=12,
            max_upgrade=5,
            max_count=6,
            d_model=16,
            n_heads=4,
            n_layers=2,
            num_global_features=9,
            dropout=0.0,
            global_conditioning=global_conditioning,
            norm_position=norm_position,
        )
        return model.eval()

    def test_permutation_and_extra_padding_do_not_change_value(self):
        model = self._model()
        globals_ = torch.arange(9, dtype=torch.float32).unsqueeze(0)
        with torch.no_grad():
            first = model(
                torch.tensor([[2, 3, 0]]),
                torch.tensor([[0, 1, 0]]),
                torch.tensor([[2, 1, 0]]),
                globals_,
            )
            permuted = model(
                torch.tensor([[3, 2, 0, 0]]),
                torch.tensor([[1, 0, 0, 0]]),
                torch.tensor([[1, 2, 0, 0]]),
                globals_,
            )
        torch.testing.assert_close(first, permuted, rtol=1e-5, atol=1e-6)

    def test_all_ablation_combinations_run(self):
        inputs = (
            torch.tensor([[2, 0]]),
            torch.tensor([[0, 0]]),
            torch.tensor([[1, 0]]),
            torch.zeros((1, 9)),
        )
        for conditioning in ("token", "late_concat"):
            for norm in ("pre", "post"):
                self.assertEqual(self._model(conditioning, norm)(*inputs).shape, (1, 1))

    def test_v3_checkpoint_round_trip(self):
        model = self._model()
        config = {
            "vocab_size": 12,
            "max_upgrade": 5,
            "max_count": 6,
            "d_model": 16,
            "n_heads": 4,
            "n_layers": 2,
            "num_global_features": 9,
            "dropout": 0.0,
            "global_conditioning": "token",
            "norm_position": "pre",
        }
        vocabulary = ItemVocabulary()
        vocabulary.add("Bash")
        vocabulary.freeze()
        feature_encoder = GlobalFeatureEncoder(
            hp_q995=100.0,
            max_hp_q995=200.0,
            gold_q995=1000.0,
        )
        with tempfile.TemporaryDirectory() as directory:
            path = os.path.join(directory, "model.pth")
            save_checkpoint(path, model, config, vocabulary, feature_encoder)
            checkpoint, loaded_vocab, loaded_encoder = load_checkpoint(path)
        self.assertEqual(checkpoint["format_version"], 3)
        self.assertEqual(loaded_vocab.to_dict(), vocabulary.to_dict())
        self.assertEqual(loaded_encoder.to_dict(), feature_encoder.to_dict())

    def test_v2_checkpoint_is_rejected(self):
        with tempfile.TemporaryDirectory() as directory:
            path = os.path.join(directory, "model.pth")
            torch.save({"format_version": 2}, path)
            with self.assertRaisesRegex(ValueError, "v1/v2 checkpoints"):
                load_checkpoint(path)

    def test_incompatible_feature_order_is_rejected(self):
        encoded = self.encoder_dict()
        encoded["feature_names"] = list(reversed(encoded["feature_names"]))
        with self.assertRaisesRegex(ValueError, "feature order"):
            GlobalFeatureEncoder.from_dict(encoded)

    def test_incompatible_feature_schema_is_rejected(self):
        encoded = self.encoder_dict()
        encoded["schema_version"] = "global-features-v2"
        with self.assertRaisesRegex(ValueError, "feature schema"):
            GlobalFeatureEncoder.from_dict(encoded)

    @staticmethod
    def encoder_dict():
        return GlobalFeatureEncoder(
            hp_q995=100.0,
            max_hp_q995=200.0,
            gold_q995=1000.0,
        ).to_dict()


if __name__ == "__main__":
    unittest.main()
