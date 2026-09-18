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
    MAP_GRID_CELLS, PLAN_HORIZON, HISTORY_HORIZON, ROOM_SYMBOL_TO_ID,
)
from model import STSValueNetwork
from train import _key_head_loss, _split_batch
from boss_context import NUM_BOSS_IDS


def _v2_model(**overrides):
    params = dict(
        vocab_size=32,
        d_model=32,
        n_heads=4,
        n_layers=1,
        num_global_features=9,
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
    globals_ = torch.rand((batch, 9))
    boss = torch.zeros((batch,), dtype=torch.long)
    return seq, upgrades, counts, globals_, boss


def _context_batch(batch=3):
    torch.manual_seed(1)
    map_rooms = torch.zeros((batch, MAP_GRID_CELLS), dtype=torch.long)
    map_flags = torch.zeros((batch, MAP_GRID_CELLS), dtype=torch.long)
    map_rooms[:, 0] = ROOM_SYMBOL_TO_ID["M"]
    map_rooms[:, 7] = ROOM_SYMBOL_TO_ID["E"]
    map_flags[:, 0] = 1 | (2 << 5)
    map_flags[:, 7] = 1 | 1 | (1 << 2) | (1 << 7)
    plan_rooms = torch.zeros((batch, PLAN_HORIZON), dtype=torch.long)
    plan_dist = torch.full((batch, PLAN_HORIZON), PLAN_HORIZON, dtype=torch.long)
    plan_rooms[:, 0] = ROOM_SYMBOL_TO_ID["R"]
    plan_dist[:, 0] = 0
    history_rooms = torch.zeros((batch, HISTORY_HORIZON), dtype=torch.long)
    history_dist = torch.full((batch, HISTORY_HORIZON), HISTORY_HORIZON, dtype=torch.long)
    history_rooms[:, 0] = ROOM_SYMBOL_TO_ID["E"]
    history_dist[:, 0] = 0
    keys_features = torch.rand((batch, 6))
    return (keys_features, map_rooms, map_flags, plan_rooms, plan_dist,
            history_rooms, history_dist)


class ModelTests(unittest.TestCase):
    def test_v2_forward_shape_unchanged(self):
        model = _v2_model()
        seq, upgrades, counts, globals_, boss = _item_batch()
        logits = model(seq, upgrades, counts, globals_, boss)
        self.assertEqual(logits.shape, (3, 21))

    def test_v3_forward_with_context_and_key_head(self):
        model = _v3_model()
        seq, upgrades, counts, globals_, boss = _item_batch()
        context = _context_batch()
        logits, key_logits = model(
            seq, upgrades, counts, globals_, boss,
            keys_features=context[0], map_rooms=context[1],
            map_flags=context[2], plan_rooms=context[3],
            plan_dist=context[4], history_rooms=context[5],
            history_dist=context[6], return_keys=True,
        )
        self.assertEqual(logits.shape, (3, 21))
        self.assertEqual(key_logits.shape, (3, 3))

    def test_v3_forward_falls_back_to_missing_context(self):
        model = _v3_model()
        seq, upgrades, counts, globals_, boss = _item_batch()
        logits = model(seq, upgrades, counts, globals_, boss)
        self.assertEqual(logits.shape, (3, 21))

    def test_return_keys_rejected_without_context(self):
        model = _v2_model()
        seq, upgrades, counts, globals_, boss = _item_batch()
        with self.assertRaises(ValueError):
            model(seq, upgrades, counts, globals_, boss, return_keys=True)

    def test_v3_backward_pass_updates_context_parameters(self):
        model = _v3_model()
        seq, upgrades, counts, globals_, boss = _item_batch()
        context = _context_batch()
        logits, key_logits = model(
            seq, upgrades, counts, globals_, boss,
            keys_features=context[0], map_rooms=context[1],
            map_flags=context[2], plan_rooms=context[3],
            plan_dist=context[4], history_rooms=context[5],
            history_dist=context[6], return_keys=True,
        )
        (logits.sum() + key_logits.sum()).backward()
        grads = [
            p.grad for p in (
                *self._params(model.map_room_emb),
                *self._params(model.map_flag_proj),
                *self._params(model.plan_dist_emb),
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
            "num_global_features": 9, "num_bosses": NUM_BOSS_IDS,
            "dropout": 0.0, "global_conditioning": "token", "norm_position": "pre",
        }
        restored = self._roundtrip(_v2_model(), config)
        self.assertFalse(restored.use_map_context)

    def test_v3_checkpoint_roundtrip(self):
        config = {
            "vocab_size": 32, "max_upgrade": 15, "max_count": 10,
            "d_model": 32, "n_heads": 4, "n_layers": 1,
            "num_global_features": 9, "num_bosses": NUM_BOSS_IDS,
            "dropout": 0.0, "global_conditioning": "token", "norm_position": "pre",
            "use_map_context": True,
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
                "visible_boss": "NO_BOSS",
                "preprocessing_version": PREPROCESSING_VERSION,
                "filter_version": FILTER_VERSION,
                **{column: 0.0 for column in TARGET_COLUMNS},
                **{column: True for column in MASK_COLUMNS},
                "act": 1,
                "map_rooms": [ROOM_SYMBOL_TO_ID["M"]] + [0] * (MAP_GRID_CELLS - 1),
                "map_flags": [1] * MAP_GRID_CELLS,
                "plan_rooms": [ROOM_SYMBOL_TO_ID["R"]],
                "history_rooms": [ROOM_SYMBOL_TO_ID["M"], ROOM_SYMBOL_TO_ID["$"]]
                                 + [0] * (HISTORY_HORIZON - 2),
                "key_states": [0, 0, 0],
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
            self.assertEqual(len(sample), 17)
            (keys_features, map_rooms, map_flags, plan_rooms, plan_dist,
             history_rooms, history_dist, key_labels, key_masks) = sample[8:]
            self.assertEqual(keys_features.shape, (6,))
            self.assertEqual(map_rooms.shape, (MAP_GRID_CELLS,))
            self.assertEqual(map_flags.shape, (MAP_GRID_CELLS,))
            self.assertEqual(plan_rooms.shape, (PLAN_HORIZON,))
            self.assertEqual(plan_dist.tolist(), [0])
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

    def test_curriculum_masking_drops_map_plan_and_history(self):
        with tempfile.TemporaryDirectory() as tmp:
            dataset = self._dataset(self._write_parquet(tmp, self._rows()))
            dataset.context_enabled = False
            sample = dataset[0]
            (_, map_rooms, map_flags, plan_rooms, plan_dist,
             history_rooms, history_dist, _, _) = sample[8:]
            self.assertFalse(map_rooms.any())
            self.assertFalse(map_flags.any())
            self.assertFalse(plan_rooms.any())
            self.assertTrue((plan_dist == PLAN_HORIZON).all())
            self.assertFalse(history_rooms.any())
            self.assertTrue((history_dist == HISTORY_HORIZON).all())

    def test_plan_dropout_rate_one_always_drops(self):
        with tempfile.TemporaryDirectory() as tmp:
            dataset = self._dataset(
                self._write_parquet(tmp, self._rows()), plan_dropout=1.0
            )
            for index in range(3):
                plan_rooms = dataset[index][11]
                self.assertFalse(plan_rooms.any())
                # History carries no future information and is never dropped.
                history_rooms = dataset[index][13]
                self.assertTrue(history_rooms.any())

    def test_missing_columns_degrade_to_missing_context(self):
        rows = self._rows()
        for row in rows:
            row["map_rooms"] = None
            row["map_flags"] = None
            row["key_states"] = None
        with tempfile.TemporaryDirectory() as tmp:
            dataset = self._dataset(self._write_parquet(tmp, rows))
            sample = dataset[0]
            (_, map_rooms, _, _, _, _, _, _, key_masks) = sample[8:]
            self.assertFalse(map_rooms.any())
            self.assertEqual(key_masks.tolist(), [1.0, 0.0, 0.0])


class TrainingHelperTests(unittest.TestCase):
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
        v2_batch = tuple(torch.zeros(2) for _ in range(8))
        base, context, keys = _split_batch(v2_batch, use_map=False)
        self.assertIsNone(context)
        self.assertIsNone(keys)
        v3_batch = tuple(torch.zeros(2) for _ in range(17))
        base, context, keys = _split_batch(v3_batch, use_map=True)
        self.assertEqual(len(context), 7)
        self.assertEqual(len(keys), 2)
        with self.assertRaises(ValueError):
            _split_batch(v2_batch, use_map=True)


class InferenceEngineTests(unittest.TestCase):
    BASE_STATE = {
        "hp": 50.0, "max_hp": 75.0, "gold": 120.0, "floor": 5.0,
        "ascension": 10.0, "visible_boss": "NO_BOSS",
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
        "plan": ["E"],
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

    def test_v2_checkpoint_engine_ignores_map_fields(self):
        from inference import STSInferenceEngine
        config = {
            "vocab_size": 2, "max_upgrade": 15, "max_count": 10,
            "d_model": 32, "n_heads": 4, "n_layers": 1,
            "num_global_features": 9, "num_bosses": NUM_BOSS_IDS,
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
        self.assertEqual(engine.tokenizer.max_seq_len, 64)
        value = engine.evaluate_state(self.MAP_STATE)
        self.assertTrue(0.0 <= value <= 1.0)
        with self.assertRaises(ValueError):
            engine.evaluate_state_keys(self.MAP_STATE)


if __name__ == "__main__":
    unittest.main()
