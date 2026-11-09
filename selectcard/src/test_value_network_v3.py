"""Tests for the v3 map-conditioned model, dataset, training and inference."""

import os
import tempfile
import unittest

import pandas as pd
import torch

from checkpointing import load_checkpoint, save_checkpoint
from config import Config
from data_contract import (
    FILTER_VERSION,
    MASK_COLUMNS,
    PREPROCESSING_VERSION,
    TARGET_COLUMNS,
)
from dataset import STSDataset
from encoding import ItemVocabulary
from map_context import (
    MAP_GRID_CELLS, HISTORY_HORIZON, ROOM_SYMBOL_TO_ID,
)
from model import STSValueNetwork
from train import (
    _binary_entropy,
    _contextual_baseline_probabilities,
    _key_head_loss,
    _split_batch,
    checkpoint_eligibility,
    configure_context_curriculum,
    monotonic_counterfactual_loss,
    value_loss_components,
)
from boss_context import NUM_BOSS_IDS
from character_context import CHARACTER_VOCABULARY, NUM_CHARACTER_IDS, character_id


def _v2_model(**overrides):
    params = dict(
        vocab_size=32,
        d_model=32,
        n_heads=4,
        n_layers=1,
        num_global_features=11,
        num_characters=NUM_CHARACTER_IDS,
        num_bosses=NUM_BOSS_IDS,
        dropout=0.0,
    )
    params.update(overrides)
    return STSValueNetwork(**params)


def _v3_model(**overrides):
    return _v2_model(use_map_context=True, **overrides)


def _item_batch(batch=3, items=6):
    torch.manual_seed(0)
    seq = torch.randint(2, 32, (batch, items))
    upgrades = torch.zeros((batch, items), dtype=torch.long)
    counts = torch.ones((batch, items), dtype=torch.long)
    globals_ = torch.rand((batch, 11))
    character = torch.zeros((batch,), dtype=torch.long)
    boss = torch.zeros((batch,), dtype=torch.long)
    return seq, upgrades, counts, globals_, character, boss


def _context_batch(batch=3):
    torch.manual_seed(1)
    map_rooms = torch.zeros((batch, MAP_GRID_CELLS), dtype=torch.long)
    map_flags = torch.zeros((batch, MAP_GRID_CELLS), dtype=torch.long)
    map_rooms[:, 0] = ROOM_SYMBOL_TO_ID["M"]
    map_rooms[:, 7] = ROOM_SYMBOL_TO_ID["E"]
    map_flags[:, 0] = 1 | (2 << 4)
    map_flags[:, 7] = 1 | (1 << 2) | (1 << 6)
    history_rooms = torch.zeros((batch, HISTORY_HORIZON), dtype=torch.long)
    history_dist = torch.full((batch, HISTORY_HORIZON), HISTORY_HORIZON, dtype=torch.long)
    history_rooms[:, 0] = ROOM_SYMBOL_TO_ID["E"]
    history_dist[:, 0] = 0
    keys_features = torch.rand((batch, 9))
    return keys_features, map_rooms, map_flags, history_rooms, history_dist


