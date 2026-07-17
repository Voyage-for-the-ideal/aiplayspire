import os
import tempfile
import unittest

import torch

from checkpointing import load_checkpoint, save_checkpoint
from data_pipeline import act_target, assign_split
from dataset import GlobalFeatureNormalizer
from encoding import ItemVocabulary, encode_items, parse_item_name
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


class ModelTests(unittest.TestCase):
    def _model(self, global_conditioning="token", norm_position="pre"):
        model = STSValueNetwork(
            vocab_size=12,
            max_upgrade=5,
            max_count=6,
            d_model=16,
            n_heads=4,
            n_layers=2,
            num_global_features=4,
            dropout=0.0,
            global_conditioning=global_conditioning,
            norm_position=norm_position,
        )
        return model.eval()

    def test_permutation_and_extra_padding_do_not_change_value(self):
        model = self._model()
        globals_ = torch.tensor([[1.0, 2.0, 3.0, 4.0]])
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
            torch.zeros((1, 4)),
        )
        for conditioning in ("token", "late_concat"):
            for norm in ("pre", "post"):
                self.assertEqual(self._model(conditioning, norm)(*inputs).shape, (1, 1))

    def test_v2_checkpoint_round_trip(self):
        model = self._model()
        config = {
            "vocab_size": 12,
            "max_upgrade": 5,
            "max_count": 6,
            "d_model": 16,
            "n_heads": 4,
            "n_layers": 2,
            "num_global_features": 4,
            "dropout": 0.0,
            "global_conditioning": "token",
            "norm_position": "pre",
        }
        vocabulary = ItemVocabulary()
        vocabulary.add("Bash")
        vocabulary.freeze()
        normalizer = GlobalFeatureNormalizer(
            means={name: 1.0 for name in GlobalFeatureNormalizer.feature_names},
            stds={name: 2.0 for name in GlobalFeatureNormalizer.feature_names},
        )
        with tempfile.TemporaryDirectory() as directory:
            path = os.path.join(directory, "model.pth")
            save_checkpoint(path, model, config, vocabulary, normalizer)
            checkpoint, loaded_vocab, loaded_normalizer = load_checkpoint(path)
        self.assertEqual(checkpoint["format_version"], 2)
        self.assertEqual(loaded_vocab.to_dict(), vocabulary.to_dict())
        self.assertEqual(loaded_normalizer.to_dict(), normalizer.to_dict())


if __name__ == "__main__":
    unittest.main()
