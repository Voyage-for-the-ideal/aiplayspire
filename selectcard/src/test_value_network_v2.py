import os
import tempfile
import unittest
from unittest import mock

import pandas as pd
import torch
from torch.utils.data import DataLoader

from checkpointing import load_checkpoint, save_checkpoint
from data_contract import (
    FILTER_VERSION,
    HAZARD_ENDPOINTS,
    HAZARD_OUTPUT_DIM,
    MASK_COLUMNS,
    TARGET_COLUMNS,
)
from data_pipeline import assign_split, hazard_targets, is_heart_victory
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
from inference import STSInferenceEngine, compose_hazard_value
from train import evaluate, hazard_predictions, save_training_report, weighted_hazard_loss
from boss_context import NUM_BOSS_IDS


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

    def test_terminal_death_marks_only_first_unreached_bucket(self):
        targets, masks = hazard_targets(20, 25, True, False)
        endpoint_index = HAZARD_ENDPOINTS.index(26)
        self.assertEqual(targets[endpoint_index], 1)
        self.assertTrue(masks[endpoint_index])
        self.assertFalse(any(masks[endpoint_index + 1:HAZARD_OUTPUT_DIM]))
        self.assertEqual(targets[-1], 0)
        self.assertTrue(masks[-1])

    def test_every_bucket_boundary_has_one_stopping_event(self):
        previous = 0
        for endpoint in HAZARD_ENDPOINTS:
            with self.subTest(endpoint=endpoint):
                floor = previous
                targets, masks = hazard_targets(floor, endpoint - 1, True, False)
                index = HAZARD_ENDPOINTS.index(endpoint)
                self.assertEqual(targets[index], 1)
                self.assertTrue(masks[index])
                self.assertFalse(any(masks[index + 1:HAZARD_OUTPUT_DIM]))
            previous = endpoint

    def test_completed_buckets_are_not_in_the_risk_set(self):
        targets, masks = hazard_targets(17, 40, True, False)
        completed = HAZARD_ENDPOINTS.index(17)
        self.assertFalse(any(masks[:completed + 1]))
        self.assertFalse(any(targets[:completed + 1]))

    def test_normal_victory_stops_before_act4(self):
        targets, masks = hazard_targets(49, 51, True, False)
        self.assertEqual(targets[HAZARD_ENDPOINTS.index(54)], 1)
        self.assertTrue(masks[HAZARD_ENDPOINTS.index(54)])
        self.assertFalse(masks[HAZARD_ENDPOINTS.index(57)])

    def test_heart_death_at_56_stops_before_57(self):
        targets, masks = hazard_targets(54, 56, True, False)
        self.assertEqual(targets[HAZARD_ENDPOINTS.index(57)], 1)
        self.assertTrue(masks[HAZARD_ENDPOINTS.index(57)])
        self.assertEqual(targets[-1], 0)

    def test_abandoned_run_masks_unknown_future(self):
        targets, masks = hazard_targets(20, 25, False, False)
        self.assertEqual(targets[HAZARD_ENDPOINTS.index(23)], 0)
        self.assertTrue(masks[HAZARD_ENDPOINTS.index(23)])
        self.assertFalse(masks[HAZARD_ENDPOINTS.index(26)])
        self.assertFalse(masks[-1])

    def test_heart_win_normalizes_progress_to_final_endpoint(self):
        targets, masks = hazard_targets(49, 56, True, True)
        self.assertEqual(targets[:HAZARD_OUTPUT_DIM], [0] * HAZARD_OUTPUT_DIM)
        self.assertTrue(all(masks[HAZARD_ENDPOINTS.index(51):]))
        self.assertEqual(targets[-1], 1)

    def test_heart_win_requires_terminal_heart_combat(self):
        base = {"victory": True, "damage_taken": [{"enemies": "Time Eater"}]}
        self.assertFalse(is_heart_victory(base))
        base["damage_taken"].append({"enemies": "The Heart"})
        self.assertTrue(is_heart_victory(base))


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
                    "visible_boss": "Time Eater",
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
                    "visible_boss": "UNKNOWN_BOSS",
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
                    "visible_boss": "UNKNOWN_BOSS",
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

    def test_missing_visible_boss_column_is_rejected(self):
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
            with self.assertRaisesRegex(ValueError, "visible_boss"):
                build_training_artifacts(directory)