class ModelTests(unittest.TestCase):
    def test_character_vocabulary_is_stable_and_strict(self):
        self.assertEqual(
            CHARACTER_VOCABULARY,
            {"IRONCLAD": 0, "THE_SILENT": 1, "DEFECT": 2, "WATCHER": 3},
        )
        self.assertEqual(character_id("WATCHER"), 3)
        for invalid in (None, "SILENT", "UNKNOWN"):
            with self.subTest(invalid=invalid), self.assertRaises(ValueError):
                character_id(invalid)

    def test_v2_forward_shape_unchanged(self):
        model = _v2_model()
        seq, upgrades, counts, globals_, character, boss = _item_batch()
        logits = model(seq, upgrades, counts, globals_, character, boss)
        self.assertEqual(logits.shape, (3, 47))

    def test_v3_forward_with_context_and_key_head(self):
        model = _v3_model()
        seq, upgrades, counts, globals_, character, boss = _item_batch()
        context = _context_batch()
        logits, key_logits = model(
            seq, upgrades, counts, globals_, character, boss,
            keys_features=context[0], map_rooms=context[1],
            map_flags=context[2], history_rooms=context[3],
            history_dist=context[4], return_keys=True,
        )
        self.assertEqual(logits.shape, (3, 47))
        self.assertEqual(key_logits.shape, (3, 3))

    def test_v3_forward_falls_back_to_missing_context(self):
        model = _v3_model()
        seq, upgrades, counts, globals_, character, boss = _item_batch()
        logits = model(seq, upgrades, counts, globals_, character, boss)
        self.assertEqual(logits.shape, (3, 47))

    def test_return_keys_rejected_without_context(self):
        model = _v2_model()
        seq, upgrades, counts, globals_, character, boss = _item_batch()
        with self.assertRaises(ValueError):
            model(seq, upgrades, counts, globals_, character, boss, return_keys=True)

    def test_v3_backward_pass_updates_context_parameters(self):
        model = _v3_model()
        seq, upgrades, counts, globals_, character, boss = _item_batch()
        context = _context_batch()
        logits, key_logits = model(
            seq, upgrades, counts, globals_, character, boss,
            keys_features=context[0], map_rooms=context[1],
            map_flags=context[2], history_rooms=context[3],
            history_dist=context[4], return_keys=True,
        )
        (logits.sum() + key_logits.sum()).backward()
        grads = [
            p.grad for p in (
                *self._params(model.map_room_emb),
                *self._params(model.map_flag_proj),
                *self._params(model.hist_dist_emb),
                *self._params(model.keys_mlp),
                *self._params(model.key_head),
            ) if p.grad is not None
        ]
        self.assertTrue(grads)

    @staticmethod
    def _params(module):
        return list(module.parameters())


class CheckpointCompatTests(unittest.TestCase):
    def _roundtrip(self, model, model_config):
        with tempfile.TemporaryDirectory() as tmp:
            path = os.path.join(tmp, "model.pth")
            save_checkpoint(
                path, model, model_config,
                ItemVocabulary().freeze(),
                __import__("dataset").GlobalFeatureEncoder(),
            )
            checkpoint, _, _ = load_checkpoint(path)
        restored = STSValueNetwork(**checkpoint["model_config"])
        restored.load_state_dict(checkpoint["model_state_dict"])
        return restored

    def test_v2_checkpoint_roundtrip(self):
        config = {
            "vocab_size": 32, "max_upgrade": 15, "max_count": 10,
            "d_model": 32, "n_heads": 4, "n_layers": 1,
            "num_global_features": 11, "num_characters": NUM_CHARACTER_IDS,
            "num_bosses": NUM_BOSS_IDS,
            "dropout": 0.0, "global_conditioning": "token", "norm_position": "pre",
        }
        restored = self._roundtrip(_v2_model(), config)
        self.assertFalse(restored.use_map_context)

    def test_v3_checkpoint_roundtrip(self):
        config = {
            "vocab_size": 32, "max_upgrade": 15, "max_count": 10,
            "d_model": 32, "n_heads": 4, "n_layers": 1,
            "num_global_features": 11, "num_characters": NUM_CHARACTER_IDS,
            "num_bosses": NUM_BOSS_IDS,
            "dropout": 0.0, "global_conditioning": "token", "norm_position": "pre",
            "use_map_context": True,
            "num_key_features": 9,
        }
        restored = self._roundtrip(_v3_model(), config)
        self.assertTrue(restored.use_map_context)


