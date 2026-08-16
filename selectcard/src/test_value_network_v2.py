import os
import tempfile
import unittest
from unittest import mock

import pandas as pd
import torch
from torch.utils.data import DataLoader

from checkpointing import load_checkpoint, save_checkpoint
from data_contract import FILTER_VERSION, MASK_COLUMNS, TARGET_COLUMNS
from data_pipeline import assign_split, horizon_targets
from dataset import (
    TRAINING_ARTIFACT_CACHE_NAME,
    GlobalFeatureEncoder,
    STSDataset,
    build_training_artifacts,
)
from encoding import (
    PREPROCESSING_VERSION,
    ItemVocabulary,
    encode_items,
    parse_item_name,
)
from model import STSValueNetwork
from inference import STSInferenceEngine, compose_scalar_value
from train import weighted_bce_loss


def supervision_fields(valid=True, label=0.0):
    return {
        **{column: label for column in TARGET_COLUMNS},
        **{column: valid for column in MASK_COLUMNS},
    }


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

    def test_act_target_is_continuous_across_boundary(self):
        before = horizon_targets(16, 40, False, False)
        after = horizon_targets(17, 40, False, False)
        self.assertEqual(before, after)
        self.assertEqual(before, ([1, 1, 0, 0], [True, True, False, False]))

    def test_terminal_death_labels_all_horizons(self):
        self.assertEqual(
            horizon_targets(20, 25, True, False),
            ([1, 0, 0, 0], [True, True, True, True]),
        )

    def test_abandoned_run_masks_unknown_future(self):
        self.assertEqual(
            horizon_targets(20, 25, False, False),
            ([1, 0, 0, 0], [True, False, False, False]),
        )


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

    def test_ascension_maps_a0_a10_and_a20(self):
        self.assertEqual(
            self.encoder.transform_state(self._state(ascension=0))[8], 0.0
        )
        self.assertEqual(
            self.encoder.transform_state(self._state(ascension=10))[8], 0.5
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
            {"ascension": 21},
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
                    **supervision_fields(target_valid),
                    "preprocessing_version": PREPROCESSING_VERSION,
                    "filter_version": FILTER_VERSION,
                    "ascension_band": 5,
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
            frame.to_parquet(
                os.path.join(directory, "train_valid_chunk_00000.parquet")
            )
            _, encoder = build_training_artifacts(directory)
        self.assertAlmostEqual(encoder.caps["gold_q995"], 19.95)

    def test_training_artifacts_are_loaded_from_cache(self):
        frame = pd.DataFrame(
            [
                {
                    "run_id": "run-1",
                    "split": "train",
                    **supervision_fields(),
                    "preprocessing_version": PREPROCESSING_VERSION,
                    "filter_version": FILTER_VERSION,
                    "ascension_band": 0,
                    "floor": 1,
                    "hp": 70.0,
                    "max_hp": 80.0,
                    "gold": 99.0,
                    "ascension": 0,
                    "deck": "Bash",
                    "relics": "Burning Blood",
                }
            ]
        )
        with tempfile.TemporaryDirectory() as directory:
            frame.to_parquet(
                os.path.join(directory, "train_valid_chunk_00000.parquet")
            )
            expected_vocabulary, expected_encoder = build_training_artifacts(directory)
            self.assertTrue(
                os.path.isfile(os.path.join(directory, TRAINING_ARTIFACT_CACHE_NAME))
            )
            with mock.patch(
                "dataset._read_frame",
                side_effect=AssertionError("parquet should not be read on a cache hit"),
            ):
                vocabulary, encoder = build_training_artifacts(directory)

        self.assertEqual(vocabulary.to_dict(), expected_vocabulary.to_dict())
        self.assertEqual(encoder.to_dict(), expected_encoder.to_dict())

    def test_dataset_loads_with_multiple_workers(self):
        frame = pd.DataFrame(
            [
                {
                    "run_id": f"run-{index}",
                    "split": "train",
                    **supervision_fields(label=float(index % 2)),
                    "preprocessing_version": PREPROCESSING_VERSION,
                    "filter_version": FILTER_VERSION,
                    "ascension_band": 0,
                    "floor": index + 1,
                    "hp": 70.0,
                    "max_hp": 80.0,
                    "gold": 99.0,
                    "ascension": 0,
                    "deck": "Bash",
                    "relics": "Burning Blood",
                }
                for index in range(4)
            ]
        )
        with tempfile.TemporaryDirectory() as directory:
            frame.to_parquet(
                os.path.join(directory, "train_valid_chunk_00000.parquet")
            )
            vocabulary, encoder = build_training_artifacts(directory)
            dataset = STSDataset(directory, vocabulary, encoder, split="train")
            loader = DataLoader(dataset, batch_size=2, num_workers=2)
            batches = list(loader)

        self.assertEqual(len(batches), 2)
        self.assertEqual(sum(batch[0].shape[0] for batch in batches), 4)


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
                self.assertEqual(self._model(conditioning, norm)(*inputs).shape, (1, 4))

    def test_masked_loss_ignores_invalid_horizon_gradient(self):
        logits = torch.zeros((1, 4), requires_grad=True)
        targets = torch.ones((1, 4))
        masks = torch.tensor([[1.0, 0.0, 0.0, 0.0]])
        globals_ = torch.zeros((1, 9))
        loss = weighted_bce_loss(logits, targets, masks, globals_, torch.ones(6))
        loss.backward()
        self.assertNotEqual(logits.grad[0, 0].item(), 0.0)
        torch.testing.assert_close(logits.grad[0, 1:], torch.zeros(3))

    def test_scalar_value_clamps_completed_horizon(self):
        components = {"reach17": 0.3, "reach34": 0.4, "reach50": 0.2, "win": 0.1}
        self.assertAlmostEqual(compose_scalar_value(components, 20), 0.275)

    def test_boss_relic_hypothetical_replaces_starter_relic(self):
        engine = STSInferenceEngine.__new__(STSInferenceEngine)
        state = {"deck": ["Strike_R"], "relics": ["Burning Blood"]}
        result = engine._apply_choice(state, {
            "action": "composite_event",
            "effects": [{"type": "obtain_relic", "relic_id": "Black Blood"}],
        })
        self.assertEqual(result["relics"], ["Black Blood"])

    def test_calling_bell_adds_known_curse_without_fake_random_relics(self):
        engine = STSInferenceEngine.__new__(STSInferenceEngine)
        state = {"deck": ["Strike_R"], "relics": [], "relic_states": []}
        result = engine._apply_choice(state, {
            "action": "composite_event",
            "effects": [{"type": "obtain_relic", "relic_id": "Calling Bell"}],
        })
        self.assertIn("CurseOfTheBell", result["deck"])
        self.assertEqual(result["relics"], ["Calling Bell"])

    def test_empty_cage_removes_two_model_ranked_cards(self):
        engine = STSInferenceEngine.__new__(STSInferenceEngine)
        engine.rank_cards_for_purpose = lambda state, purpose, n, exclude_ids=None: [
            next(card for card in ("Strike_R", "Defend_R") if card in state["deck"])
        ]
        state = {"deck": ["Strike_R", "Defend_R", "Bash"], "relics": []}
        result = engine._apply_choice(state, {
            "action": "composite_event",
            "effects": [{"type": "obtain_relic", "relic_id": "Empty Cage"}],
        })
        self.assertEqual(result["deck"], ["Bash"])

    def test_astrolabe_removes_three_transform_targets_without_random_cards(self):
        engine = STSInferenceEngine.__new__(STSInferenceEngine)
        engine.rank_cards_for_purpose = lambda state, purpose, n, exclude_ids=None: [
            "Strike_R", "Defend_R", "Bash"
        ]
        state = {"deck": ["Strike_R", "Defend_R", "Bash", "Inflame"], "relics": []}
        result = engine._apply_choice(state, {
            "action": "composite_event",
            "effects": [{"type": "obtain_relic", "relic_id": "Astrolabe"}],
        })
        self.assertEqual(result["deck"], ["Inflame"])

    def test_pandoras_box_removes_all_starter_strikes_and_defends(self):
        engine = STSInferenceEngine.__new__(STSInferenceEngine)
        state = {"deck": ["Strike_R", "Defend_R", "Bash", "Inflame"], "relics": []}
        result = engine._apply_choice(state, {
            "action": "composite_event",
            "effects": [{"type": "obtain_relic", "relic_id": "Pandora's Box"}],
        })
        self.assertEqual(result["deck"], ["Bash", "Inflame"])

    def test_v5_checkpoint_round_trip(self):
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
        self.assertEqual(checkpoint["format_version"], 5)
        self.assertEqual(loaded_vocab.to_dict(), vocabulary.to_dict())
        self.assertEqual(loaded_encoder.to_dict(), feature_encoder.to_dict())

    def test_wrong_checkpoint_format_is_rejected(self):
        with tempfile.TemporaryDirectory() as directory:
            path = os.path.join(directory, "model.pth")
            torch.save({"format_version": 3}, path)
            with self.assertRaisesRegex(ValueError, "legacy single-act value target"):
                load_checkpoint(path)

    def test_incompatible_feature_order_is_rejected(self):
        encoded = self.encoder_dict()
        encoded["feature_names"] = list(reversed(encoded["feature_names"]))
        with self.assertRaisesRegex(ValueError, "feature order"):
            GlobalFeatureEncoder.from_dict(encoded)

    def test_incompatible_feature_schema_is_rejected(self):
        encoded = self.encoder_dict()
        encoded["schema_version"] = "global-features-v3"
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