class ModelTests(unittest.TestCase):
    def test_training_report_is_written(self):
        history = [
            {"epoch": 1, "train_loss": 0.4, "val_loss": 0.5, "learning_rate": 2e-4},
            {"epoch": 2, "train_loss": 0.3, "val_loss": 0.35, "learning_rate": 1e-4},
        ]
        with tempfile.TemporaryDirectory() as directory:
            path = os.path.join(directory, "training_report.png")
            save_training_report(history, path)
            self.assertTrue(os.path.isfile(path))
            self.assertGreater(os.path.getsize(path), 0)

    def _model(self, global_conditioning="token", norm_position="pre"):
        model = STSValueNetwork(
            vocab_size=12,
            max_upgrade=5,
            max_count=6,
            d_model=16,
            n_heads=4,
            n_layers=2,
            num_global_features=9,
            num_bosses=NUM_BOSS_IDS,
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
                torch.tensor([8]),
            )
            permuted = model(
                torch.tensor([[3, 2, 0, 0]]),
                torch.tensor([[1, 0, 0, 0]]),
                torch.tensor([[1, 2, 0, 0]]),
                globals_,
                torch.tensor([8]),
            )
        torch.testing.assert_close(first, permuted, rtol=1e-5, atol=1e-6)

    def test_all_ablation_combinations_run(self):
        inputs = (
            torch.tensor([[2, 0]]),
            torch.tensor([[0, 0]]),
            torch.tensor([[1, 0]]),
            torch.zeros((1, 9)),
            torch.tensor([0]),
        )
        for conditioning in ("token", "late_concat"):
            for norm in ("pre", "post"):
                self.assertEqual(self._model(conditioning, norm)(*inputs).shape, (1, 21))

    def test_masked_loss_ignores_invalid_hazard_gradient(self):
        logits = torch.zeros((1, 21), requires_grad=True)
        targets = torch.zeros((1, 21))
        masks = torch.zeros((1, 21))
        targets[0, 0] = 1.0
        masks[0, 0] = 1.0
        globals_ = torch.zeros((1, 9))
        loss = weighted_hazard_loss(
            logits, targets, masks, torch.tensor([1.0]), globals_, torch.ones(6)
        )
        loss.backward()
        self.assertNotEqual(logits.grad[0, 0].item(), 0.0)
        torch.testing.assert_close(logits.grad[0, 1:], torch.zeros(20))

    def test_boss_ids_follow_distinct_embedding_paths(self):
        model = self._model()
        self.assertFalse(torch.equal(model.boss_emb.weight[8], model.boss_emb.weight[9]))
        inputs = (
            torch.tensor([[2, 0]]), torch.tensor([[0, 0]]),
            torch.tensor([[1, 0]]), torch.zeros((1, 9)),
        )
        self.assertEqual(model(*inputs, torch.tensor([0])).shape, (1, 21))

    def test_hazard_survival_is_monotone_and_heart_is_bounded(self):
        logits = torch.linspace(-2.0, 2.0, 21).unsqueeze(0)
        _, survival, heart = hazard_predictions(logits, torch.tensor([10.0]))
        self.assertTrue(torch.all(survival[:, 1:] <= survival[:, :-1]))
        self.assertLessEqual(heart.item(), survival[0, -1].item())

    def test_evaluation_reports_hazards_heart_floor_and_baseline(self):
        batch_size = 2
        targets = torch.zeros((batch_size, 21))
        targets[0, 0] = 1.0
        targets[1, -1] = 1.0
        masks = torch.ones((batch_size, 21))
        batch = (
            torch.tensor([[2, 0], [2, 0]]),
            torch.zeros((batch_size, 2), dtype=torch.long),
            torch.tensor([[1, 0], [1, 0]]),
            torch.zeros((batch_size, 9)),
            torch.zeros(batch_size, dtype=torch.long),
            torch.ones(batch_size),
            targets,
            masks,
        )
        metrics = evaluate(
            self._model(), [batch], torch.device("cpu"), baseline_rates=[0.1] * 21
        )
        self.assertEqual(len(metrics["hazards"]), HAZARD_OUTPUT_DIM)
        self.assertIn("heart_win", metrics)
        self.assertTrue(torch.isfinite(torch.tensor(metrics["terminal_floor_mae"])))
        self.assertIn("constant_baseline", metrics)

    def test_scalar_value_rewards_act4_progress_and_heart_win(self):
        normal_hazards = {endpoint: 0.0 for endpoint in HAZARD_ENDPOINTS}
        normal_hazards[54] = 1.0
        _, _, normal_floor, normal_value = compose_hazard_value(
            normal_hazards, 0.0, 0, heart_bonus_floors=3
        )
        deep_hazards = dict(normal_hazards)
        deep_hazards[54] = 0.0
        deep_hazards[57] = 1.0
        _, _, deep_floor, deep_value = compose_hazard_value(
            deep_hazards, 0.0, 0, heart_bonus_floors=3
        )
        self.assertEqual(normal_floor, 51.0)
        self.assertEqual(deep_floor, 54.0)
        self.assertGreater(deep_value, normal_value)
        perfect = {endpoint: 0.0 for endpoint in HAZARD_ENDPOINTS}
        self.assertAlmostEqual(compose_hazard_value(perfect, 1.0, 0, 3)[-1], 1.0)
        self.assertNotEqual(
            compose_hazard_value(normal_hazards, 0.5, 0, 3)[-1],
            compose_hazard_value(normal_hazards, 0.5, 0, 6)[-1],
        )

    def test_boss_relic_hypothetical_replaces_starter_relic(self):
        engine = STSInferenceEngine.__new__(STSInferenceEngine)
        state = {"deck": ["Strike_R"], "relics": ["Burning Blood"]}
        result = engine._apply_choice(state, {
            "action": "composite_event",
            "effects": [{"type": "obtain_relic", "relic_id": "Black Blood"}],
        })
        self.assertEqual(result["relics"], ["Black Blood"])

    def test_choice_simulation_preserves_visible_boss(self):
        engine = STSInferenceEngine.__new__(STSInferenceEngine)
        state = {"deck": ["Strike_R"], "relics": [], "visible_boss": "Time Eater"}
        result = engine._apply_choice(state, {"action": "skip"})
        self.assertEqual(result["visible_boss"], "Time Eater")

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

    def test_v7_checkpoint_round_trip(self):
        model = self._model()
        config = {
            "vocab_size": 12,
            "max_upgrade": 5,
            "max_count": 6,
            "d_model": 16,
            "n_heads": 4,
            "n_layers": 2,
            "num_global_features": 9,
            "num_bosses": NUM_BOSS_IDS,
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
        self.assertEqual(checkpoint["format_version"], 7)
        self.assertEqual(loaded_vocab.to_dict(), vocabulary.to_dict())
        self.assertEqual(loaded_encoder.to_dict(), feature_encoder.to_dict())

    def test_wrong_checkpoint_format_is_rejected(self):
        with tempfile.TemporaryDirectory() as directory:
            path = os.path.join(directory, "model.pth")
            torch.save({"format_version": 3}, path)
            with self.assertRaisesRegex(ValueError, "bucket hazard model"):
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