class DatasetContextTests(unittest.TestCase):
    def _write_parquet(self, tmp, rows):
        frame = pd.DataFrame(rows)
        os.makedirs(tmp, exist_ok=True)
        frame.to_parquet(os.path.join(tmp, "train_valid_chunk_00000.parquet"), index=False)
        return tmp

    def _rows(self):
        rows = []
        for index in range(3):
            row = {
                "run_id": f"run{index}",
                "split": "train",
                "floor": 5.0 + index,
                "hp": 50.0,
                "max_hp": 75.0,
                "gold": 120.0,
                "ascension": 10.0,
                "deck": "Strike_R,Bash",
                "relics": "Burning Blood",
                "ascension_band": 3,
                "character": "IRONCLAD",
                "state_timing": "post_action",
                "terminal_known": True, "terminal_floor": 57., "heart_victory": False,
                "visible_boss": "NO_BOSS",
                "preprocessing_version": PREPROCESSING_VERSION,
                "filter_version": FILTER_VERSION,
                **{column: 0.0 for column in TARGET_COLUMNS},
                **{column: True for column in MASK_COLUMNS},
                "act": 1,
                "map_rooms": [ROOM_SYMBOL_TO_ID["M"]] + [0] * (MAP_GRID_CELLS - 1),
                "map_flags": [1] * MAP_GRID_CELLS,
                "map_outgoing_mask": [0] * MAP_GRID_CELLS,
                "history_rooms": [ROOM_SYMBOL_TO_ID["M"], ROOM_SYMBOL_TO_ID["$"]]
                                 + [0] * (HISTORY_HORIZON - 2),
                "key_states": [0, 0, 0],
                "key_known_mask": [1, 1, 0],
            }
            rows.append(row)
        return rows

    def _dataset(self, tmp, **kwargs):
        return STSDataset(
            tmp, ItemVocabulary().freeze(),
            __import__("dataset").GlobalFeatureEncoder(),
            split="train", max_seq_len=8,
            use_map_context=True, **kwargs,
        )

    def test_context_tensors_are_returned(self):
        with tempfile.TemporaryDirectory() as tmp:
            dataset = self._dataset(self._write_parquet(tmp, self._rows()))
            sample = dataset[0]
            self.assertEqual(len(sample), 18)
            self.assertEqual(sample[4].item(), 0)  # IRONCLAD
            (keys_features, map_rooms, map_flags,
             history_rooms, history_dist, key_labels, key_masks, outgoing) = sample[9:-1]
            self.assertEqual(keys_features.shape, (9,))
            self.assertEqual(map_rooms.shape, (MAP_GRID_CELLS,))
            self.assertEqual(map_flags.shape, (MAP_GRID_CELLS,))
            self.assertEqual(history_rooms.shape, (HISTORY_HORIZON,))
            self.assertEqual(
                history_rooms.tolist(),
                [ROOM_SYMBOL_TO_ID["M"], ROOM_SYMBOL_TO_ID["$"]]
                + [0] * (HISTORY_HORIZON - 2),
            )
            self.assertEqual(
                history_dist.tolist(),
                [0, 1] + [HISTORY_HORIZON] * (HISTORY_HORIZON - 2),
            )
            self.assertEqual(key_labels.tolist(), [0.0, 0.0, 0.0])
            self.assertEqual(key_masks.tolist(), [1.0, 1.0, 0.0])

    def test_curriculum_masking_drops_map_and_history(self):
        with tempfile.TemporaryDirectory() as tmp:
            dataset = self._dataset(self._write_parquet(tmp, self._rows()))
            dataset.context_enabled = False
            sample = dataset[0]
            (_, map_rooms, map_flags, history_rooms, history_dist, _, _, _) = sample[9:-1]
            self.assertFalse(map_rooms.any())
            self.assertFalse(map_flags.any())
            self.assertFalse(history_rooms.any())
            self.assertTrue((history_dist == HISTORY_HORIZON).all())

    def test_missing_columns_degrade_to_missing_context(self):
        rows = self._rows()
        for row in rows:
            row["map_rooms"] = None
            row["map_flags"] = None
            row["key_states"] = None
            row["key_known_mask"] = None
        with tempfile.TemporaryDirectory() as tmp:
            dataset = self._dataset(self._write_parquet(tmp, rows))
            sample = dataset[0]
            (_, map_rooms, _, _, _, _, key_masks, _) = sample[9:-1]
            self.assertFalse(map_rooms.any())
            self.assertEqual(key_masks.tolist(), [0.0, 0.0, 0.0])


