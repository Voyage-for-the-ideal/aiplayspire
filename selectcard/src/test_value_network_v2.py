import os
import tempfile
import unittest
from unittest import mock

import numpy as np
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
from train import (
    ascension_band_tensor,
    evaluate,
    hazard_predictions,
    save_training_report,
    training_objective_metadata,
    weighted_hazard_loss,
)
from boss_context import NUM_BOSS_IDS
from character_context import NUM_CHARACTER_IDS
from audit_value_deltas import audit_states


def supervision_fields(valid=True, label=0.0):
    return {
        **{column: label for column in TARGET_COLUMNS},
        **{column: valid for column in MASK_COLUMNS},
        "terminal_known": bool(valid), "terminal_floor": 57., "heart_victory": False,
        "key_states": [0, 0, 0],
        "key_known_mask": [1, 1, 0],
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

    def test_feature_schema_has_eleven_features_with_absolute_floor(self):
        self.assertEqual(len(self.encoder.feature_names), 11)
        self.assertNotIn("floor_progress", self.encoder.feature_names)
        self.assertEqual(len(self.encoder.transform_state(self._state())), 11)

    def test_act_boundaries_and_progress(self):
        for floor in (0,16,17,33,34,50,51,54,57):
            stage = min(sum(floor >= e for e in (17,34,51)), 3)
            features = self.encoder.transform_state(self._state(floor=floor))
            self.assertEqual(features[:4], [float(i == stage) for i in range(4)])
            self.assertEqual(features[5], floor/57)
            self.assertGreaterEqual(features[4], 0)
            self.assertLessEqual(features[4], 1)

    def test_hp_preserves_relative_and_absolute_information(self):
        low_ratio = self.encoder.transform_state(self._state(hp=20, max_hp=80))
        high_ratio = self.encoder.transform_state(self._state(hp=20, max_hp=25))
        self.assertEqual(low_ratio[7], high_ratio[7])
        self.assertNotEqual(low_ratio[6], high_ratio[6])
        self.assertNotEqual(low_ratio[8], high_ratio[8])

    def test_gold_is_log_scaled_and_bounded(self):
        zero = self.encoder.transform_state(self._state(gold=0))[9]
        normal = self.encoder.transform_state(self._state(gold=100))[9]
        extreme = self.encoder.transform_state(self._state(gold=10**12))[9]
        self.assertEqual(zero, 0.0)
        self.assertGreater(normal, zero)
        self.assertLess(normal, 1.0)
        self.assertEqual(extreme, 1.0)

    def test_ascension_maps_a0_a10_and_a20(self):
        self.assertEqual(
            self.encoder.transform_state(self._state(ascension=0))[10], 0.0
        )
        self.assertEqual(
            self.encoder.transform_state(self._state(ascension=10))[10], 0.5
        )
        self.assertEqual(
            self.encoder.transform_state(self._state(ascension=20))[10], 1.0
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
                    "character": "IRONCLAD",
                    "state_timing": "post_action",
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
                    "character": "IRONCLAD",
                    "state_timing": "post_action",
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
                    "character": "IRONCLAD",
                    "state_timing": "post_action",
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
                    "character": "IRONCLAD",
                    "state_timing": "post_action",
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
    def test_safe_addition_audit_reports_signed_deltas(self):
        class AuditEngine:
            def evaluate_state(self, state):
                return len(state.get("relics", [])) + sum(
                    bool(value) for value in (state.get("keys") or {}).values()
                )

        report = audit_states(AuditEngine(), [{"relics": [], "keys": {}}])
        self.assertEqual(report["Molten Egg 2"]["mean"], 1.0)
        self.assertEqual(report["sapphire"]["positive"], 1)

    def test_training_report_is_written(self):
        old_history = [
            {"epoch": 1, "train_loss": 0.4, "val_loss": 0.5, "learning_rate": 2e-4},
            {"epoch": 2, "train_loss": 0.3, "val_loss": 0.35, "learning_rate": 1e-4},
        ]
        new_history = [
            {
                "epoch": 1,
                "train_total": 1.1,
                "train_value": 1.0,
                "train_hazard_relative_nll": 0.9,
                "train_heart_relative_nll": 1.1,
                "train_key_bce": 0.5,
                "val_relative_value_nll": 0.95,
                "contextual_baseline_relative_nll": 1.0,
                "val_hazard_relative_nll": 0.9,
                "val_heart_relative_nll": 1.0,
                "val_key_bce": 0.45,
                "terminal_floor_mae": 4.0,
                "contextual_baseline_terminal_floor_mae": 4.5,
                "checkpoint_eligible": True,
                "learning_rate": 2e-4,
            }
        ]
        with tempfile.TemporaryDirectory() as directory:
            for index, history in enumerate((old_history, new_history)):
                path = os.path.join(directory, f"training_report_{index}.png")
                save_training_report(history, path)
                self.assertTrue(os.path.isfile(path))
                self.assertGreater(os.path.getsize(path), 0)

    def test_normalized_ascension_feature_recovers_band_boundaries(self):
        levels = torch.tensor([0, 1, 5, 6, 10, 11, 15, 16, 19, 20])
        globals_ = torch.zeros((len(levels), 11))
        globals_[:, 10] = levels / 20.0
        self.assertEqual(
            ascension_band_tensor(globals_).tolist(),
            [0, 1, 1, 2, 2, 3, 3, 4, 4, 5],
        )

    def _model(self, global_conditioning="token", norm_position="pre"):
        model = STSValueNetwork(
            vocab_size=12,
            max_upgrade=5,
            max_count=6,
            d_model=16,
            n_heads=4,
            n_layers=2,
            num_global_features=11,
            num_characters=NUM_CHARACTER_IDS,
            num_bosses=NUM_BOSS_IDS,
            dropout=0.0,
            global_conditioning=global_conditioning,
            norm_position=norm_position,
        )
        return model.eval()

    def test_permutation_and_extra_padding_do_not_change_value(self):
        model = self._model()
        globals_ = torch.arange(11, dtype=torch.float32).unsqueeze(0)
        with torch.no_grad():
            first = model(
                torch.tensor([[2, 3, 0]]),
                torch.tensor([[0, 1, 0]]),
                torch.tensor([[2, 1, 0]]),
                globals_,
                torch.tensor([0]),
                torch.tensor([8]),
            )
            permuted = model(
                torch.tensor([[3, 2, 0, 0]]),
                torch.tensor([[1, 0, 0, 0]]),
                torch.tensor([[1, 2, 0, 0]]),
                globals_,
                torch.tensor([0]),
                torch.tensor([8]),
            )
        torch.testing.assert_close(first, permuted, rtol=1e-5, atol=1e-6)

    def test_all_ablation_combinations_run(self):
        inputs = (
            torch.tensor([[2, 0]]),
            torch.tensor([[0, 0]]),
            torch.tensor([[1, 0]]),
            torch.zeros((1, 11)),
            torch.tensor([0]),
            torch.tensor([0]),
        )
        for conditioning in ("token", "late_concat"):
            for norm in ("pre", "post"):
                self.assertEqual(self._model(conditioning, norm)(*inputs).shape, (1, 47))

    def test_masked_loss_ignores_invalid_hazard_gradient(self):
        logits = torch.zeros((1, 47), requires_grad=True)
        targets = torch.zeros((1, 24))
        masks = torch.zeros((1, 24))
        targets[0, 0] = 1.0
        masks[0, 0] = 1.0
        globals_ = torch.zeros((1, 11))
        loss = weighted_hazard_loss(
            logits, targets, masks, torch.tensor([1.0]), globals_, torch.ones(6), [.5]*24, terminal_floors=torch.tensor([1.])
        )
        loss.backward()
        self.assertNotEqual(logits.grad[0, 0].item(), 0.0)
        torch.testing.assert_close(logits.grad[0, 1:], torch.zeros(46))

    def test_boss_ids_follow_distinct_embedding_paths(self):
        model = self._model()
        self.assertFalse(torch.equal(model.boss_emb.weight[8], model.boss_emb.weight[9]))
        inputs = (
            torch.tensor([[2, 0]]), torch.tensor([[0, 0]]),
            torch.tensor([[1, 0]]), torch.zeros((1, 11)),
        )
        self.assertEqual(model(*inputs, torch.tensor([0]), torch.tensor([0])).shape, (1, 47))

    def test_character_ids_follow_distinct_embedding_paths(self):
        model = self._model()
        self.assertFalse(
            torch.equal(model.character_emb.weight[0], model.character_emb.weight[1])
        )

    def test_hazard_survival_is_monotone_and_heart_is_bounded(self):
        logits = torch.linspace(-2.0, 2.0, 47).unsqueeze(0)
        from multiscale import distribution
        d = distribution(logits, torch.tensor([10.0]))
        alive = d["survival"][0, d["active"][0]][torch.argsort(d["ends"][d["active"][0]])]
        self.assertTrue(torch.all(alive[1:] <= alive[:-1]))
        self.assertLessEqual(float(d["heart_probability"][0]), float(d["final_survival"][0]))

    def test_evaluation_reports_hazards_heart_floor_and_baseline(self):
        batch_size = 2
        targets = torch.zeros((batch_size, 24))
        targets[0, 0] = 1.0
        targets[1, -1] = 1.0
        masks = torch.ones((batch_size, 24))
        batch = (
            torch.tensor([[2, 0], [2, 0]]),
            torch.zeros((batch_size, 2), dtype=torch.long),
            torch.tensor([[1, 0], [1, 0]]),
            torch.zeros((batch_size, 11)),
            torch.zeros(batch_size, dtype=torch.long),
            torch.zeros(batch_size, dtype=torch.long),
            torch.ones(batch_size),
            targets,
            masks,
        )
        metrics = evaluate(
            self._model(), [(*batch, torch.tensor([2., 57.]))], torch.device("cpu"), baseline_rates=[0.1] * 24
        )
        self.assertEqual(len(metrics["hazards"]), 23)
        self.assertIn("heart_win", metrics)
        self.assertTrue(torch.isfinite(torch.tensor(metrics["terminal_floor_mae"])))
        self.assertIn("constant_baseline", metrics)
        self.assertEqual(metrics["by_ascension"]["0"]["samples"], batch_size)

    def test_evaluation_loss_is_independent_of_batch_partition(self):
        batch_size = 4
        targets = torch.zeros((batch_size, 24))
        targets[0, 0] = 1.0
        targets[1, 1] = 1.0
        targets[2, -1] = 1.0
        masks = torch.ones((batch_size, 24))
        batch = (
            torch.tensor([[2, 0]] * batch_size),
            torch.zeros((batch_size, 2), dtype=torch.long),
            torch.tensor([[1, 0]] * batch_size),
            torch.zeros((batch_size, 11)),
            torch.zeros(batch_size, dtype=torch.long),
            torch.zeros(batch_size, dtype=torch.long),
            torch.ones(batch_size),
            targets,
            masks,
        )
        batch = (*batch, torch.tensor([2., 5., 57., 34.]))
        split_batches = [
            tuple(value[:1] for value in batch),
            tuple(value[1:] for value in batch),
        ]
        model = self._model()
        together = evaluate(
            model, [batch], torch.device("cpu"), baseline_rates=[0.25] * 21
        )
        partitioned = evaluate(
            model, split_batches, torch.device("cpu"), baseline_rates=[0.25] * 21
        )
        self.assertAlmostEqual(
            together["relative_value_nll"],
            partitioned["relative_value_nll"],
            delta=1e-6,
        )
        self.assertAlmostEqual(
            together["terminal_floor_mae"],
            partitioned["terminal_floor_mae"],
            delta=1e-6,
        )

        def assert_checkpoint_safe(value):
            self.assertNotIsInstance(value, np.generic)
            if isinstance(value, dict):
                for nested in value.values():
                    assert_checkpoint_safe(nested)
            elif isinstance(value, (list, tuple)):
                for nested in value:
                    assert_checkpoint_safe(nested)

        assert_checkpoint_safe(together)

    def test_scalar_value_rewards_act4_progress_and_heart_win(self):
        from data_contract import active_bins
        bins = active_bins(0)
        hazards = {end:0. for _,_,_,end in bins}
        hazards[57] = 1.
        means = {end:start for _,_,start,end in bins}
        normal = compose_hazard_value(hazards,0.,0,stop_means=means)
        self.assertEqual(normal[2],51.)
        means[57] = 54.
        deep = compose_hazard_value(hazards,0.,0,stop_means=means)
        self.assertEqual(deep[2],54.)
        self.assertGreater(deep[-1],normal[-1])
        hazards[57] = 0.
        perfect = compose_hazard_value(hazards,1.,0,stop_means=means)
        self.assertEqual(perfect[-1],1.)

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

    def test_choice_simulation_applies_each_egg_to_matching_new_cards(self):
        cases = (
            ("Molten Egg 2", "Anger", "Anger+1"),
            ("Toxic Egg 2", "Armaments", "Armaments+1"),
            ("Frozen Egg 2", "Inflame", "Inflame+1"),
        )
        engine = STSInferenceEngine.__new__(STSInferenceEngine)
        for egg, card, expected in cases:
            with self.subTest(egg=egg):
                state = {"deck": ["Bash"], "relics": [egg], "gold": 100}
                picked = engine._apply_choice(
                    state, {"action": "pick_card", "target": card}
                )
                bought = engine._apply_choice(
                    state, {"action": "buy_card", "target": card, "cost": 10}
                )
                self.assertEqual(picked["deck"], ["Bash", expected])
                self.assertEqual(bought["deck"], ["Bash", expected])

    def test_composite_add_card_applies_egg_without_double_upgrade(self):
        engine = STSInferenceEngine.__new__(STSInferenceEngine)
        state = {"deck": [], "relics": ["Molten Egg 2"]}
        added = engine._apply_choice(state, {
            "action": "composite_event",
            "effects": [{"type": "add_card", "card_id": "Anger", "amount": 2}],
        })
        upgraded = engine._apply_choice(state, {
            "action": "composite_event",
            "effects": [{"type": "add_card", "card_id": "Anger+1"}],
        })
        mismatched = engine._apply_choice(state, {
            "action": "composite_event",
            "effects": [{"type": "add_card", "card_id": "Armaments"}],
        })
        self.assertEqual(added["deck"], ["Anger+1", "Anger+1"])
        self.assertEqual(upgraded["deck"], ["Anger+1"])
        self.assertEqual(mismatched["deck"], ["Armaments"])

    def test_take_key_sets_requested_key_on_copied_state(self):
        engine = STSInferenceEngine.__new__(STSInferenceEngine)
        state = {
            "deck": [],
            "relics": [],
            "keys": {"ruby": False, "emerald": True, "sapphire": False},
        }
        result = engine._apply_choice(
            state, {"action": "take_key", "target": "sapphire"}
        )
        self.assertTrue(result["keys"]["sapphire"])
        self.assertTrue(result["keys"]["emerald"])
        self.assertFalse(state["keys"]["sapphire"])

    def test_singing_bowl_increases_current_and_max_hp(self):
        engine = STSInferenceEngine.__new__(STSInferenceEngine)
        state = {"deck": [], "relics": [], "hp": 50, "max_hp": 75}
        result = engine._apply_choice(state, {"action": "singing_bowl"})
        self.assertEqual((result["hp"], result["max_hp"]), (52, 77))

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

    def test_pandoras_box_is_suffix_tolerant(self):
        engine = STSInferenceEngine.__new__(STSInferenceEngine)
        state = {
            "deck": ["Strike_R", "Defend_R+1", "Strike_P+2", "Bash"],
            "relics": [],
        }
        result = engine._apply_choice(state, {
            "action": "composite_event",
            "effects": [{"type": "obtain_relic", "relic_id": "Pandora's Box"}],
        })
        self.assertEqual(result["deck"], ["Bash"])

    def test_upgrade_card_resolves_bare_target_to_suffixed_entry(self):
        engine = STSInferenceEngine.__new__(STSInferenceEngine)
        state = {"deck": ["Strike_R", "Strike_R+1"], "relics": []}
        result = engine._apply_choice(
            state, {"action": "upgrade_card", "target": "Strike_R"}
        )
        self.assertEqual(result["deck"], ["Strike_R+1", "Strike_R+1"])

    def test_upgrade_card_never_appends_phantom_target(self):
        engine = STSInferenceEngine.__new__(STSInferenceEngine)
        state = {"deck": ["Bash+1"], "relics": []}
        result = engine._apply_choice(
            state, {"action": "upgrade_card", "target": "Bash"}
        )
        self.assertEqual(result["deck"], ["Bash+1"])

    def test_upgrade_card_ignores_target_absent_from_deck(self):
        engine = STSInferenceEngine.__new__(STSInferenceEngine)
        state = {"deck": ["Bash"], "relics": []}
        result = engine._apply_choice(
            state, {"action": "upgrade_card", "target": "Necronomicurse"}
        )
        self.assertEqual(result["deck"], ["Bash"])

    def test_upgrade_card_advances_searing_blow_level(self):
        engine = STSInferenceEngine.__new__(STSInferenceEngine)
        state = {"deck": ["Searing Blow+2"], "relics": []}
        result = engine._apply_choice(
            state, {"action": "upgrade_card", "target": "Searing Blow+2"}
        )
        self.assertEqual(result["deck"], ["Searing Blow+3"])

    def test_upgrade_card_bare_target_does_not_match_upgraded_copy(self):
        # A bare reference means the level-0 copy specifically.  With only an
        # upgraded copy present there is no unambiguous target, so the branch
        # no-ops rather than guessing which copy was meant.
        engine = STSInferenceEngine.__new__(STSInferenceEngine)
        state = {"deck": ["Searing Blow+2"], "relics": []}
        result = engine._apply_choice(
            state, {"action": "upgrade_card", "target": "Searing Blow"}
        )
        self.assertEqual(result["deck"], ["Searing Blow+2"])

    def test_upgrade_card_handles_display_form_target(self):
        engine = STSInferenceEngine.__new__(STSInferenceEngine)
        state = {"deck": ["Bash+1"], "relics": []}
        result = engine._apply_choice(
            state, {"action": "upgrade_card", "target": "Bash+"}
        )
        self.assertEqual(result["deck"], ["Bash+1"])

    def test_composite_upgrade_does_not_corrupt_multi_level_target(self):
        engine = STSInferenceEngine.__new__(STSInferenceEngine)
        state = {"deck": ["Searing Blow+2"], "relics": []}
        result = engine._apply_choice(state, {
            "action": "composite_event",
            "effects": [{"type": "upgrade_card", "card_id": "Searing Blow+2"}],
        })
        self.assertEqual(result["deck"], ["Searing Blow+3"])

    def test_composite_upgrade_drops_target_absent_from_deck(self):
        engine = STSInferenceEngine.__new__(STSInferenceEngine)
        state = {"deck": ["Bash"], "relics": []}
        result = engine._apply_choice(state, {
            "action": "composite_event",
            "effects": [{"type": "upgrade_card", "card_id": "Unknown"}],
        })
        self.assertEqual(result["deck"], ["Bash"])

    def test_pick_card_canonicalizes_display_form_target(self):
        engine = STSInferenceEngine.__new__(STSInferenceEngine)
        state = {"deck": ["Bash"], "relics": []}
        result = engine._apply_choice(
            state, {"action": "pick_card", "target": "Bash+"}
        )
        self.assertEqual(result["deck"], ["Bash", "Bash+1"])

    def test_replace_starter_strikes_removes_upgraded_strikes(self):
        engine = STSInferenceEngine.__new__(STSInferenceEngine)
        state = {
            "deck": ["Strike_R", "Strike_R+1", "Defend_R", "Bash"],
            "relics": [],
        }
        result = engine._apply_choice(state, {
            "action": "composite_event",
            "effects": [{"type": "replace_starter_strikes", "amount": 5}],
        })
        self.assertEqual(result["deck"], ["Defend_R", "Bash"] + ["Bite"] * 5)

    def test_upgrade_matching_leaves_upgraded_starters_alone(self):
        engine = STSInferenceEngine.__new__(STSInferenceEngine)
        state = {"deck": ["Strike_R", "Defend_R+1", "Bash"], "relics": []}
        result = engine._apply_choice(state, {
            "action": "composite_event",
            "effects": [{"type": "upgrade_matching"}],
        })
        self.assertEqual(result["deck"], ["Strike_R+1", "Defend_R+1", "Bash"])

    def test_bare_remove_ladder_falls_through_to_upgraded_copy(self):
        engine = STSInferenceEngine.__new__(STSInferenceEngine)
        state = {"deck": ["Strike_R+1", "Strike_R"], "relics": []}
        result = engine._apply_choice(state, {
            "action": "composite_event",
            "effects": [{"type": "remove_card", "amount": 2}],
        })
        self.assertEqual(result["deck"], [])

    def test_rank_cards_for_purpose_respects_suffixed_exclusions(self):
        engine = STSInferenceEngine.__new__(STSInferenceEngine)
        engine.evaluate_state = lambda state: 1.0
        state = {"deck": ["Defend_R", "Strike_R+2", "Bash"], "relics": []}
        ranked = engine.rank_cards_for_purpose(
            state, "purge", 1, exclude_ids={"Strike_R+2"}
        )
        self.assertEqual(ranked, ["Defend_R"])

    def test_rank_cards_for_purpose_skips_upgraded_cards_for_upgrade(self):
        engine = STSInferenceEngine.__new__(STSInferenceEngine)
        engine.evaluate_state = lambda state: 1.0
        state = {"deck": ["Strike_R+1", "Bash"], "relics": []}
        ranked = engine.rank_cards_for_purpose(state, "upgrade", 2)
        self.assertNotIn("Strike_R+1", ranked)
        self.assertEqual(ranked, ["Bash"])


    def test_v9_checkpoint_round_trip(self):
        model = self._model()
        config = {
            "vocab_size": 12,
            "max_upgrade": 5,
            "max_count": 6,
            "d_model": 16,
            "n_heads": 4,
            "n_layers": 2,
            "num_global_features": 11,
            "num_characters": NUM_CHARACTER_IDS,
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
        self.assertEqual(checkpoint["format_version"], 12)
        self.assertEqual(loaded_vocab.to_dict(), vocabulary.to_dict())
        self.assertEqual(loaded_encoder.to_dict(), feature_encoder.to_dict())

    def test_checkpoint_round_trip_preserves_training_objective_metadata(self):
        model = self._model()
        config = {
            "vocab_size": 12,
            "max_upgrade": 5,
            "max_count": 6,
            "d_model": 16,
            "n_heads": 4,
            "n_layers": 2,
            "num_global_features": 11,
            "num_characters": NUM_CHARACTER_IDS,
            "num_bosses": NUM_BOSS_IDS,
            "dropout": 0.0,
            "global_conditioning": "token",
            "norm_position": "pre",
        }
        vocabulary = ItemVocabulary().freeze()
        feature_encoder = GlobalFeatureEncoder()
        rates = [0.25] * (HAZARD_OUTPUT_DIM + 1)
        statistics = {
            "schema_version": "floor-ascension-band-v1",
            "prior_strength": 100.0,
        }
        objective = training_objective_metadata(
            rates, statistics, selection={"score": 0.9, "mae_guard_passed": True}
        )
        with tempfile.TemporaryDirectory() as directory:
            path = os.path.join(directory, "model.pth")
            save_checkpoint(
                path,
                model,
                config,
                vocabulary,
                feature_encoder,
                {"training_objective": objective},
            )
            checkpoint, _, _ = load_checkpoint(path)
        self.assertEqual(checkpoint["format_version"], 12)
        self.assertEqual(checkpoint["metadata"]["training_objective"], objective)

    def test_wrong_checkpoint_format_is_rejected(self):
        with tempfile.TemporaryDirectory() as directory:
            path = os.path.join(directory, "model.pth")
            torch.save({"format_version": 8}, path)
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