class TrainingHelperTests(unittest.TestCase):
    def test_monotonic_loss_penalizes_lower_observed_egg_value(self):
        class FloorValueModel(torch.nn.Module):
            def forward(self, seq, upgrades, counts, globals_, character, boss, **kwargs):
                # Removing token 7 raises the predicted value, which violates
                # the free-relic ordering and must produce a positive loss.
                present = seq.eq(7).any(dim=1).float()
                logits = torch.zeros((len(seq), 47), device=seq.device)
                logits[:, 0] = present * 2.0 - 1.0
                return logits

        model = FloorValueModel()
        seq = torch.tensor([[7, 2], [2, 3]])
        upgrades = torch.zeros_like(seq)
        counts = torch.ones_like(seq)
        globals_ = torch.zeros((2, 11))
        character = torch.zeros(2, dtype=torch.long)
        boss = torch.zeros(2, dtype=torch.long)
        floors = torch.ones(2)
        observed = model(seq, upgrades, counts, globals_, character, boss)
        loss = monotonic_counterfactual_loss(
            model, observed, seq, upgrades, counts, globals_, character, boss,
            floors, [7],
        )
        self.assertGreater(loss.item(), 0.0)

    def test_monotonic_loss_checks_every_observed_egg(self):
        class SecondEggValueModel(torch.nn.Module):
            def forward(self, seq, upgrades, counts, globals_, character, boss, **kwargs):
                # Token 7 is neutral. Removing token 8 improves the prediction,
                # so a first-Egg-only implementation incorrectly returns zero.
                second_egg_present = seq.eq(8).any(dim=1).float()
                logits = torch.zeros((len(seq), 47), device=seq.device)
                logits[:, 0] = second_egg_present * 2.0 - 1.0
                return logits

        model = SecondEggValueModel()
        seq = torch.tensor([[7, 8, 2]])
        upgrades = torch.zeros_like(seq)
        counts = torch.ones_like(seq)
        globals_ = torch.zeros((1, 11))
        character = torch.zeros(1, dtype=torch.long)
        boss = torch.zeros(1, dtype=torch.long)
        floors = torch.ones(1)
        observed = model(seq, upgrades, counts, globals_, character, boss)
        loss = monotonic_counterfactual_loss(
            model, observed, seq, upgrades, counts, globals_, character, boss,
            floors, [7, 8],
        )
        self.assertGreater(loss.item(), 0.0)

    def test_monotonic_loss_penalizes_lower_known_key_value(self):
        class KeyValueModel(torch.nn.Module):
            def forward(self, seq, upgrades, counts, globals_, character, boss,
                        keys_features=None, **kwargs):
                present = keys_features[:, 0]
                logits = torch.zeros((len(seq), 47), device=seq.device)
                logits[:, 0] = present * 2.0 - 1.0
                return logits

        model = KeyValueModel()
        seq = torch.tensor([[2, 3]])
        upgrades = torch.zeros_like(seq)
        counts = torch.ones_like(seq)
        globals_ = torch.zeros((1, 11))
        character = torch.zeros(1, dtype=torch.long)
        boss = torch.zeros(1, dtype=torch.long)
        floors = torch.ones(1)
        keys_features = torch.tensor([[1.0, 0.0, 0.0, 1.0, 1.0, 0.0,
                                       0.0, 1.0 / 3.0, 0.25]])
        context = (
            keys_features,
            torch.zeros((1, MAP_GRID_CELLS), dtype=torch.long),
            torch.zeros((1, MAP_GRID_CELLS), dtype=torch.long),
            torch.zeros((1, HISTORY_HORIZON), dtype=torch.long),
            torch.full((1, HISTORY_HORIZON), HISTORY_HORIZON, dtype=torch.long),
        )
        observed = model(
            seq, upgrades, counts, globals_, character, boss,
            keys_features=keys_features,
        )
        loss = monotonic_counterfactual_loss(
            model, observed, seq, upgrades, counts, globals_, character, boss,
            floors, [], context=context,
            key_labels=torch.tensor([[1.0, 0.0, 0.0]]),
            key_masks=torch.tensor([[1.0, 1.0, 0.0]]),
        )
        self.assertGreater(loss.item(), 0.0)

    def test_monotonic_loss_checks_every_key_and_restores_emerald_pending(self):
        class SecondKeyValueModel(torch.nn.Module):
            def __init__(self):
                super().__init__()
                self.seen_key_features = []

            def forward(self, seq, upgrades, counts, globals_, character, boss,
                        keys_features=None, **kwargs):
                self.seen_key_features.append(keys_features.detach().clone())
                emerald_present = keys_features[:, 1]
                logits = torch.zeros((len(seq), 47), device=seq.device)
                logits[:, 0] = emerald_present * 2.0 - 1.0
                return logits

        model = SecondKeyValueModel()
        seq = torch.tensor([[2, 3]])
        upgrades = torch.zeros_like(seq)
        counts = torch.ones_like(seq)
        globals_ = torch.zeros((1, 11))
        character = torch.zeros(1, dtype=torch.long)
        boss = torch.zeros(1, dtype=torch.long)
        floors = torch.ones(1)
        keys_features = torch.tensor([[
            1.0, 1.0, 0.0,  # collected values
            1.0, 1.0, 0.0,  # known mask
            0.0, 2.0 / 3.0, 0.25,  # pending, progress, act
        ]])
        map_flags = torch.zeros((1, MAP_GRID_CELLS), dtype=torch.long)
        map_flags[0, 0] = 2  # FLAG_BURNING
        context = (
            keys_features,
            torch.zeros((1, MAP_GRID_CELLS), dtype=torch.long),
            map_flags,
            torch.zeros((1, HISTORY_HORIZON), dtype=torch.long),
            torch.full((1, HISTORY_HORIZON), HISTORY_HORIZON, dtype=torch.long),
        )
        observed = model(
            seq, upgrades, counts, globals_, character, boss,
            keys_features=keys_features,
        )
        loss = monotonic_counterfactual_loss(
            model, observed, seq, upgrades, counts, globals_, character, boss,
            floors, [], context=context,
            key_labels=torch.tensor([[1.0, 1.0, 0.0]]),
            key_masks=torch.tensor([[1.0, 1.0, 0.0]]),
        )
        self.assertGreater(loss.item(), 0.0)
        seen = torch.cat(model.seen_key_features[1:], dim=0)
        emerald_removed = seen[seen[:, 1].eq(0.0)]
        self.assertEqual(len(emerald_removed), 1)
        self.assertEqual(emerald_removed[0, 3:6].tolist(), [1.0, 1.0, 0.0])
        self.assertEqual(emerald_removed[0, 6].item(), 1.0)
        self.assertAlmostEqual(emerald_removed[0, 7].item(), 1.0 / 3.0)

    def test_key_head_loss_masks_unsupervised_targets(self):
        logits = torch.tensor([[2.0, 2.0, 2.0]])
        labels = torch.tensor([[1.0, 0.0, 1.0]])
        masks = torch.tensor([[1.0, 0.0, 0.0]])
        loss = _key_head_loss(logits, labels, masks)
        expected = torch.nn.functional.binary_cross_entropy_with_logits(
            torch.tensor([2.0]), torch.tensor([1.0])
        )
        self.assertAlmostEqual(loss.item(), expected.item(), places=6)

    def test_split_batch_shapes(self):
        v2_batch = tuple(torch.zeros(2) for _ in range(10))
        base, context, keys = _split_batch(v2_batch, use_map=False)
        self.assertIsNone(context)
        self.assertIsNone(keys)
        v3_batch = tuple(torch.zeros(2) for _ in range(18))
        base, context, keys = _split_batch(v3_batch, use_map=True)
        self.assertEqual(len(context), 6)
        self.assertEqual(len(keys), 2)
        with self.assertRaises(ValueError):
            _split_batch(v2_batch, use_map=True)

    @staticmethod
    def _loss_inputs(batch_size=4):
        logits = torch.zeros((batch_size, 47), requires_grad=True)
        labels = torch.zeros((batch_size, 24))
        masks = torch.zeros((batch_size, 24))
        floors = torch.ones(batch_size)
        globals_ = torch.zeros((batch_size, 11))
        return logits, labels, masks, floors, globals_

    def test_macro_loss_gives_supervised_heads_equal_weight(self):
        logits, labels, masks, floors, globals_ = self._loss_inputs(2)
        with torch.no_grad():
            logits[0, 0] = 0.0
            logits[:, 1] = 2.0
        labels[0, 0] = 1.0
        labels[:, 1] = 1.0
        masks[0, 0] = 1.0
        masks[:, 1] = 1.0
        masks[:, -1] = 1.0
        components = value_loss_components(
            logits, labels, masks, floors, globals_, torch.ones(6), [0.5] * 24, terminal_floors=torch.full_like(floors,57.)
        )
        first = torch.nn.functional.binary_cross_entropy_with_logits(
            torch.tensor(0.0), torch.tensor(1.0)
        )
        second = torch.nn.functional.binary_cross_entropy_with_logits(
            torch.tensor(2.0), torch.tensor(1.0)
        )
        self.assertAlmostEqual(
            components["hazard_macro_nll"].item(), ((first + second) / 2).item(), places=6
        )
        self.assertNotAlmostEqual(
            components["hazard_micro_nll"].item(), components["hazard_macro_nll"].item()
        )
        self.assertTrue(torch.isfinite(components["total"]))

    def test_prior_prediction_has_unit_relative_nll(self):
        batch_size = 10
        logits, labels, masks, floors, globals_ = self._loss_inputs(batch_size)
        rate = 0.2
        prior_logit = torch.logit(torch.tensor(rate))
        with torch.no_grad():
            logits[:, :23] = -100.
            logits[:, 0] = prior_logit
            logits[:, -1] = torch.logit(torch.tensor(.5/(1-rate)))
        labels[:2, 0] = 1.0
        masks[:, 0] = 1.0
        labels[:5, -1] = 1.0
        masks[:, -1] = 1.0
        rates = [rate] + [0.5] * 23
        components = value_loss_components(
            logits, labels, masks, floors, globals_, torch.ones(6), rates, terminal_floors=torch.full_like(floors,57.)
        )
        self.assertAlmostEqual(components["hazard_relative_nll"].item(), 1.0, places=5)
        self.assertAlmostEqual(components["heart_relative_nll"].item(), 1.0, places=5)
        self.assertAlmostEqual(components["relative_value"].item(), 1.0, places=5)

    def test_entropy_floor_keeps_extreme_priors_and_gradients_finite(self):
        entropy = _binary_entropy([0.0] * 24, dtype=torch.float32, device="cpu")
        torch.testing.assert_close(entropy, torch.full((24,), Config.BASELINE_ENTROPY_FLOOR))
        logits, labels, masks, floors, globals_ = self._loss_inputs(1)
        masks[0, 0] = 1.0
        masks[0, -1] = 1.0
        components = value_loss_components(
            logits, labels, masks, floors, globals_, torch.ones(6), [0.0] * 24, terminal_floors=torch.full_like(floors,57.)
        )
        components["total"].backward()
        self.assertTrue(torch.isfinite(logits.grad).all())

    def test_micro_mode_matches_legacy_loss(self):
        logits, labels, masks, floors, globals_ = self._loss_inputs(3)
        masks[:, :2] = 1.0
        masks[:, -1] = 1.0
        labels[0, 0] = 1.0
        from train import weighted_hazard_loss

        expected = weighted_hazard_loss(
            logits, labels, masks, floors, globals_, torch.ones(6), [.5]*24, terminal_floors=torch.full_like(floors,57.)
        )
        expected_gradient = torch.autograd.grad(expected, logits, retain_graph=True)[0]
        actual = value_loss_components(
            logits,
            labels,
            masks,
            floors,
            globals_,
            torch.ones(6),
            [0.5] * 24,
            loss_mode="micro_nll",
            terminal_floors=torch.full_like(floors,57.),
        )["value"]
        actual_gradient = torch.autograd.grad(actual, logits)[0]
        torch.testing.assert_close(actual, expected)
        torch.testing.assert_close(actual_gradient, expected_gradient)

    def test_contextual_baseline_smooths_and_falls_back(self):
        rates = [0.25] * 21
        statistics = {
            "schema_version": "floor-ascension-band-v1",
            "global": {"rates": rates},
            "strata": {"5:2": {"rates": [0.75] * 21}},
        }
        result = _contextual_baseline_probabilities(
            statistics, [5.0, 6.0], [2, 2]
        )
        self.assertTrue((result[0] == 0.75).all())
        self.assertTrue((result[1] == 0.25).all())

    def test_checkpoint_eligibility_enforces_context_and_mae(self):
        self.assertEqual(
            checkpoint_eligibility(True, True, 1.0, 2.0),
            (False, "warm-up context"),
        )
        self.assertEqual(
            checkpoint_eligibility(True, False, 3.0, 2.0),
            (False, "terminal-floor MAE guard"),
        )
        self.assertEqual(checkpoint_eligibility(True, False, 2.0, 2.0), (True, None))

    def test_curriculum_aligns_train_and_val_but_never_masks_test(self):
        class Split:
            context_enabled = True

        datasets = {name: Split() for name in ("train", "val", "test")}
        self.assertTrue(configure_context_curriculum(datasets, 0, True, 3))
        self.assertFalse(datasets["train"].context_enabled)
        self.assertFalse(datasets["val"].context_enabled)
        self.assertTrue(datasets["test"].context_enabled)

        self.assertFalse(configure_context_curriculum(datasets, 3, True, 3))
        self.assertTrue(datasets["train"].context_enabled)
        self.assertTrue(datasets["val"].context_enabled)
        self.assertTrue(datasets["test"].context_enabled)


class InferenceEngineTests(unittest.TestCase):
    BASE_STATE = {
        "hp": 50.0, "max_hp": 75.0, "gold": 120.0, "floor": 5.0,
        "ascension": 10.0, "character": "IRONCLAD", "visible_boss": "NO_BOSS",
        "deck": ["Strike_R", "Bash"], "relics": ["Burning Blood"],
    }
    MAP_STATE = {
        **BASE_STATE,
        "act": 1,
        "floor_in_act": 4,
        "current_node": (3, 4),
        "map_nodes": [
            {"x": 3, "y": 4, "symbol": "M", "burning": False,
             "children": [{"x": 3, "y": 5}]},
            {"x": 3, "y": 5, "symbol": "E", "burning": True, "children": []},
        ],
        "history": ["M", "$", "M"],
        "keys": {"ruby": False, "emerald": False, "sapphire": False},
    }

    def test_v3_engine_uses_map_context(self):
        from inference import STSInferenceEngine
        engine = STSInferenceEngine()
        self.assertTrue(engine.uses_map_context)
        value = engine.evaluate_state(self.MAP_STATE)
        self.assertTrue(0.0 <= value <= 1.0)
        keys = engine.evaluate_state_keys(self.MAP_STATE)
        self.assertEqual(len(keys), 3)
        self.assertTrue(all(0.0 <= key <= 1.0 for key in keys))

    def test_inference_rejects_missing_or_unknown_character(self):
        from inference import STSInferenceEngine
        engine = STSInferenceEngine.__new__(STSInferenceEngine)
        with self.assertRaises(ValueError):
            engine._character_id({})
        with self.assertRaises(ValueError):
            engine._character_id({"character": "SILENT"})

    def test_v2_checkpoint_engine_ignores_map_fields(self):
        from inference import STSInferenceEngine
        config = {
            "vocab_size": 2, "max_upgrade": 15, "max_count": 10,
            "d_model": 32, "n_heads": 4, "n_layers": 1,
            "num_global_features": 11, "num_characters": NUM_CHARACTER_IDS,
            "num_bosses": NUM_BOSS_IDS,
            "dropout": 0.0, "global_conditioning": "token", "norm_position": "pre",
        }
        model = STSValueNetwork(**config)
        with tempfile.TemporaryDirectory() as tmp:
            path = os.path.join(tmp, "v2.pth")
            save_checkpoint(
                path, model, config,
                ItemVocabulary().freeze(),
                __import__("dataset").GlobalFeatureEncoder(),
            )
            engine = STSInferenceEngine(model_path=path)
        self.assertFalse(engine.uses_map_context)
        self.assertEqual(engine.tokenizer.max_seq_len, 192)
        value = engine.evaluate_state(self.MAP_STATE)
        self.assertTrue(0.0 <= value <= 1.0)
        with self.assertRaises(ValueError):
            engine.evaluate_state_keys(self.MAP_STATE)


if __name__ == "__main__":
    unittest.main()
